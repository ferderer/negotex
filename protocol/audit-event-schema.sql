-- Negotex audit event schema (canonical)
-- Target: TimescaleDB (PostgreSQL-compatible)
-- See ADR-027 for full design rationale
--
-- Three event tables, three lifecycles:
--
--   node_failures         operational  — act on it now; short retention
--   node_exits            diagnostic   — hash chain, perf analysis; medium retention
--   process_completions   compliance   — permanent record; jurisdiction-driven retention
--
-- Payload storage:
--
--   attribute_classifications   type-level metadata (classification, jurisdiction)
--   payload_attributes          content-addressable value store + instance-level lifecycle
--
-- Hash chain (ADR-028) spans node_exits, node_failures, and process_completions.


-- ---------------------------------------------------------------------------
-- attribute_classifications
--
-- Classification is a property of the attribute type (key + process definition),
-- not the attribute instance. Normalised here to avoid repeating 38-44 bytes
-- of metadata on every high-cardinality payload_attributes row.
--
-- Populated at process definition deployment time by the process compiler.
-- One row per (attr_key, process_definition_id) pair.
-- ---------------------------------------------------------------------------

CREATE TABLE attribute_classifications (
    attr_key              TEXT     NOT NULL,
    process_definition_id TEXT     NOT NULL,
    -- 32-bit: TECHNICAL, ACCOUNTING, AML, CONTRACTUAL, PII, SPECIAL_CATEGORY,
    --         FINANCIAL, AUDIT_LOG, TAX, LEGAL, INSURANCE, HEALTHCARE
    --         Bits 24-31 reserved for custom extensions
    classification_flags  INTEGER  NOT NULL DEFAULT 0,
    -- 16-bit: PUBLIC, INTERNAL, CONFIDENTIAL, STRICTLY_CONFIDENTIAL
    security_flags        SMALLINT NOT NULL DEFAULT 0,
    -- 16-bit: EVENT_TIME, CONTRACT_END, RELATIONSHIP_END, FISCAL_YEAR_END, TRANSACTION_DATE
    anchor_flags          SMALLINT NOT NULL DEFAULT 0,
    jurisdictions         TEXT[]   NOT NULL DEFAULT ARRAY['DE'],

    PRIMARY KEY (attr_key, process_definition_id)
);

CREATE INDEX idx_classifications_pii
    ON attribute_classifications (process_definition_id)
    WHERE classification_flags & 16 != 0;   -- PII bit (bit 4)

CREATE INDEX idx_classifications_special
    ON attribute_classifications (process_definition_id)
    WHERE classification_flags & 32 != 0;   -- SPECIAL_CATEGORY bit (Art. 9 GDPR, bit 5)

COMMENT ON TABLE attribute_classifications IS
    'Type-level classification metadata per attribute key and process definition. '
    'Populated at deployment time. Never updated after deployment — changes require '
    'a new process definition version. '
    'Joined at query time for compliance sweeps, GDPR erasure identification, '
    'and retention policy evaluation. Never read on the hot path.';


-- ---------------------------------------------------------------------------
-- payload_attributes
--
-- Content-addressable value store. Deduplication key: SHA-256(attr_key || attr_value).
-- An attribute value unchanged across N node transitions or process instances
-- is stored once and referenced by hash from node_exits and process_completions.
--
-- lifecycle_flags stays here (not in attribute_classifications) because it tracks
-- the state of a specific attribute value — REDACTED is set per-value during
-- GDPR erasure, not per attribute type.
-- ---------------------------------------------------------------------------

CREATE TABLE payload_attributes (
    attr_hash       TEXT        PRIMARY KEY,  -- SHA-256(attr_key || serialised_attr_value)
    attr_key        TEXT        NOT NULL,
    attr_value      JSONB       NOT NULL,
    size_bytes      INTEGER     NOT NULL,
    -- 16-bit: ACTIVE, LEGAL_HOLD, PENDING_DELETION, REDACTED, ANONYMISED
    -- Instance-level state — set per value, not per type
    lifecycle_flags SMALLINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attrs_lifecycle ON payload_attributes (lifecycle_flags)
    WHERE lifecycle_flags != 0;              -- only non-ACTIVE rows indexed
CREATE INDEX idx_attrs_key       ON payload_attributes (attr_key);

COMMENT ON TABLE payload_attributes IS
    'Content-addressable attribute value store. '
    'Join with attribute_classifications on (attr_key, process_definition_id) '
    'to resolve classification, security, and jurisdiction metadata. '
    'GDPR erasure: set REDACTED in lifecycle_flags and replace attr_value '
    'with a tombstone token. The attr_hash and row structure are preserved — '
    'audit trail shape remains intact.';


-- ---------------------------------------------------------------------------
-- node_failures — operational table
--
-- Lifecycle: short to medium (days–weeks). Prunable once resolved.
-- Access pattern: current failures for process X, retry history for envelope Y.
-- ---------------------------------------------------------------------------

CREATE TABLE node_failures (
    failure_id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Identity
    envelope_id           UUID        NOT NULL,
    process_instance_id   UUID        NOT NULL,
    process_definition_id TEXT        NOT NULL,
    node_id               TEXT        NOT NULL,
    handler_version       TEXT        NOT NULL,
    -- Timing
    failed_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Error detail
    error_type            TEXT        NOT NULL,  -- exception class name
    error_message         TEXT        NOT NULL,
    stack_trace           TEXT,
    retry_count           INTEGER     NOT NULL DEFAULT 0,
    max_retries           INTEGER     NOT NULL,
    -- Outcome
    outcome               TEXT        NOT NULL CHECK (outcome IN (
                              'RETRYING',   -- scheduled for retry
                              'DLQ',        -- retry budget exhausted, routed to DLQ
                              'RECOVERED'   -- subsequent retry succeeded
                          )),
    dlq_topic             TEXT,             -- set when outcome = 'DLQ'
    recovered_at          TIMESTAMPTZ,      -- set when outcome = 'RECOVERED'
    -- Hash chain (ADR-028)
    envelope_hash         TEXT        NOT NULL,
    previous_hash         TEXT
);

SELECT create_hypertable('node_failures', 'failed_at');

CREATE INDEX idx_failures_instance ON node_failures (process_instance_id, failed_at DESC);
CREATE INDEX idx_failures_node     ON node_failures (node_id, failed_at DESC);
CREATE INDEX idx_failures_open     ON node_failures (failed_at DESC)
    WHERE outcome IN ('RETRYING', 'DLQ');  -- open failures only

COMMENT ON TABLE node_failures IS
    'Operational failure records. One row per handler exception. '
    'Prunable once outcome = RECOVERED or after a configurable operational retention window. '
    'DLQ rows should be retained until manually acknowledged.';


-- ---------------------------------------------------------------------------
-- node_exits — diagnostic table
--
-- Lifecycle: medium (weeks–months). Prunable after process_completions exists
--            and chain_verified_at is set on the completion row.
-- Access pattern: timeline reconstruction, per-node performance, hash chain verification.
-- ---------------------------------------------------------------------------

CREATE TABLE node_exits (
    exit_id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Identity
    envelope_id           UUID        NOT NULL,
    process_instance_id   UUID        NOT NULL,
    process_definition_id TEXT        NOT NULL,
    node_id               TEXT        NOT NULL,
    handler_version       TEXT        NOT NULL,  -- required for hash chain reproducibility
    -- Timing
    exited_at             TIMESTAMPTZ NOT NULL,
    duration_ms           INTEGER     NOT NULL,  -- derived from envelope.nodeEnteredAt
    -- Payload snapshot (content-addressable, ADR-027)
    -- Join payload_attributes + attribute_classifications for full attribute detail
    inline_payload        JSONB,                 -- attributes < 256 bytes stored inline
    attribute_refs        TEXT[],                -- SHA-256 refs into payload_attributes
    -- Hash chain (ADR-028)
    envelope_hash         TEXT        NOT NULL,
    previous_hash         TEXT
);

SELECT create_hypertable('node_exits', 'exited_at');

CREATE INDEX idx_exits_instance ON node_exits (process_instance_id, exited_at ASC);
CREATE INDEX idx_exits_node     ON node_exits (node_id, exited_at DESC);
CREATE INDEX idx_exits_duration ON node_exits (node_id, duration_ms DESC);

COMMENT ON TABLE node_exits IS
    'Diagnostic exit records. One row per successful node completion. '
    'No retention classification — not a compliance record. '
    'Prunable after the associated process_completions row has chain_verified_at set.';


-- ---------------------------------------------------------------------------
-- process_completions — compliance table
--
-- Lifecycle: permanent; driven by attribute_classifications + jurisdictions.
-- Access pattern: compliance queries, regulatory audits, GDPR erasure requests,
--                 retention sweep, legal hold management.
-- ---------------------------------------------------------------------------

CREATE TABLE process_completions (
    completion_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Identity
    process_instance_id   UUID        NOT NULL UNIQUE,
    process_definition_id TEXT        NOT NULL,
    process_version       TEXT        NOT NULL,
    -- Outcome
    terminal_node_id      TEXT        NOT NULL,  -- which Terminate node was reached
    outcome_label         TEXT,                  -- human-readable, e.g. 'approved', 'rejected'
    -- Timing
    started_at            TIMESTAMPTZ NOT NULL,  -- from envelope.createdAt
    completed_at          TIMESTAMPTZ NOT NULL,
    duration_ms           BIGINT      NOT NULL,
    -- Final payload (content-addressable, ADR-027)
    -- Full accumulated payload at the Terminate node
    -- Join payload_attributes + attribute_classifications for full attribute detail
    inline_payload        JSONB,
    attribute_refs        TEXT[],                -- SHA-256 refs into payload_attributes
    -- Hash chain (ADR-028) — terminal link
    final_envelope_hash   TEXT        NOT NULL,
    previous_hash         TEXT        NOT NULL,
    chain_verified_at     TIMESTAMPTZ,           -- set by Audit Verifier after full chain check
    -- Retention anchor (ADR-027)
    -- anchor_reference_id: external ref when anchor_flags != EVENT_TIME
    --   e.g. contract ID, relationship ID, fiscal year reference
    -- Retention duration resolved by RetentionPolicyProvider SPI from
    -- attribute_classifications joined on process_definition_id.
    anchor_reference_id   TEXT,
    -- 16-bit: ACTIVE, LEGAL_HOLD, PENDING_DELETION, REDACTED, ANONYMISED
    lifecycle_flags       SMALLINT    NOT NULL DEFAULT 0,
    retain_until          TIMESTAMPTZ           -- NULL until policy first evaluated
);

SELECT create_hypertable('process_completions', 'completed_at');

CREATE INDEX idx_completions_instance     ON process_completions (process_instance_id);
CREATE INDEX idx_completions_definition   ON process_completions (process_definition_id, completed_at DESC);
CREATE INDEX idx_completions_lifecycle    ON process_completions (lifecycle_flags)
    WHERE lifecycle_flags != 0;
CREATE INDEX idx_completions_retain_until ON process_completions (retain_until)
    WHERE retain_until IS NOT NULL
      AND lifecycle_flags & 2 = 0;              -- exclude LEGAL_HOLD (bit 1)
CREATE INDEX idx_completions_legal_hold   ON process_completions (completed_at DESC)
    WHERE lifecycle_flags & 2 != 0;             -- LEGAL_HOLD rows only
CREATE INDEX idx_completions_anchor_ref   ON process_completions (anchor_reference_id)
    WHERE anchor_reference_id IS NOT NULL;
CREATE INDEX idx_completions_unverified   ON process_completions (completed_at ASC)
    WHERE chain_verified_at IS NULL;            -- Audit Verifier work queue

COMMENT ON TABLE process_completions IS
    'Permanent compliance record. One row per completed process instance. '
    'Retention duration resolved by RetentionPolicyProvider SPI. Inputs: '
    'attribute_classifications joined on (attr_key, process_definition_id) '
    'to find dominant classification and jurisdiction set. '
    'Multi-flag conflicts resolved by longest-retention-wins rule. '
    'GDPR erasure: redact PII values in payload_attributes; this row is preserved. '
    'Legal hold: set LEGAL_HOLD in lifecycle_flags; retain_until ignored while active.';


-- ---------------------------------------------------------------------------
-- retention_decisions — immutable audit log of policy evaluations
-- ---------------------------------------------------------------------------

CREATE TABLE retention_decisions (
    decision_id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    decided_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Subject
    completion_id           UUID        NOT NULL REFERENCES process_completions (completion_id),
    process_instance_id     UUID        NOT NULL,
    -- Policy inputs
    dominant_classification TEXT        NOT NULL,  -- which flag won multi-flag conflict
    anchor_flags            SMALLINT    NOT NULL,
    anchor_reference_id     TEXT,
    jurisdictions           TEXT[]      NOT NULL,
    -- Policy outputs
    policy_id               TEXT        NOT NULL,
    policy_version          TEXT        NOT NULL,
    retain_until            TIMESTAMPTZ NOT NULL,
    applied_duration_days   INTEGER     NOT NULL,
    used_safe_default       BOOLEAN     NOT NULL DEFAULT FALSE,
    reason                  TEXT        NOT NULL,
    applied_rules           JSONB
);

SELECT create_hypertable('retention_decisions', 'decided_at');

CREATE INDEX idx_retention_completion ON retention_decisions (completion_id);

COMMENT ON TABLE retention_decisions IS
    'Immutable audit log of every retention policy evaluation. '
    'Records which policy version computed retain_until and why. '
    'Required for demonstrating compliance with deletion decisions to regulators.';

-- Negotex audit schema — ADR-027 + ADR-028
-- TimescaleDB hypertables partitioned by time.
-- envelope_hash and handler_version are required on every row so the
-- Audit Verifier can reproduce the hash chain without implicit manifest knowledge.

-- ── node_completions ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS node_completions (
    completion_id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    envelope_id           TEXT          NOT NULL,
    process_instance_id   TEXT          NOT NULL,
    process_definition_id TEXT          NOT NULL,
    process_version       TEXT,
    node_id               TEXT          NOT NULL,
    handler_version       TEXT          NOT NULL,   -- ADR-028: required for chain verification
    envelope_hash         TEXT          NOT NULL,   -- previousEnvelopeHash after this transition
    completed_at          TIMESTAMPTZ   NOT NULL,
    duration_ms           BIGINT,
    target_topics         TEXT,                     -- comma-separated
    -- ADR-027 retention classification (Phase 2)
    classification_flags  INTEGER       NOT NULL DEFAULT 0,
    security_flags        SMALLINT      NOT NULL DEFAULT 0,
    anchor_flags          SMALLINT      NOT NULL DEFAULT 0,
    lifecycle_flags       SMALLINT      NOT NULL DEFAULT 0,
    jurisdictions         TEXT[]        NOT NULL DEFAULT ARRAY['DE']
);

SELECT create_hypertable('node_completions', 'completed_at',
    if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_completions_instance
    ON node_completions (process_instance_id, completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_completions_envelope
    ON node_completions (envelope_id);

-- ── node_failures ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS node_failures (
    failure_id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    envelope_id           TEXT          NOT NULL,
    process_instance_id   TEXT          NOT NULL,
    process_definition_id TEXT          NOT NULL,
    process_version       TEXT,
    node_id               TEXT          NOT NULL,
    handler_version       TEXT          NOT NULL,   -- ADR-028
    envelope_hash         TEXT          NOT NULL,   -- hash at point of failure
    failed_at             TIMESTAMPTZ   NOT NULL,
    error_type            TEXT,
    error_message         TEXT,
    retry_count           INT,
    max_retries           INT,
    outcome               TEXT,                     -- RETRYING | DLQ
    classification_flags  INTEGER       NOT NULL DEFAULT 0,
    lifecycle_flags       SMALLINT      NOT NULL DEFAULT 0,
    jurisdictions         TEXT[]        NOT NULL DEFAULT ARRAY['DE']
);

SELECT create_hypertable('node_failures', 'failed_at',
    if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_failures_instance
    ON node_failures (process_instance_id, failed_at DESC);

-- ── process_terminations ──────────────────────────────────────────────────────
-- Long-retention compliance record — the primary record for compliance queries.
CREATE TABLE IF NOT EXISTS process_terminations (
    termination_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    envelope_id           TEXT          NOT NULL,
    process_instance_id   TEXT          NOT NULL,
    process_definition_id TEXT          NOT NULL,
    process_version       TEXT,
    terminal_node_id      TEXT          NOT NULL,
    handler_version       TEXT          NOT NULL,   -- ADR-028
    envelope_hash         TEXT          NOT NULL,   -- final chain hash
    completed_at          TIMESTAMPTZ   NOT NULL,
    duration_ms           BIGINT,
    -- ADR-027 retention — terminations are typically AUDIT_LOG | ACCOUNTING
    classification_flags  INTEGER       NOT NULL DEFAULT 0,
    security_flags        SMALLINT      NOT NULL DEFAULT 0,
    anchor_flags          SMALLINT      NOT NULL DEFAULT 0,
    anchor_reference_id   TEXT,                     -- e.g. contract ID for CONTRACT_END anchoring
    lifecycle_flags       SMALLINT      NOT NULL DEFAULT 0,
    jurisdictions         TEXT[]        NOT NULL DEFAULT ARRAY['DE']
);

SELECT create_hypertable('process_terminations', 'completed_at',
    if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_terminations_instance
    ON process_terminations (process_instance_id);

CREATE INDEX IF NOT EXISTS idx_terminations_definition
    ON process_terminations (process_definition_id, completed_at DESC);

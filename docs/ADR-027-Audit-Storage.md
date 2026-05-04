# ADR-027 — Audit storage: content-addressable payloads with differentiated retention

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-002 (immutable envelope), ADR-004 (async publishing), ADR-028 (hash chains) |

## Context

Every node transition generates an audit event (ENTERED, EXITED, FAILED, END) that must be persisted for compliance, debugging, and process replay. The immutable envelope model (ADR-002) means that each node transition carries the full accumulated payload — by node 10 of a 10-node process, the payload may contain all attributes added by all previous nodes. Storing the full payload at each node naively produces massive redundancy.

**Storage estimate without deduplication:**
A 10-node process with a 1 KB base payload grows to ~2 KB by node 10. Storing the full payload at each step yields ~14 KB per process instance. At 1 million instances/year: ~14 TB, with ~75% redundancy.

**Compliance requirements that must be met:**
- Multi-jurisdictional retention (DE: 10y accounting, UK: 6y contracts, US: 7y financial)
- GDPR erasure (PII must be selectively erasable without destroying the audit trail structure)
- Legal hold (specific instances cannot be deleted regardless of retention period)
- Retention anchor flexibility (retention may start at contract end, not event time)
- Audit log of retention decisions (who deleted what, under which policy)

## Decision

### Hot path vs cold path

**Hot path (runtime — performance-first):** Kafka topics and Valkey carry the full payload. No deduplication, no normalisation. This path must never touch TimescaleDB during envelope processing.

**Cold path (audit — storage-efficient):** TimescaleDB receives audit events via the async Publisher. Large attributes (≥ 256 bytes) are deduplicated via content-addressable storage; small attributes are stored inline.

### Content-addressable attribute deduplication

Attribute deduplication key: `SHA-256(attrKey || serialisedAttrValue)`.

An attribute that appears unchanged across 10 node transitions is stored once in `payload_attributes` and referenced by hash from each `envelope_events` row. The 75% redundancy above collapses to a single copy of the large attribute plus 10 lightweight hash references.

**Schema (simplified):**
```sql
CREATE TABLE envelope_events (
    event_id          UUID PRIMARY KEY,
    envelope_id       TEXT NOT NULL,
    process_instance_id TEXT NOT NULL,
    node_id           TEXT NOT NULL,
    event_type        TEXT NOT NULL,   -- ENTERED | EXITED | FAILED | END
    timestamp         TIMESTAMPTZ NOT NULL,
    duration_ms       INTEGER,
    inline_payload    JSONB,           -- attributes < 256 bytes
    attribute_refs    TEXT[],          -- SHA-256 hashes of large attributes
    classification_flags INTEGER NOT NULL DEFAULT 0,
    security_flags    SMALLINT NOT NULL DEFAULT 0,
    anchor_flags      SMALLINT NOT NULL DEFAULT 0,
    anchor_reference_id TEXT,
    lifecycle_flags   SMALLINT NOT NULL DEFAULT 0,
    jurisdictions     TEXT[] NOT NULL DEFAULT ARRAY['DE']
);

CREATE TABLE payload_attributes (
    attr_hash    TEXT PRIMARY KEY,     -- SHA-256(key || value)
    attr_key     TEXT NOT NULL,
    attr_value   JSONB NOT NULL,
    size_bytes   INTEGER NOT NULL,
    classification_flags INTEGER NOT NULL DEFAULT 0,
    lifecycle_flags SMALLINT NOT NULL DEFAULT 0,
    jurisdictions TEXT[] NOT NULL DEFAULT ARRAY['DE'],
    created_at   TIMESTAMPTZ NOT NULL
);

SELECT create_hypertable('envelope_events', 'timestamp');
```

### Bitmap-based retention classification

Retention metadata is encoded as compact bitfields rather than nullable columns, enabling efficient multi-flag queries and policy evaluation:

**Classification flags (32-bit INTEGER):** Data category — `TECHNICAL`, `ACCOUNTING`, `AML`, `CONTRACTUAL`, `PII`, `SPECIAL_CATEGORY` (Art. 9 GDPR), `FINANCIAL`, `AUDIT_LOG`, `TAX`, `LEGAL`, `INSURANCE`, `HEALTHCARE`. Bits 24–31 reserved for custom extensions.

**Security flags (16-bit SMALLINT):** Confidentiality level — `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `STRICTLY_CONFIDENTIAL`.

**Anchor flags (16-bit SMALLINT):** When retention duration starts — `EVENT_TIME` (default), `CONTRACT_END`, `RELATIONSHIP_END`, `FISCAL_YEAR_END`, `TRANSACTION_DATE`.

**Lifecycle flags (16-bit SMALLINT):** Current state — `ACTIVE`, `LEGAL_HOLD`, `PENDING_DELETION`, `REDACTED`, `ANONYMISED`.

### Policy SPI

Retention duration is not hardcoded. A `RetentionPolicyProvider` SPI resolves the combination of classification flags + jurisdiction + anchor to a concrete retention period. The resolution decision is logged to a `retention_decisions` table for audit purposes.

Multi-flag conflicts (e.g. `ACCOUNTING | PII`) are resolved by a configurable priority rule — the longer-retention classification wins by default.

### GDPR erasure

PII attributes are stored in `payload_attributes` with `PII` in `classification_flags`. Erasure sets `REDACTED` in `lifecycle_flags` and replaces `attr_value` with a tombstone token. The `envelope_events` row structure (hash references, timestamps, metadata) is preserved — the audit trail shape remains intact, only the PII values are removed.

## Consequences

**Positive:**
- Storage cost for large, stable attributes (e.g. application objects) is O(1) regardless of how many nodes they pass through.
- Retention classification is queryable and indexable without JSON parsing.
- Policy logic is external to the schema — jurisdictional rule changes do not require schema migrations.
- GDPR erasure is surgical — PII values are removed without destroying the audit trail.

**Negative:**
- Payload reconstruction for debugging requires a join between `envelope_events` and `payload_attributes`.
- The inline threshold (256 bytes) is a heuristic — misclassified attributes incur either unnecessary deduplication overhead (too small) or missed deduplication benefit (too large).
- Content-addressable storage assumes attributes are immutable once written; a modified attribute must be stored as a new hash entry.

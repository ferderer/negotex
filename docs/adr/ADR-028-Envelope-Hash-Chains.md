# ADR-028 — Envelope hash chains

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 5 — Compliance features |
| **Priority** | PoC phase |
| **Relates to** | ADR-002 (immutable envelope), ADR-027 (audit storage) |

## Context

Regulated industries (banking, insurance, healthcare) require audit trails that are not merely complete but provably tamper-evident. A database record that shows "this node processed this envelope at this time" is auditable — but can be modified by a database administrator, a storage compromise, or a migration error. Basel III, SOX, and GDPR audit requirements go further: the integrity of the audit trail itself must be verifiable.

Neither Camunda nor Temporal provide native cryptographic chaining over execution history. Blockchain-based approaches add significant infrastructure overhead and governance complexity.

## Decision

Every envelope carries a cryptographic hash that chains it to the previous envelope in the process instance:

```
EnvelopeHash = SHA-256(
    previousEnvelopeHash
    || executionResult
    || timestamp
    || handlerVersion
)
```

The chain is initialised at the Trigger node:
```
EnvelopeHash₀ = SHA-256("GENESIS" || initialPayload || timestamp || processVersion)
```

The hash is stored as metadata on the `Envelope` and persisted with every `envelope_events` record in TimescaleDB. It is not part of the business payload and is not accessible to handlers.

**Verification:** A separate Audit Verifier consumer reads the `envelope_events` chain for a process instance from TimescaleDB and recomputes each hash. Any gap, reordering, or modification of records breaks the chain at the tampered point. Verification can run on-demand (triggered by a compliance query) or continuously (background consumer).

## Consequences

**Positive:**
- The audit trail is tamper-evident without external blockchain infrastructure.
- Verification is deterministic — the same inputs always produce the same hash.
- Hash chain verification is the prerequisite for Process Replay Debugging (planned Enterprise feature): the replay system can certify that the replayed execution matches the original chain.
- Differentiating — no major workflow engine competitor offers this natively.

**Negative:**
- Hash computation adds a small overhead per envelope transition (microseconds — negligible against network I/O).
- A verified chain requires that every intermediate event was captured and stored. Any gap in audit event delivery (due to async publishing lag or consumer failure) breaks verification until the gap is filled.
- `handlerVersion` must be explicitly tracked per node deployment; if it is omitted or incorrect, the chain cannot be reproduced for verification.

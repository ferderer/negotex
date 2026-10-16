# ADR-042 — Retry, dead-letter topics and redrive

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-026 (topic naming), ADR-023 (versioning), ADR-030 (consistency contracts), ADR-027 (audit storage) |

## Context

ADR-026 specifies the edge topics but not what happens to an envelope whose handler fails permanently. The architecture document states "retry per retry policy, then DLQ", yet neither the retry mechanism nor the DLQ topic naming, content, or recovery path is defined. The process compiler (ADR-041) must plan these topics, and operators need a defined way to re-drive individual process instances after fixing the cause.

Constraints:
- Partition key is `processInstanceId` (ADR-026); ordering within an instance must hold.
- Kafka has no delayed delivery. Non-blocking retry therefore needs either tiered retry topics with consumer pausing or an external scheduler — both add significant complexity.
- A parked envelope is still an active process instance (a Join may be waiting on it, Valkey state must survive).
- DLQ messages contain full payloads, including PII. Kafka retention is outside the GDPR erasure mechanism of ADR-027.

## Decision

### Retry

**v1: in-process retry only.** The node processor retries a failed handler call in process with bounded exponential backoff, on a virtual thread (ADR-024). Parameters come from the node's retry policy: `maxAttempts` (default 3), `initialBackoff`, `maxBackoff`. Every failed attempt persists a `FAILED` audit event with the attempt number. When the budget is exhausted, the envelope goes to the node's DLQ.

**Reserved, not implemented:** non-blocking retry topics for long backoffs (minutes to hours). The name is reserved so that it does not collide later:

```
{processId}-v{version}.retry.{nodeId}
```

The compiler must not allow a node with this ID pattern to claim the namespace; the `retry` segment is reserved in the same way as `edge` and `dlq`.

### Dead-letter topics

One DLQ topic **per node**, created by `ops` at deployment together with the edge topics:

```
{processId}-v{version}.dlq.{nodeId}

Example:
  loan-app-v1.3.0.dlq.credit-check
```

Per-node granularity gives lag and depth per node (consumer groups are already per node, ADR-026), selective redrive, and a direct mapping from DLQ to the node that failed. Topic count is a management concern (ADR-026), not a runtime concern.

**Message content:** the original envelope, unchanged, with the original partition key (`processInstanceId`). Failure information travels in headers only, so the envelope and its hash chain stay untouched:

| Header | Content |
|---|---|
| `x-negotex-origin-topic` | Edge topic the envelope was consumed from |
| `x-negotex-node-id` | Node that failed |
| `x-negotex-reason` | `HANDLER_EXHAUSTED`, `CONDITION_FAILED`, `DESERIALIZATION`, `MIGRATION_TIMEOUT` |
| `x-negotex-error-class` | Exception class |
| `x-negotex-error-message` | Message, truncated to 1 KB |
| `x-negotex-attempts` | Attempts made |
| `x-negotex-failed-at` | ISO-8601 timestamp |
| `x-negotex-redrive-count` | Number of earlier redrives (absent on first failure) |

For `DESERIALIZATION` failures the envelope cannot be parsed; the raw bytes are forwarded unchanged and no audit event with payload can be written.

**Retention:** default 14 days, configurable per process. This is deliberately longer than the 1–24 h of edge topics (ADR-026) because DLQ entries await manual intervention. Because DLQ messages carry payloads outside the ADR-027 erasure mechanism, the retention must not exceed the shortest erasure SLA of the jurisdictions involved; a process that cannot accept this must route DLQ payloads through a reference store instead (out of scope for v1).

### Redrive

Redrive republishes DLQ messages to `x-negotex-origin-topic` with the same key. It is an operation of `ops` (ADR-041), exposed through CLI and API:

```
negotex dlq list    --process loan-app --version 1.3.0 [--node credit-check]
negotex dlq redrive --process loan-app --version 1.3.0 --node credit-check [--instance <id>]
```

Semantics:
1. Read the node's DLQ, filter by `processInstanceId` if given.
2. Republish to the origin topic; strip failure headers, increment `x-negotex-redrive-count`.
3. Commit the DLQ offset only after the publish succeeded (at-least-once).
4. Write a `REDRIVEN` audit event for the instance.

Redrive of a node with `consistency: eventual` can execute side effects twice; idempotency remains the handler author's responsibility (ADR-030). Redrive is rejected for a DLQ whose process version has already been removed.

### Interaction with versioning (ADR-023)

- An instance parked in a DLQ is **active**. The drain counter must count it; otherwise old topics would be deleted under it.
- `ops` refuses to delete the topics of a version while any of its DLQ topics is non-empty, unless forced.
- On `drainTimeout`, `ops` moves the remaining envelopes from the edge topics of the old version into the DLQ of the consuming node with reason `MIGRATION_TIMEOUT`.

### Interaction with Join and Wait

A branch parked in a DLQ keeps the Join waiting. Valkey TTLs for Join state must therefore be configured longer than the DLQ retention, or the process-level timeout must be aligned with it. The compiler emits a warning if a process with a Join has a Valkey TTL shorter than its DLQ retention.

## Consequences

**Positive:**
- Failure handling is explicit, inspectable, and consistent for all language kits — header-only metadata keeps the envelope format unchanged.
- Individual instances can be recovered without touching the audit trail or the process definition.
- The hash chain is unaffected: the failed envelope is republished as-is.

**Negative:**
- In-process retry occupies a virtual thread during backoff and delays later envelopes of the same partition; long backoffs are discouraged until retry topics exist.
- Redrive by scanning a DLQ is linear in DLQ size; acceptable at expected failure volumes, revisit if DLQs become large.
- DLQ payloads live outside GDPR erasure for the retention period.

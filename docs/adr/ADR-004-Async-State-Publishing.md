# ADR-004 — Async state publishing

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 1 — Fundamental architecture |
| **Relates to** | ADR-025 (Publisher interface), ADR-027 (Audit storage) |

## Context

Every node transition generates an audit event (ENTERED, EXITED, FAILED) that must be persisted to TimescaleDB. If this write is synchronous — blocking the processor before it publishes the next envelope — the database becomes the throughput bottleneck for the entire system. This negates the horizontal scaling benefit of the orchestrator-free design.

## Decision

State events are published asynchronously, fire-and-forget, via the Publisher. The processor does not wait for the write to complete before publishing the outgoing envelope to Kafka.

The audit trail in TimescaleDB may lag behind the actual execution position by a small, bounded interval. This is acceptable because:

- Kafka provides at-least-once delivery guarantees on the execution path.
- The audit trail is used for compliance queries, monitoring, and debugging — not for routing decisions. No processor reads from TimescaleDB during execution.
- Consumer lag on the audit topic is a monitored metric; excessive lag triggers an alert.

## Consequences

**Positive:**
- Database writes do not block the hot path.
- Throughput is bounded by Kafka partition count and processor count, not by TimescaleDB write latency.
- The Publisher can batch and buffer audit writes internally.

**Negative:**
- Eventual consistency on state queries. A process that has already advanced three nodes may still show as being at node one in the audit trail.
- State lag must be monitored and alerted on.
- Payload at the time of each event must be captured at publish time (before the envelope is mutated by subsequent nodes), not at write time.

# ADR-025 — Publisher interface

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-004 (async publishing) |

## Decision

The Publisher exposes a single public method:

```java
void publish(Envelope<?> envelope, List<String> targetNodeIds);
```

All internal complexity is hidden behind this interface:
- Resolve target node IDs to versioned Kafka topic names.
- Write envelope to each target topic.
- Update Valkey correlation state if the node is a Fork (increment expected-branch counter at the downstream Join).
- Async-write `EXITED` audit event to TimescaleDB.
- Emit throughput and latency metrics to VictoriaMetrics.

Node processors call only `publish()`. They have no direct dependency on Kafka, Valkey, TimescaleDB, or metrics clients.

## Consequences

The Publisher interface is trivially mockable in tests — a single method with no return value. The Publisher implementation is complex, but that complexity is isolated and does not leak into handler or processor code. Changes to routing logic, topic naming, or audit format require no changes to processors.

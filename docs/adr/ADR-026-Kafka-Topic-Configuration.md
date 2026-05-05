# ADR-026 — Kafka topic naming and configuration

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-023 (versioning), ADR-025 (Publisher) |

## Decision

**Topic naming:**
```
{processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}

Example:
  loan-app-v1.3.0.edge.validate-to-credit-check
```

**Partition key:** `processInstanceId` — guarantees ordering of all envelopes belonging to the same process instance within a topic.

**Retention:** Short TTL (1–24h, configurable). Kafka topics are transit storage only. TimescaleDB is the persistent audit trail. Long Kafka retention would waste storage without compliance benefit.

**Topic creation:** The console creates topics at deployment time with the configured partition count and replication factor. Processors do not create topics at startup.

**Consumer group naming:**
```
{processId}-v{version}.{nodeId}
```

Each node type gets its own consumer group, enabling independent scaling via partition assignment.

## Consequences

The naming convention makes topic purpose immediately readable from the broker's topic list. A large process graph with many nodes produces many topics — this is expected and intentional. Topic count does not affect runtime performance; it is a management concern addressed by the console's topic lifecycle tooling.

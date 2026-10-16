# ADR-026 — Kafka topic naming and configuration

| | |
|---|---|
| **Status** | Accepted (amended) |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-023 (versioning), ADR-025 (Publisher), ADR-041 (ops), ADR-042 (retry and DLQ) |

## Decision

**Topic naming:**
```
{processId}-v{version}.{kind}.{name}

Edge topics:
  {processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}
  Example: loan-app-v1.3.0.edge.validate-to-credit-check

Dead-letter topics (ADR-042):
  {processId}-v{version}.dlq.{nodeId}

Reserved for future retry topics (ADR-042):
  {processId}-v{version}.retry.{nodeId}
```

The segment after `edge.` is the **edge ID**: always `{sourceNodeId}-to-{targetNodeId}`. The transport derives the edge ID from the topic name by this rule. To keep the derivation unambiguous, node IDs must match `[a-z0-9]+(-[a-z0-9]+)*` and must not contain the segment `-to-`; `edge`, `dlq` and `retry` are reserved segments. At most one edge may exist per `(source, target)` pair. The process compiler (ADR-041) enforces these rules.

**Partition key:** `processInstanceId` — guarantees ordering of all envelopes belonging to the same process instance within a topic.

**Retention:** Short TTL (1–24h, configurable) for edge topics. Kafka edge topics are transit storage only. TimescaleDB is the persistent audit trail. Long Kafka retention would waste storage without compliance benefit. Dead-letter topics are the exception (default 14 days, ADR-042) because they await manual intervention.

**Topic creation:** `ops` (ADR-041) creates all topics at deployment time with the configured partition count and replication factor. Processors do not create topics at startup.

**Consumer group naming:**
```
{processId}-v{version}.{nodeId}
```

Each node type gets its own consumer group, enabling independent scaling via partition assignment.

## Consequences

The naming convention makes topic purpose immediately readable from the broker's topic list. A large process graph with many nodes produces many topics — edge topics plus one DLQ topic per node — which is expected and intentional. Topic count does not affect runtime performance; it is a management concern addressed by the topic lifecycle tooling in `ops`.

## Amendment history

- Edge ID rule and node ID constraints made explicit (they were implicit in the implementation).
- `dlq` and `retry` segments added; topic creation moved from "the console" to `ops`.

# Negotex Topic Naming Convention

## Pattern

```
{processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}
```

### Components

| Component | Format | Example |
|---|---|---|
| `processId` | kebab-case | `loan-application` |
| `version` | semver | `1.3.0` |
| `sourceNodeId` | kebab-case node ID | `validate` |
| `targetNodeId` | kebab-case node ID | `credit-check` |

### Example

```
loan-application-v1.3.0.edge.validate-to-credit-check
```

## DLQ topic naming

```
{processId}-v{version}.dlq.{nodeId}
```

Example: `loan-application-v1.3.0.dlq.credit-check`

DLQ topics receive envelopes that have exhausted their retry budget at a specific node.
One DLQ topic per node, following the same version namespace as edge topics.
DLQ topics are created by the process compiler alongside edge topics at deployment time.

## Consumer group naming

```
{processId}-v{version}.{nodeId}
```

Example: `loan-application-v1.3.0.credit-check`

## Partition key

`processInstanceId` — guarantees ordering of all envelopes for one process instance within a topic.

## Constraints

- Topic names must match the pattern exactly. The process compiler rejects non-conforming names.
- Topics are created by the process compiler at deployment time. Processors do not create topics.
- Retention: 1–24h (configurable). Kafka is transit storage only — TimescaleDB is the audit trail.

# ADR-023 — Zero-downtime versioning

| | |
|---|---|
| **Status** | Accepted (amended) |
| **Level** | 3 — Multilanguage & runtime |
| **Relates to** | ADR-026 (topic naming), ADR-042 (retry and DLQ) |

## Context

Process definitions change. A new version may modify handler logic, add nodes, or restructure the flow. Long-running process instances (hours, days) may still be in-flight on the old version when a new version is deployed. Cutting over all instances simultaneously causes failures for in-flight instances whose envelope is on a topic the old consumers are no longer listening to.

## Decision

Each process version gets its own isolated set of Kafka topics, named according to ADR-026:

```
{processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}

Examples:
  loan-app-v1.2.0.edge.start-to-validate
  loan-app-v1.3.0.edge.start-to-validate   ← new version, separate topic
```

The deployment strategy is **drain-and-switch**:

1. Deploy new version (creates new topics, starts new processor instances).
2. Pause the old version's Trigger node — no new instances start on the old version.
3. Old processor instances continue consuming from old topics until all in-flight instances complete.
4. Monitor active instance count on the old version. An instance whose envelope is parked in a DLQ (ADR-042) counts as active.
5. When count reaches zero and all DLQ topics of the old version are empty, remove old processor instances and delete old topics.

```yaml
process:
  id: loan-application
  version: 1.3.0
  migration:
    from: 1.2.0
    strategy: drain-and-switch
    drainTimeout: 24h
```

`drainTimeout` sets the maximum time to wait before force-removing old instances. When it expires, `ops` moves the envelopes still in the edge topics of the old version into the DLQ of the consuming node with reason `MIGRATION_TIMEOUT` (ADR-042). Topics of a version with a non-empty DLQ are only deleted when explicitly forced.

A `gradual-rollout` strategy (percentage-based traffic split between versions) is planned for the Enterprise Control Plane.

## Consequences

**Positive:**
- In-flight instances complete on the version they started on — no envelope format mismatch.
- New instances immediately use the new version's topics and handlers.
- No coordinated cutover window required.

**Negative:**
- Both versions run simultaneously during the drain window, consuming Kafka resources for both topic sets.
- Long-running instances extend the drain window; `drainTimeout` is a safety valve, not a substitute for appropriate process timeouts.
- Instances parked in a DLQ keep an old version alive until they are redriven or discarded.

## Amendment history

- Topic naming corrected from `edge-{edgeId}` to the ADR-026 scheme, matching the implementation.
- DLQ handling added (ADR-042).

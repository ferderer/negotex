# ADR-012 — Filter primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Inclusive Gateway (diverging) |

## Decision

**Structure:** 1 incoming edge, N outgoing edges, 0–N activated, Valkey state.

**Semantics:** Conditional multi-path activation (OR split). The handler evaluates the payload and returns the set of edges to activate. Zero edges is valid — the envelope is consumed and dropped. The set of activated edges is stored in Valkey so that a paired Join node knows how many branches to wait for.

**Handler interface:**
```java
@FunctionalInterface
interface FilterHandler {
    Set<String> selectEdges(Map<String, Object> payload);
}
```

**Process definition:**
```yaml
- id: apply-notifications
  type: filter
  edges:
    incoming: decision-made
    outgoing:
      - id: email-notification
        condition: "notifications.contains('email')"
      - id: sms-notification
        condition: "notifications.contains('sms')"
    default: email-notification
```

## Consequences

A downstream Join paired with a Filter reads the activated-edges record from Valkey to determine its expected branch count (rather than using a fixed `expectedBranches` value). The `processInstanceId + filterNodeId` key is the correlation handle. TTL rules follow the same pattern as Join state.

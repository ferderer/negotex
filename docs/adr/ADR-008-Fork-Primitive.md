# ADR-008 — Fork primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Parallel Gateway (diverging) |

## Decision

**Structure:** 1 incoming edge, N outgoing edges (N ≥ 2), no state, no handler.

**Semantics:** Unconditional parallel split. The same envelope is published to all N outgoing topics. All branches are always activated — there are no conditions. Fork is a pure infrastructure primitive; business logic belongs in downstream Map nodes.

The envelope is not cloned. The same immutable `Envelope` object is published to all topics — safe because envelopes are immutable (ADR-002).

**Processor logic:**
```java
void process(Envelope envelope) {
    for (String edgeId : outgoingEdges) {
        publisher.publish(edgeId, envelope);
    }
}
```

**Process definition:**
```yaml
- id: split-checks
  type: fork
  edges:
    incoming: application-validated
    outgoing:
      - credit-check
      - income-check
      - fraud-check
```

## Consequences

Fork has no error conditions of its own. Partial fan-out (some topics published, some not) is handled by Kafka's at-least-once delivery guarantees. The paired Join node tracks expected envelope count and will not proceed until all branches arrive.

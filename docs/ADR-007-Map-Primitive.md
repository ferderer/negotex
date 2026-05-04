# ADR-007 — Map primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Service Task, Script Task, Business Rule Task |

## Decision

**Structure:** 1 incoming edge, 1 outgoing edge, no state, no correlation.

**Semantics:** Pure function transformation — `Input → Output`. The handler receives a typed input extracted from the envelope payload, returns a typed output. The processor adds the output as a new attribute on the payload (additive enrichment per ADR-002) and publishes the enriched envelope downstream.

**Handler interface:**
```java
@FunctionalInterface
interface TaskHandler<I, O> {
    O handle(I input);
}
```

**Processor lifecycle:**
1. Receive envelope from incoming Kafka topic.
2. Persist `ENTERED` event (async).
3. Extract typed input from payload.
4. Call handler: `input → output`.
5. Produce enriched payload: original payload + output attribute.
6. Publish outgoing envelope to Kafka.
7. Persist `EXITED` event (async).

**Process definition:**
```yaml
- id: credit-check
  type: map
  handler: CreditCheckHandler
  input: application
  output: creditScore
  edges:
    incoming: application-validated
    outgoing: credit-checked
```

## Consequences

Map is the most common node type. Because the handler is a pure function, it is unit-testable without infrastructure and deterministic by design — making it compatible with Audit Certification Mode (ADR-030).

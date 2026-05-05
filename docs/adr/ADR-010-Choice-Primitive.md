# ADR-010 — Choice primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Exclusive Gateway (diverging) |

## Decision

**Structure:** 1 incoming edge, N outgoing edges, exactly 1 activated, no state.

**Semantics:** Routes the envelope to exactly one outgoing edge based on a condition. A default edge is required and activates if no other condition matches.

**Handler interface:**
```java
@FunctionalInterface
interface ChoiceHandler {
    String selectEdge(Map<String, Object> payload);  // null → default edge
}
```

**Condition strategies:**

| Strategy | Description |
|---|---|
| `expression` (default) | Simple expression evaluated against payload fields |
| `handler` | Custom Java / F# / Rust handler |
| `script-{lang}` | JSR-223 scripting |

**Process definition:**
```yaml
- id: route-by-amount
  type: choice
  conditionStrategy: expression
  edges:
    incoming: application-received
    outgoing:
      - id: small-loan
        condition: "amount < 1000"
      - id: medium-loan
        condition: "amount < 10000"
      - id: large-loan
        default: true
```

## Consequences

Exactly-one semantics are enforced by the processor: if a handler returns an unknown edge ID, the processor throws rather than silently dropping the envelope. Condition evaluation failure routes the envelope to the DLQ.

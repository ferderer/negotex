# ADR-011 — Merge primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Exclusive Gateway (converging) |

## Decision

**Structure:** N incoming edges, 1 outgoing edge, no state, no handler.

**Semantics:** Pass-through join — no synchronisation. Each arriving envelope is immediately forwarded to the outgoing edge without waiting for other branches. Used as a convergence point for branches that were split by Choice (only one branch was ever active) or branches guaranteed to be mutually exclusive by design.

**Processor logic:**
```java
void process(Envelope envelope) {
    publisher.publish(outgoingEdge, envelope);
}
```

The processor subscribes to all N incoming topics. Any envelope on any topic is immediately forwarded.

## Consequences

Merge must not be used as a synchronisation point for truly parallel branches — use Join instead. Using Merge where Join is needed silently causes downstream nodes to receive partial payloads with no error indication.

# ADR-014 — Trigger primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Start Event (None, Message, Timer, Signal) |

## Decision

**Structure:** 0 incoming edges, 1 outgoing edge, no state.

**Semantics:** Creates the initial envelope. Trigger is structurally a normal node with one distinction: it has no incoming edge and is responsible for envelope creation. The trigger type determines what calls the processor — not how it works.

**Handler interface:**
```java
@FunctionalInterface
interface TriggerHandler {
    Map<String, Object> createPayload(Object externalEvent);
}
```

The processor computes the initial `_hash`, creates the `Envelope`, persists the `STARTED` audit event, and publishes to the outgoing topic.

**Trigger types:**

| Type | Activation |
|---|---|
| `api` | HTTP POST to `/api/processes/{processId}/start` |
| `message` | Kafka topic subscription |
| `timer` | Cron expression |
| `signal` | Signal broadcast API |

A process may have multiple Trigger nodes. All create independent process instances.

## Consequences

Swapping trigger type (API → Kafka → timer) does not change the handler or any downstream node. The trigger type is an infrastructure wiring detail confined to the process definition.

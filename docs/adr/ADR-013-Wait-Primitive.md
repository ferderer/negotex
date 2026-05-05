# ADR-013 — Wait primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | User Task, Manual Task, Message Intermediate Event, Timer Intermediate Event |

## Decision

**Structure:** 1 incoming edge, 1 outgoing edge, Valkey pending queue.

**Semantics:** Suspends the process instance until an external event resumes it. On receiving an envelope, the processor assigns a `taskId`, stores the envelope in Valkey, and calls `onSuspend`. The processor polls Valkey at a fixed interval for completions. When a completion arrives via the external API, `onResume` enriches the payload and the envelope is published downstream.

**Handler interface:**
```java
interface WaitHandler {
    void onSuspend(String taskId, Map<String, Object> context);
    Map<String, Object> onResume(String taskId, Map<String, Object> externalData);
}
```

**External completion:**
```
POST /api/tasks/{taskId}/complete
{ "outcome": "approved", "comment": "..." }
```

**Timer variant:** Register a TTL on the Valkey key. Expiry triggers automatic resumption with a timeout payload.

## Consequences

The `taskId` is the external handle — it must be communicated to external actors (e.g. included in an approval email or task system). Long-running tasks require Valkey persistence (AOF/RDB) to survive restarts. Uncompleted tasks accumulate in Valkey; process-level timeouts must be configured to bound this.

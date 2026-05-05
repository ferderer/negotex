# ADR-015 — Terminate primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | End Event |

## Decision

**Structure:** 1 incoming edge, 0 outgoing edges, optional handler.

**Semantics:** Ends the process instance. Publishes the `END` audit event with the complete final payload to TimescaleDB (this is the long-retention compliance record per ADR-027). Cleans up all Valkey correlation state for the `processInstanceId`. Optionally calls a `TerminateHandler` for cleanup side effects.

**Handler interface (optional):**
```java
@FunctionalInterface
interface TerminateHandler {
    void onTerminate(Map<String, Object> finalPayload);
}
```

A process may have multiple Terminate nodes representing different outcomes:
```yaml
- id: end-approved
  type: terminate
  edges:
    incoming: approval-granted

- id: end-rejected
  type: terminate
  handler: RejectionNotificationHandler
  edges:
    incoming: approval-denied
```

## Consequences

The `END` event carries the full final payload and is the primary record for compliance queries. Valkey cleanup at Terminate is best-effort; a TTL-based background job handles cases where Terminate was not reached (e.g. process timeout or crash).

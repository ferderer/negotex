# ADR-005 — Handlers as pure functions

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **Relates to** | ADR-002 (Envelope), ADR-006 (Node primitives) |

## Context

Business logic in a workflow engine needs to be testable in isolation, free of infrastructure dependencies, and deployable without knowledge of the surrounding topology. If handlers import Kafka clients, database connections, or envelope routing logic, they become coupled to the runtime and untestable without a full infrastructure stack.

## Decision

Every handler is a pure function with zero infrastructure dependencies:

```java
@FunctionalInterface
interface TaskHandler<I, O> {
    O handle(I input);
}
```

The handler receives a typed input extracted from the envelope payload by the Node Processor. It returns a typed output. It has no access to the `Envelope`, the `Publisher`, or any infrastructure component. It cannot publish to Kafka, read from Valkey, or write to TimescaleDB.

All infrastructure interaction — receiving the envelope, persisting audit events, publishing the outgoing envelope — is the exclusive responsibility of the Node Processor that wraps the handler.

Specialised handler interfaces exist per primitive type:

| Primitive | Handler interface | Signature |
|---|---|---|
| Map | `TaskHandler<I, O>` | `I → O` |
| Choice | `ChoiceHandler` | `payload → edgeId` |
| Filter | `FilterHandler` | `payload → Set<edgeId>` |
| Wait | `WaitHandler` | `onSuspend(taskId, ctx)` / `onResume(taskId, data) → payload` |
| Trigger | `TriggerHandler` | `externalEvent → payload` |
| Terminate | `TerminateHandler` | `onTerminate(payload)` (optional cleanup hook) |

Fork, Join, and Merge have no handler — they are pure infrastructure primitives.

## Consequences

**Positive:**
- Handlers are unit-testable without any mocking of infrastructure.
- The same handler runs identically in all deployments and all languages (Java, F#, Rust).
- Pure functions are the foundation for Consistency Contracts (ADR-030): a handler that has no side effects is trivially deterministic.

**Negative:**
- Handlers cannot access envelope metadata (correlation ID, timestamps) — this is intentional but occasionally inconvenient for debugging.
- Cross-cutting concerns (logging, metrics) must be injected by the processor wrapper, not written into the handler.

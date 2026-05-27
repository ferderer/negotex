# ADR-041 — Persistence layer abstraction

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-004 (async state publishing), ADR-027 (audit storage), ADR-040 (transport layer abstraction) |

## Context

ADR-027 mandates TimescaleDB for audit event persistence. Like the transport layer (ADR-040), this creates implicit vendor lock-in. Organisations with existing Oracle, PostgreSQL, or other RDBMS infrastructure may require a different persistence backend. The compliance requirements (retention classification, GDPR erasure, legal hold) are schema-level concerns — they do not mandate a specific database engine.

The `EnvelopeEventWriter` interface was already introduced during runtime implementation. This ADR formalises the abstraction, the module structure, and the factory pattern.

## Decision

`EnvelopeEventWriter` is defined in `negotex-runtime-api`. Concrete implementations ship as separate Maven modules. The Runtime has no compile-time dependency on any persistence implementation.

### Event model

A sealed hierarchy of envelope processing events drives the persistence contract:

```java
public sealed interface EnvelopeEvent
        permits EnvelopeEvent.Completion,
                EnvelopeEvent.Failure,
                EnvelopeEvent.Termination {

    record Completion(Envelope envelope, String nodeId,
                      List<String> targetTopics, long durationMs)
            implements EnvelopeEvent {}

    record Failure(Envelope envelope, String nodeId,
                   Exception error, int retryCount,
                   int maxRetries, String outcome)
            implements EnvelopeEvent {}

    record Termination(Envelope envelope, String terminalNodeId,
                       long durationMs)
            implements EnvelopeEvent {}
}
```

### Interface

```java
public interface EnvelopeEventWriter {
    void write(EnvelopeEvent event);   // always async — must never block the hot path
}
```

### Factory pattern

Each persistence module provides exactly one `EnvelopeEventWriterFactory` as a Spring `@Service`:

```java
// in negotex-persistence-timescale
@Service
public class TimescaleEnvelopeEventWriterFactory
        implements EnvelopeEventWriterFactory {
    public EnvelopeEventWriter create(InfrastructureManifest infrastructure) {
        return new TimescaleEnvelopeEventWriter(infrastructure);
    }
}
```

Only one persistence module may be active — Spring throws `NoUniqueBeanDefinitionException` if multiple factories are present.

### Event routing

The `Publisher` (ADR-025) routes events to the appropriate table via switch pattern-matching on the sealed hierarchy:

| Event type | Table |
|---|---|
| `Completion` | `node_completions` |
| `Failure` | `node_failures` |
| `Termination` | `process_terminations` |

### Module structure

| Module | Description |
|---|---|
| `negotex-persistence-timescale` | TimescaleDB — canonical implementation, full retention classification, hash chain (ADR-027, ADR-028) |
| `negotex-persistence-noop` | No-op, logs at DEBUG. PoC and testing. |

Community-contributed implementations may target PostgreSQL, Oracle, or any JDBC-compatible database. The schema defined in `protocol/audit-event-schema.sql` is the canonical reference — implementations must honour the three-table structure and the event type semantics.

## Consequences

**Positive:**
- Infrastructure-agnostic persistence. Swap TimescaleDB for Oracle with one dependency change — no process definition changes, no migration scripts.
- The sealed `EnvelopeEvent` hierarchy guarantees exhaustive handling in all implementations — the compiler enforces that every event type is handled.
- The no-op implementation enables PoC deployments and integration testing without a database.
- All writes are async by contract — no implementation can accidentally block the hot path.

**Negative:**
- Implementations must faithfully reproduce the retention classification semantics (ADR-027) and hash chain storage (ADR-028) for compliance guarantees to hold. A naive implementation that drops classification metadata silently breaks regulatory compliance.
- The `protocol/audit-event-schema.sql` schema is the canonical reference but is TimescaleDB-specific (hypertables). Oracle or PostgreSQL implementations must adapt the schema while preserving the semantic guarantees.

# ADR-018 — Multilanguage node processors

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 3 — Multilanguage & runtime |

## Decision

All supported languages (Java, F#, Rust) are first-class node processor implementations. There is no "External Task" pattern — no language is a second-class citizen that polls a task queue managed by a Java runtime.

Each language's processor kit communicates directly with Kafka, Valkey, and TimescaleDB using the language's native clients. A node implemented in F# consumes from and publishes to Kafka topics the same way a Java node does — via the shared topic naming convention (ADR-026) and the same envelope serialisation format.

**Processor kit responsibilities per language:**
- Kafka consumer/producer (topic subscription, envelope deserialisation)
- Valkey client (correlation state for Join/Filter/Wait)
- TimescaleDB writer (audit events, async)
- VictoriaMetrics client (metrics)
- Handler invocation

## Consequences

**Positive:**
- No latency overhead from polling an intermediary task queue.
- Polyglot teams can implement any node in their language of choice.
- Financial services teams can use F# for quant and risk nodes without an "External Task" wrapper.

**Negative:**
- Each processor kit must implement the full Negotex protocol (topic naming, envelope format, correlation semantics). This is significant up-front investment per language.
- Protocol evolution must be backward-compatible across all kits simultaneously.

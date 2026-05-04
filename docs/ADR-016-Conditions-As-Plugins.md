# ADR-016 — Conditions as plugins

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **Relates to** | ADR-010 (Choice), ADR-012 (Filter) |

## Decision

Condition evaluation in Choice and Filter nodes is a plugin. The plugin type is configured per edge in the process definition. Built-in types: `expression` (simple field comparisons), `handler` (custom Java/F#/Rust code), `script-{lang}` (JSR-223 scripting). Users can register additional condition plugin types.

A `ConditionHandler` has the signature `payload → boolean`.

## Consequences

Simple conditions (field comparisons, threshold checks) require no custom code. Complex conditions (ML model inference, external lookups) are first-class handler plugins with the same packaging and deployment model as task handlers. This avoids the need for a domain-specific condition language that would need its own parser and security sandbox.

# ADR-006 — Nine node primitives

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **Relates to** | ADR-007 through ADR-015 (individual primitives) |

## Context

A BPMN execution engine must cover the full BPMN 2.0 execution semantics: sequential flow, parallel split and join, conditional routing, inclusive routing, suspension, process instantiation, and termination. The set of built-in node types must be both complete (covers all BPMN control flow patterns) and minimal (no redundant abstractions).

## Decision

Negotex implements nine orthogonal node primitives. Every BPMN process graph can be expressed using this set.

| Primitive | BPMN equivalent | Edges | State | Handler |
|---|---|---|---|---|
| **Map** | Service / Script / Business Rule Task | 1 in, 1 out | None | Required |
| **Fork** | Parallel Gateway (diverging) | 1 in, N out | None | None |
| **Join** | Parallel Gateway (converging) | N in, 1 out | Valkey | Optional (merge strategy) |
| **Choice** | Exclusive Gateway (diverging) | 1 in, N out (1 activated) | None | Required |
| **Merge** | Exclusive Gateway (converging) | N in, 1 out | None | None |
| **Filter** | Inclusive Gateway (diverging) | 1 in, N out (0–N activated) | Valkey | Required |
| **Wait** | User / Message / Timer Intermediate Event | 1 in, 1 out | Valkey | Required |
| **Trigger** | Start Event (any type) | 0 in, 1 out | None | Required |
| **Terminate** | End Event | 1 in, 0 out | None | Optional |

Design principles applied:
- **Stateless where possible.** Map, Fork, Choice, Merge, and Terminate carry no state between envelopes. Only Join, Filter, and Wait require Valkey state.
- **No handler where none is needed.** Fork and Merge are pure topology primitives. Requiring a handler would add ceremony with no value.
- **Single responsibility.** Each primitive does exactly one thing. Fork never routes; Choice never waits.

## Consequences

**Positive:**
- The primitive set is finite and learnable. Any process engineer who understands these nine types can read any Negotex process definition.
- Stateless primitives are trivially horizontally scalable — no state coordination required.
- Adding a new BPMN construct does not require new primitives; it is expressed as a composition of existing ones.

**Negative:**
- Some higher-level BPMN constructs (e.g. event-based gateways) require composition of multiple primitives — the process definition is slightly more verbose than a native BPMN representation.

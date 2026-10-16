# ADR-036 — Console UI architecture

| | |
|---|---|
| **Status** | Superseded by ADR-040 |
| **Level** | 7 — Tooling |
| **Relates to** | ADR-032 (OSS console vs Enterprise CP) |

## Decision

The OSS console uses Okygraph (Vadim's own template engine) for server-side rendering of approximately 90% of the UI. Svelte islands handle interactive components: topology graph, process instance timeline, live metric charts. A no-JS SSR fallback is provided for restricted environments.

**Rationale for Okygraph:** The console is a showcase for the engine — using it in a real production application validates it and generates real-world feedback. SSR-first keeps the initial payload lightweight (~22 KB JS for Svelte islands vs a full SPA).

## Consequences

Fast time-to-first-byte. Minimal JavaScript footprint. The console doubles as a live integration test for Okygraph. Maintaining a custom template engine adds a dependency that must be kept compatible with console feature requirements.

## Superseded

Replaced by ADR-040: the console is a Svelte 5 SPA with Texivia routing. SEO and the no-JS fallback do not matter for an internal operations tool, and a single UI technology for OSS console and ECP outweighs the showcase argument. Okygraph continues to serve negotex.dev and the documentation.

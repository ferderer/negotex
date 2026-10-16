# ADR-040 — Console UI: Svelte 5 and Texivia

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 7 — Tooling |
| **Supersedes** | ADR-036 |
| **Amends** | ADR-032 |
| **Relates to** | ADR-041 (ops), ADR-039 (ECP UI/CLI distribution) |

## Context

ADR-036 chose Okygraph (server-side rendering) with Svelte islands for the OSS console. Its rationale was a showcase for the engine, a small initial payload, and a no-JS fallback. The console is an internal operations tool: search-engine visibility is irrelevant, the interesting views (instance timeline, topology, live lag) are interactive anyway, and the no-JS fallback has no target audience. Two UI technologies (Okygraph for OSS, Svelte for the ECP) were the principal cost identified in ADR-032.

## Decision

The OSS console is a single-page application built with **Svelte 5** (runes) and **Texivia** for routing.

- The console is a pure client of the REST API (ADR-041 for deployment operations, the instance API for monitoring). It contains no server-side rendering and no business logic.
- The API is described by an OpenAPI specification shared by console, CLI and ECP.
- The production build consists of static assets served from the runtime JAR. There is still no separate console process (ADR-032).
- Live views (consumer lag, instance timeline, topology) use SSE.
- The shared Apache 2.0 component library is created as its own package from the start and is used by both the OSS console and the Enterprise Control Plane (ADR-032).
- Okygraph remains in use for negotex.dev and the documentation, where server-side rendering and SEO matter.

Dropped from ADR-036: the no-JS fallback and the claim of a minimal JavaScript footprint.

## Consequences

**Positive:**
- One UI technology across OSS console and ECP; the component library becomes shared in practice.
- The console adds no capability the API does not expose, which keeps CLI, console and ECP at parity (ADR-039).
- No custom template engine in the operational path of the product.

**Negative:**
- Larger initial JavaScript payload than SSR.
- Console and API must be versioned together; breaking API changes affect the static bundle shipped inside the JAR.
- Texivia becomes a dependency of the console and must be kept compatible with its requirements.

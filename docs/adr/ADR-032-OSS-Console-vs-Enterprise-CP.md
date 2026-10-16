# ADR-032 — OSS console vs Enterprise Control Plane

| | |
|---|---|
| **Status** | Accepted (amended) |
| **Level** | 6 — Business & product |
| **Relates to** | ADR-040 (console UI), ADR-041 (ops) |

## Decision

**OSS console:** Read-only monitoring and direct process deployment. A Svelte 5 single-page application with Texivia routing (ADR-040), delivered as static assets inside the runtime JAR — no separate process. It is a pure client of the REST API; deployment operations are served by `ops` (ADR-041).

**Enterprise Control Plane:** Full management capabilities — governance policies, blue/green deployments, multi-cluster, rollback, compliance reporting. Built with Svelte 5 (rune-based state management), embedded in the Rust control plane binary. Separate deployment.

A shared Apache 2.0 component library, maintained as its own package, provides common UI components used by both shells.

## Consequences

OSS users have a functional, useful console without any commercial dependency. The Enterprise Control Plane upgrade path is clear — same technology, same component vocabulary, additional capabilities. Both shells now use one UI technology, which removes the main cost named in the earlier version of this ADR (two UI stacks); what remains is maintaining two shells over one API.

## Amendment history

- Okygraph SSR with Svelte islands replaced by a Svelte 5 SPA with Texivia (ADR-040).

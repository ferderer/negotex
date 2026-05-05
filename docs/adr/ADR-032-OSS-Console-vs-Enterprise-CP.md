# ADR-032 — OSS console vs Enterprise Control Plane

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 6 — Business & product |

## Decision

**OSS console:** Read-only monitoring and direct process deployment. Built with Okygraph (SSR, embedded in the Java runtime) with Svelte islands for interactive components (topology visualisation, timeline). Deployed as part of the runtime JAR — no separate process.

**Enterprise Control Plane:** Full management capabilities — governance policies, blue/green deployments, multi-cluster, rollback, compliance reporting. Built with Svelte 5 (rune-based state management), embedded in the Rust control plane binary. Separate deployment.

A shared Apache 2.0 component library provides common UI components used by both shells.

## Consequences

OSS users have a functional, useful console without any commercial dependency. The Enterprise Control Plane upgrade path is clear — same component vocabulary, additional capabilities. Maintaining two UI shells is the principal cost.

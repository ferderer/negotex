# ADR-031 — Open-core model and enterprise separation

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 6 — Business & product |

## Decision

The Negotex core is released under Apache 2.0. The community edition is complete and production-ready — not a teaser for the Enterprise edition. Any team can run Negotex in production without a commercial licence.

The Enterprise Control Plane is a separate application (written in Rust) that observes, manages, and controls Negotex runtimes — but never executes workflow logic itself. This architectural separation maintains a clean IP boundary: the OSS core and the Enterprise Control Plane can be developed and released independently. The Enterprise Control Plane is closed-source and commercially licensed.

**Enterprise-only capabilities:** multi-cluster management, blue/green deployments, automated rollback, compliance reporting, Process Replay Debugging, SLA monitoring, advanced audit queries.

**Community capabilities (complete):** full workflow execution, all nine primitives, envelope hash chains, execution contracts, consistency contracts, OSS console (read-only monitoring + direct deployment).

A stable interface contract between the OSS runtime and the Enterprise Control Plane is required and maintained as a versioned API.

## Consequences

Adoption without licence friction. Enterprise features motivate organisations that outgrow the community capabilities to upgrade rather than fork. The clean IP boundary allows the Enterprise Control Plane to be closed-source without contaminating the Apache 2.0 core.

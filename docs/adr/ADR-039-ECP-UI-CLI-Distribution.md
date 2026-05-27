# ADR-039 — ECP UI and CLI functional distribution

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 7 — Tooling |
| **Relates to** | ADR-037 (Kubernetes Operator), ADR-038 (infrastructure split), ADR-034 (project generator) |

## Decision

All ECP management operations are expressed as Kubernetes CRD mutations. The ECP UI and CLI are two surfaces over the same underlying API — neither has exclusive functionality. Any action taken in the UI produces a Kubernetes object that can equivalently be authored as a manifest and applied via `kubectl` or a GitOps pipeline.

**GitOps compatibility as a design principle:** The ECP is not a closed management system. Every runtime deployment, node assignment, and process deployment is representable as a declarative manifest. Teams that prefer GitOps workflows (ArgoCD, Flux) can manage their entire Negotex topology from Git without touching the UI.

**Functional distribution by persona:**

| Capability | UI | CLI / Manifests | Notes |
|---|---|---|---|
| Create / scale NegotexRuntime | ✅ | ✅ | UI adds visual topology; both produce the same CRD |
| Assign process nodes to runtimes | ✅ | ✅ | UI preferred — visual drag-assign over Node grid |
| Deploy process definition | ✅ | ✅ | CLI preferred for CI/CD pipelines |
| Blue/green cutover | ✅ | ✅ | UI preferred — progressive rollout visualisation |
| View runtime topology | ✅ | — | Read-only visualisation; no CLI equivalent needed |
| Live metrics / consumer lag | ✅ | — | Operational monitoring; UI only |
| Audit queries / compliance reports | ✅ | — | Compliance Officer persona; UI only |
| Hash-chain verification | ✅ | ✅ | CLI useful for scripted verification in CI |
| Process Replay Debugging | ✅ | — | Enterprise UI feature |
| Node pool discovery (read labels) | ✅ | — | ECP reads available Nodes; displayed in UI |

**What is explicitly outside ECP scope (both UI and CLI):**

Cluster provisioning, Node pool management, Helm chart configuration, Kafka topic capacity planning, TimescaleDB schema migrations. These are Layer 1 and Layer 2 concerns (ADR-038) and are not exposed in ECP at all — not as read-only views, not as configuration surfaces.

**CLI scope:**

The ECP CLI (`negotex-ecp`) covers the machine-friendly subset of operations: process definition deployment, runtime scaling, hash-chain verification, status queries. It is a thin client over the Kubernetes API — it creates and reads CRDs, it does not embed Operator logic.

The OSS CLI (`negotex`, distributed via SDKMAN — ADR-035) covers project generation, process validation, and Docker Compose deployment. The two CLIs are distinct binaries with distinct distribution channels. The ECP CLI ships with the Enterprise Control Plane.

## Consequences

Teams with strict GitOps requirements can adopt the Enterprise Control Plane without abandoning their existing workflows — the UI is additive, not mandatory. Compliance Officers and Operations personas, who are UI-first, are not affected by how Platform Engineers choose to manage manifests. The clean separation between the two CLIs maintains the IP boundary: the OSS CLI contains no Enterprise logic.

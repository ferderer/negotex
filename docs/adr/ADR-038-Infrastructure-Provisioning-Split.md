# ADR-038 — Infrastructure provisioning split

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 6 — Business & product |
| **Relates to** | ADR-037 (Kubernetes Operator), ADR-003 (unified infrastructure stack) |

## Decision

Infrastructure provisioning is divided into three layers. Negotex — both OSS and Enterprise — is responsible only for the third layer. The first two layers are the customer's responsibility, managed with tools of their choice.

**Layer 1 — Cluster infrastructure (customer-owned)**

Kubernetes Nodes, Node pools, network topology, storage classes, IAM roles, firewall rules. Provisioned with Terraform, Pulumi, cloud-provider tooling, or manually. Negotex has no opinion on tooling and no integration with it.

The customer's Platform Engineers are responsible for labelling Nodes according to the `negotex.dev/runtime-kit` scheme (ADR-037) and for applying taints as appropriate. These labels are the only interface between Layer 1 and Layer 3.

**Layer 2 — Infrastructure workloads (customer-owned)**

Kafka, TimescaleDB, VictoriaMetrics, the ECP itself. Deployed via Helm, ArgoCD, Flux, or equivalent GitOps tooling. Negotex publishes Helm charts for each component as a convenience; customers may substitute compatible alternatives (e.g. Redpanda for Kafka).

**Layer 3 — Negotex runtime workloads (ECP-managed)**

Runtime Deployments, Runtime Manifests, sidecar configuration, process topic lifecycle. Managed by the ECP Operator (ADR-037). The ECP receives the coordinates of Layer 2 resources — Kafka broker addresses, TimescaleDB connection strings, VictoriaMetrics remote-write endpoint — as configuration. It does not provision or manage those resources.

**What the ECP knows about the layers below it:**

| Resource | ECP access |
|---|---|
| Kubernetes Nodes with `negotex.dev/runtime-kit` labels | Read — for runtime placement decisions |
| Kafka broker endpoints | Configuration input — not managed |
| TimescaleDB connection | Configuration input — not managed |
| VictoriaMetrics endpoint | Configuration input — not managed |
| Node pool sizing, instance types, network topology | Not visible, not relevant |

**Namespace structure (recommended):**

```
negotex-runtimes/    — NegotexRuntime Pods (Operator-managed)
negotex-system/      — ECP Deployment, Operator controller
```

Infrastructure workloads (Kafka, TimescaleDB, VictoriaMetrics) are deployed in customer-defined namespaces. The ECP does not assume their location — endpoints are supplied as configuration.

## Consequences

Negotex has a minimal operational footprint. It does not need cloud-provider credentials, Terraform state, or access to infrastructure APIs. The ECP is portable across cloud providers and on-premises Kubernetes without modification. Layer 1 and Layer 2 can be managed with any GitOps or IaC toolchain the customer prefers — Negotex is not prescriptive about either.

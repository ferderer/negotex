# ADR-037 — Kubernetes Operator as Enterprise Control Plane component

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 6 — Business & product |
| **Relates to** | ADR-031 (open-core model), ADR-038 (infrastructure split), ADR-039 (ECP UI/CLI distribution) |

## Context

Negotex runtimes running in Kubernetes require programmatic management: creating Deployments, enforcing language constraints, generating sidecar configurations, and keeping runtime state consistent with the desired topology. This management capability must be assigned to either the OSS layer or the Enterprise Control Plane.

Kubernetes implies production-scale deployment — multi-cluster management, blue/green, SLA monitoring — which is the defined Enterprise upgrade trigger (ADR-031). OSS users deploy via Docker Compose; the natural escalation to Kubernetes coincides with the need for Enterprise capabilities.

## Decision

The Negotex Kubernetes Operator is an Enterprise Control Plane component. It is closed-source and ships embedded in the ECP binary. It is not available in the community edition.

The OSS deployment boundary is Docker Compose. Any team operating Negotex on Kubernetes has outgrown the community deployment model and is in the Enterprise upgrade path. Requiring an ECP licence for Kubernetes is not licence friction — it is the natural capability boundary.

**The Operator manages the following Kubernetes resources on behalf of the ECP:**

- `NegotexRuntime` CRDs → `Deployment` per runtime instance, with language-constrained `nodeAffinity`
- `ConfigMap` per runtime carrying the Runtime Manifest (ADR-022)
- `vmagent` sidecar container injected into every runtime Pod, pre-configured to scrape the runtime's metrics endpoint and push to VictoriaMetrics
- `Service` per runtime for intra-cluster reachability

**Language constraint enforcement via Node labels:**

Kubernetes Nodes are labelled by the customer's Platform Engineers using the scheme:

```
negotex.dev/runtime-kit = java-1.2
negotex.dev/runtime-kit = fsharp-1.0
```

The label value encodes `{language}-{runtime-api-version}`. The runtime API version defines the contract — envelope format, Kafka protocol, management endpoints — that a runtime kit must implement. The Operator sets `requiredDuringSchedulingIgnoredDuringExecution` nodeAffinity on each Deployment, matching the `runtime-kit` label of the assigned language and API version. A Pod that requires `fsharp-1.0` will not be scheduled on a Node labelled `java-1.2`.

**Taint/toleration pattern:** Customer-managed Nodes may carry a `negotex.dev/dedicated` taint to prevent non-Negotex workloads from landing on runtime Nodes. The Operator adds the corresponding toleration automatically to all Negotex runtime Deployments.

**CRD schemata are publicly documented** as part of the ECP API specification. Customers may author `NegotexRuntime` manifests manually or via GitOps tooling — the Operator reconciles them regardless of origin. The controller implementation remains closed-source.

**vmagent sidecar generation:**

Each runtime Pod receives a `vmagent` sidecar container. The Operator generates a per-Pod `ConfigMap` configuring vmagent to scrape `localhost:{metricsPort}` and remote-write to the VictoriaMetrics endpoint registered in the ECP. The sidecar shares the Pod network namespace — no service discovery required.

## Consequences

The IP boundary established in ADR-031 is preserved: the OSS runtime JAR and the Operator are independent; the Operator adds no capability to the OSS runtime itself. Platform Engineers on the customer side retain full control of cluster infrastructure — Node provisioning, labelling, and taint configuration are their responsibility. The ECP reads Node labels as discovery input but never creates or modifies Nodes.

# ADR-022 — Runtime manifest

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 3 — Multilanguage & runtime |

## Decision

The console generates a Runtime Manifest per runtime instance at deployment time. The manifest contains everything the runtime needs to start:

| Section | Content |
|---|---|
| `process` | Process ID, version, node definitions |
| `runtime` | Language, handler artifact location |
| `handlers` | Handler reference → implementation mapping |
| `infrastructure` | Kafka brokers, Valkey URL, TimescaleDB URL, VictoriaMetrics URL |
| `topics` | Incoming and outgoing topic name per node |
| `secrets` | References only (names/paths) — never values |

Secrets are resolved by the runtime itself from Vault or environment variables at startup. The manifest is delivered via Kubernetes ConfigMap or mounted volume.

## Consequences

A single document contains everything needed to start a runtime instance — no runtime coordination with the console after startup. The manifest is debuggable (inspectable YAML/JSON). Secret values never appear in the manifest, preserving security properties of secret management systems.

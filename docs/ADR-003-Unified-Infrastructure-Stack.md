# ADR-003 — Unified infrastructure stack

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 1 — Fundamental architecture |

## Context

An earlier design proposed three deployment profiles (Micro, Standard, Distributed) with different backends per profile: Chronicle Queue for embedded transport, H2/SQLite for embedded state, PostgreSQL for production state. The intent was to lower the barrier for local development.

The profile model creates the following problems:

- Two code paths for transport and state — bugs manifest only in one profile.
- Local behaviour diverges from production behaviour. What passes locally may fail against Kafka.
- Maintenance burden: every new feature must be tested across all profiles and all backend combinations.
- The "micro" Chronicle Queue approach cannot replicate Kafka's ordering, partition, and consumer group semantics — testing against it gives false confidence.

Docker Compose with lightweight images (total stack ~235 MiB) removes the practical barrier to running the full stack locally.

## Decision

One infrastructure stack for all deployments:

| Component | Role |
|---|---|
| Kafka 4+ (KRaft) | Edge transport — one topic per process edge |
| Valkey | Correlation state — Fork/Join tracking, Wait pending queue |
| TimescaleDB | Audit trail and persistent state |
| VictoriaMetrics | Metrics and observability |

No deployment profiles. No Chronicle Queue. No H2/SQLite. Local development uses Docker Compose with the same images as production. Scaling is via replica count and Kafka partition count, not profile switching.

## Consequences

**Positive:**
- Single code path — behaviour is identical from laptop to Kubernetes.
- Tests run against the real stack; no mock transports.
- Simpler mental model for operators and contributors.
- Redpanda is a supported drop-in for Kafka in environments where a lighter Kafka-compatible broker is preferred.

**Negative:**
- Docker is required for local development. No purely in-process test mode.
- Kafka is a hard dependency — there is no embedded fallback for edge or constrained deployments.

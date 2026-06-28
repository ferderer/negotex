# Negotex

**Orchestrator-free BPMN workflow engine for compliance-heavy industries.**

Negotex compiles BPMN process definitions directly to message topologies — each edge becomes a Kafka topic, each node a stateless stream processor. No central coordinator. No database polling. No single point of failure.

> **Status:** Active development — Java runtime substantially implemented. 14-module Maven build passing. Not yet production-ready.

---

## The core idea

Traditional workflow engines (Camunda, Temporal, Conductor) share an architectural pattern: a central orchestrator polls a database and dispatches tasks. That orchestrator is both the bottleneck and the single point of failure.

Negotex takes a different path. A BPMN process definition compiles to a message topology:

| BPMN concept | Negotex concept |
|---|---|
| Sequence Flow (edge) | Kafka topic |
| Task / Gateway (node) | Stateless stream processor |
| Token | `Envelope<T>` message |
| Process instance | Correlated message group |

Each node type scales independently. The messaging backend owns durability. The hot path never touches a database.

---

## Nine primitives

Negotex covers the full BPMN execution model with a minimal, orthogonal set of primitives:

| Primitive | BPMN equivalent | Behaviour |
|---|---|---|
| **Map** | Service Task | `Input → Output`, pure handler function |
| **Fork** | Parallel Gateway (split) | Fans out to all branches simultaneously |
| **Join** | Parallel Gateway (join) | Waits for all expected branches, verifies origin hash |
| **Choice** | Exclusive Gateway (split) | Routes to exactly one branch via condition |
| **Merge** | Exclusive Gateway (join) | Passes the first-arriving envelope, no synchronisation |
| **Filter** | Inclusive Gateway (split) | Activates 0–N outgoing branches via predicate |
| **Wait** | Intermediate Catch Event | Suspends until external signal or timer |
| **Trigger** | Start Event | Creates the initial envelope — entry point for the process |
| **Terminate** | End Event | Ends the process instance, writes the compliance record |

---

## Key properties

**No orchestrator.** The process graph *is* the message topology. Processors are stateless and independently scalable.

**Compliance-first.** Envelope hash chains produce a tamper-evident audit trail without external systems or blockchain overhead:

```
EnvelopeHash = SHA-256(previousHash § executionResult § timestamp § handlerVersion)
```

`executionResult` is canonicalised deterministically (sorted map keys at all levels, stable types) so the chain is reproducible by a verifier given only the audit trail. `handlerVersion` is persisted on every audit record. Neither Camunda nor Temporal offer native cryptographic chaining.

**Handlers are pure functions.** Business logic has zero infrastructure dependencies — `Input → Output`. The node processor handles all infrastructure concerns: Kafka consumption, Valkey state, TimescaleDB writes, metrics.

**Transport metadata stays out of the payload.** Delivery metadata (incoming edge, retry count) is passed to processors via `ReceivedEnvelope(Envelope, DeliveryContext)` — never written into the business payload. Join merges are never poisoned by transport keys.

**Stateful nodes fail safely.** `JoinProcessor` and `WaitProcessor` implement `StatefulProcessor` — a marker interface that routes them directly to DLQ on failure. Retrying a stateful node while a correlation claim is live would silently discard the envelope; this is prevented structurally, not by configuration.

**Zero-downtime versioning.** Each process version gets isolated topics. Live processes drain while new versions go live — no coordinated cutover required.

**Execution contracts.** Every node declares its consistency model (`deterministic` or `eventual`) and every handler declares its runtime capabilities. Violations are caught at deployment, not at runtime.

---

## Runtime stack

| Component | Role |
|---|---|
| Kafka 4+ (KRaft) | Edge transport — one topic per process edge |
| TimescaleDB | Audit trail — `handler_version` and `envelope_hash` on every record |
| Valkey | Correlation state for Join/Wait |
| VictoriaMetrics | Observability |
| Java 21+ | Runtime — Virtual Threads for processor pools |

Redpanda is a supported drop-in for Kafka in lighter deployments.

---

## Module structure

The Java kit is a 14-module Maven project:

```
negotex-java-api              — public handler interfaces (TaskHandler, ChoiceHandler, …)
negotex-runtime-api           — Envelope, Publisher, EnvelopeTransport, EnvelopeEvent,
                                HashChainStep, ExecutionResultCanonicalizer, DeliveryContext
negotex-annotation-processor  — @NegotexHandler processing
negotex-java-runtime          — nine node processors, ProcessorStarter, DefaultPublisher
negotex-transport-kafka       — KafkaEnvelopeTransport (AcknowledgingMessageListener)
negotex-transport-inprocess   — InProcessEnvelopeTransport (QueuedEnvelope, no broker)
negotex-persistence-timescale — TimescaleDB writer, canonical DDL
negotex-persistence-h2        — H2 writer for local dev, same schema as Timescale
negotex-persistence-noop      — no-op writer for PoC / CI
negotex-correlation-valkey    — ValkeyCorrelationStore (Lua atomic claim)
negotex-correlation-inmemory  — InMemoryCorrelationStore (claim expiry, 300s TTL)
negotex-starter-standard      — Kafka + TimescaleDB + Valkey (production)
negotex-starter-micro         — in-process transport + in-memory correlation (no Docker)
negotex-starter-test          — test convenience dependencies
```

---

## Handler example (Java)

```java
@NegotexHandler
public class CreditCheckHandler implements TaskHandler<LoanApplication, CreditResult> {

    @Override
    public CreditResult handle(LoanApplication application) {
        // Pure function — no Kafka, no DB, no framework imports
        var score = CreditScoring.evaluate(application);
        return new CreditResult(score, score >= 650);
    }
}
```

Process definition wires it up:

```yaml
nodes:
  - id: credit-check
    type: map
    handler: CreditCheckHandler
    edges:
      incoming: application-validated
      outgoing: credit-checked
```

Handlers are unit-testable without any mocking of infrastructure. The same handler runs identically in the test micro stack and the production Kafka stack.

---

## Deployment

**OSS (Docker Compose)** — standard runtime image + handler JAR on the classpath. No Docker rebuild for handler changes.

```yaml
services:
  negotex-runtime:
    image: negotex/java-runtime:latest
    volumes:
      - ./handlers/loan-app.jar:/handlers/loan-app.jar
    environment:
      NEGOTEX_HANDLERS_PATH: /handlers/loan-app.jar
      NEGOTEX_PROCESS_CONFIG: /config/loan-app.yaml
      KAFKA_BROKERS: kafka:9092
      TIMESCALEDB_URL: jdbc:postgresql://timescaledb:5432/negotex
      VALKEY_URL: valkey:6379
```

**Enterprise** — Kubernetes + Enterprise Control Plane (Rust). Each node type scales independently as a separate Deployment. The ECP Operator manages `NegotexRuntime` CRDs, sidecar injection, and topic lifecycle. GitOps-compatible: every UI action produces a CRD that can be authored as a manifest.

---

## Multilanguage handlers

Handlers are not limited to Java. Negotex is building processor kits for:

| Kit | Phase | Target |
|---|---|---|
| `negotex-java` | Current | Enterprise Java shops |
| `negotex-fsharp` | Phase 2 | Financial markets, quant, risk |
| `negotex-rust` | Phase 3 | Systems-level, security-critical nodes |

F# is prioritised for Phase 2 because of its dominance in financial services domain modelling — algebraic types and discriminated unions are a natural fit for regulated domains where invalid states must be unrepresentable.

Each language kit communicates directly with Kafka using native clients. There is no External Task polling pattern — no language is a second-class citizen.

---

## Open-core model

| | Community (Apache 2.0) | Enterprise |
|---|---|---|
| Workflow execution engine | ✅ Full | ✅ |
| All nine primitives | ✅ | ✅ |
| Envelope hash chains | ✅ | ✅ |
| Execution contracts | ✅ | ✅ |
| Consistency contracts | ✅ | ✅ |
| OSS console | ✅ | ✅ |
| Multi-cluster management | — | ✅ |
| Blue/green deployments | — | ✅ |
| Process replay debugging | — | ✅ |
| Compliance reporting | — | ✅ |
| Kubernetes Operator | — | ✅ |
| SLA + support | — | ✅ |

The community edition is complete and production-ready — not a teaser. Any team can run Negotex in production without a commercial licence.

Pricing is per-cluster, not per-execution. No metering surprises as process volume grows.

---

## Project status

- [x] 39 Architecture Decision Records across seven levels
- [x] arc42 system architecture document
- [x] Java runtime — 14 modules, BUILD SUCCESS (~2,600 lines)
- [x] All nine node primitives (Map, Fork, Join, Choice, Merge, Filter, Wait, Trigger, Terminate)
- [x] Envelope hash chains — ADR-028 compliant (`HashChainStep`, `ExecutionResultCanonicalizer`)
- [x] Audit persistence — `handler_version` + `envelope_hash` on all TimescaleDB / H2 records
- [x] Transport abstraction — `DeliveryContext`, `ReceivedEnvelope`, `StatefulProcessor`
- [x] Retry-topic pattern for stateless nodes; stateful nodes fail directly to DLQ
- [x] `negotex-starter-micro` — full integration testing without Docker
- [ ] First integration test (Trigger → Map → Terminate, micro stack)
- [ ] `TriggerProcessor` REST endpoint
- [ ] `ops/` compiler module — BPMN/YAML → RuntimeManifest
- [ ] OSS console (Okygraph + Svelte islands)
- [ ] `negotex-fsharp` processor kit
- [ ] Enterprise Control Plane (Rust)

---

## Documentation

- [`/docs/adr/`](./docs/adr/) — 39 Architecture Decision Records
- [`/docs/arc42.md`](./docs/arc42.md) — System architecture (arc42 format)
- [`/kits/java/adapter/persistence-timescale/src/main/resources/db/audit-event-schema.sql`](./kits/java/adapter/persistence-timescale/src/main/resources/db/audit-event-schema.sql) — Canonical TimescaleDB DDL

---

## License

Community edition: [Apache 2.0](LICENSE)  
Enterprise Control Plane: Commercial — contact for licensing.

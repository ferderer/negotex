# Negotex — System Architecture (arc42)

**Version:** 1.0  
**Status:** Living document — updated as architectural decisions are made  
**ADR index:** [docs/adr/index.md](adr/index.md)

---

## 1. Introduction and Goals

### 1.1 Requirements overview

Negotex is an orchestrator-free, BPMN-compatible workflow execution engine targeting compliance-heavy industries — banking, insurance, healthcare, and government. Where traditional engines deploy a central coordinator that polls a database and dispatches tasks, Negotex compiles a BPMN process definition directly to a Kafka message topology: each edge becomes a topic, each node a stateless stream processor, each BPMN token an `Envelope<T>` message.

**Core functional requirements:**

- Execute BPMN 2.0 process definitions with full control flow coverage (sequential, parallel, conditional, inclusive, suspension, termination)
- Provide a tamper-evident, compliance-grade audit trail for every process step
- Support multilanguage handler implementations (Java, F#, Rust) as first-class node processors
- Enable zero-downtime process version upgrades across long-running instances
- Expose process definitions and execution state through an OSS console

**Out of scope for the community edition:**

- Multi-cluster management
- Blue/green deployments with gradual traffic rollout
- Process Replay Debugging UI
- Enterprise compliance reporting

### 1.2 Quality goals

| Priority | Quality goal | Rationale |
|---|---|---|
| 1 | **Horizontal scalability** | Each node type scales independently via Kafka consumer groups. No central bottleneck. |
| 2 | **Throughput** | DB-free hot path targets 10x+ throughput vs. traditional polling-based engines |
| 3 | **Compliance** | Tamper-evident audit trail, differentiated retention, GDPR erasure, multi-jurisdictional policy |
| 4 | **Handler simplicity** | Business logic is a pure function with zero infrastructure dependencies — testable without a running stack |
| 5 | **BPMN compatibility** | Process definitions map directly to BPMN 2.0 concepts; regulatory environments accepting BPMN definitions are served without translation |

### 1.3 Stakeholders

| Stakeholder | Role | Primary concern |
|---|---|---|
| Process developer | Implements handler functions and authors process definitions | Handler API simplicity, BPMN coverage, local dev experience |
| Platform engineer | Deploys and operates Negotex runtimes | Deployment model, scaling, observability, zero-downtime upgrades |
| Compliance officer | Audits process execution | Audit trail completeness, retention policy, tamper evidence, GDPR erasure |
| Operations | Monitors live processes | Metrics, consumer lag, DLQ, alerting |
| Enterprise buyer | Evaluates adoption | Open-core completeness, upgrade path, pricing predictability |

---

## 2. Constraints

### 2.1 Technical constraints

| Constraint | Detail |
|---|---|
| Java 21+ | Minimum runtime — required for Virtual Threads (Project Loom) |
| Kafka 4+ (KRaft) | Minimum broker version — KRaft mode required; ZooKeeper-based clusters not supported |
| Spring Boot 3+ | Implementation framework for the Java runtime; handler code has no Spring dependency |
| TimescaleDB | Audit trail and persistent state storage |
| Valkey | Correlation state (Fork/Join tracking, Wait pending queue) |
| VictoriaMetrics | Metrics and observability |
| Docker Compose | Required for local development — no embedded/in-process fallback |

Redpanda is a supported drop-in for Kafka in environments that prefer a lighter Kafka-compatible broker.

### 2.2 Organisational constraints

- Community edition must be complete and production-ready — not a crippled teaser
- Enterprise Control Plane is a separate binary (Rust); it must never execute workflow logic
- Apache 2.0 licence governs the community edition; the Enterprise Control Plane is commercially licensed
- Handler interfaces are part of the public API and must be stable across minor versions

### 2.3 Conventions

- Architectural decisions are recorded as ADRs in `docs/adr/`
- Topic naming: `{processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}`
- Handler references: Java qualified class name; F# `Namespace.Module.function`; Rust `crate::module::fn`
- All envelope operations are immutable — handlers never receive a mutable reference

---

## 3. Context and Scope

### 3.1 Business context

```
                    ┌─────────────────────────────────┐
                    │           Negotex               │
                    │                                 │
External triggers ──┤  Trigger node (API/Kafka/Timer) │
                    │                                 │
External services ──┤  Wait node (suspend/resume)     │
                    │                                 │
                    │  [process executes...]          │
                    │                                 │
Completion hooks ◄──┤  Terminate node                 │
                    └───────────────┬─────────────────┘
                                    │
                    ┌───────────────▼─────────────────┐
                    │    OSS Console / Enterprise CP  │
                    │    (monitoring, deployment)     │
                    └─────────────────────────────────┘
```

**External actors:**

- **API callers** — start process instances via the Trigger REST endpoint
- **Kafka producers** — start process instances or resume Wait nodes via message topics
- **Schedulers** — start process instances on timer triggers
- **External services** — resume Wait nodes via the task completion API
- **Process developers** — deploy process definitions and handler artifacts
- **Compliance tools** — query the audit trail in TimescaleDB directly or via the Enterprise Control Plane

### 3.2 Technical context

| Component | Interface | Protocol |
|---|---|---|
| Kafka broker | Topic producer/consumer | Kafka protocol (binary) |
| TimescaleDB | Audit event writer; compliance queries | JDBC / PostgreSQL wire protocol |
| Valkey | Correlation state (Fork/Join counters, Wait queue) | Redis protocol (RESP) |
| VictoriaMetrics | Metrics push | Prometheus remote-write |
| Handler artifact store | JAR / DLL / binary retrieval | S3 / volume mount |
| OSS console | Process definition deployment; read-only monitoring | HTTP/REST |
| Enterprise Control Plane | Runtime management, governance, multi-cluster | HTTP/REST (versioned API) |

---

## 4. Solution Strategy

### 4.1 The core idea

**The process graph is the message topology.**

A BPMN process definition compiles to an executable Kafka topology. The compiler produces one Kafka topic per process edge and one processor instance per node. There is no orchestrator, no central dispatcher, no shared state store on the hot path.

| BPMN concept | Negotex concept |
|---|---|
| Sequence Flow (edge) | Kafka topic |
| Task / Gateway (node) | Stateless stream processor |
| Token | `Envelope<T>` message |
| Process instance | Correlated `Envelope` group sharing a `processInstanceId` |

### 4.2 Key architectural decisions

| Decision | Rationale | ADR |
|---|---|---|
| No central orchestrator | Eliminates bottleneck and SPOF; each node type scales independently | ADR-001 |
| Immutable envelope with map payload | Thread-safe, merge-verifiable, tamper-detectable via `_hash` | ADR-002 |
| Unified infrastructure stack | Single code path; local = production | ADR-003 |
| Async state publishing | DB writes never block the hot path | ADR-004 |
| Handlers as pure functions | Zero infrastructure dependencies; unit-testable without mocking | ADR-005 |
| Nine orthogonal primitives | Complete BPMN coverage from a minimal, learnable set | ADR-006 |
| Topic-per-edge naming | Explicit, inspectable topology; version isolation via topic namespace | ADR-026 |
| Envelope hash chains | Tamper-evident audit trail without blockchain overhead | ADR-028 |
| Execution contracts | Capability declarations validated at deployment, not at runtime | ADR-029 |

---

## 5. Building Block View

### 5.1 Level 1 — System overview

```
┌──────────────────────────────────────────────────────────────────┐
│                          Negotex runtime                         │
│                                                                  │
│  ┌─────────────┐   ┌─────────────────────────────────────────┐  │
│  │   Process   │   │           Node processors               │  │
│  │  compiler   │──▶│  Map │ Fork │ Join │ Choice │ Wait │ …  │  │
│  └─────────────┘   └──────────────────┬──────────────────────┘  │
│                                        │                         │
│                              ┌─────────▼────────┐               │
│                              │    Publisher      │               │
│                              └─────────┬─────────┘               │
└────────────────────────────────────────┼─────────────────────────┘
                                         │
         ┌───────────┬───────────────────┼───────────────┐
         ▼           ▼                   ▼               ▼
      Kafka      TimescaleDB           Valkey     VictoriaMetrics
   (transit)   (audit trail)      (correlation)   (observability)
```

**Process compiler:** Reads a process definition (YAML), validates handler references, creates Kafka topics, generates the Runtime Manifest. Runs at deployment time, not at execution time.

**Node processors:** One processor instance per node. Stateless (except Join, Filter, Wait which use Valkey). Each processor consumes from its incoming Kafka topic(s), calls the handler, and delegates all outbound work to the Publisher.

**Publisher:** Single public method — `publish(envelope, targetNodeIds)`. Internally routes to Kafka, updates Valkey correlation state, and async-writes audit events to TimescaleDB. The publisher is the only component with direct access to all infrastructure clients.

### 5.2 Level 2 — Node processors

Each primitive is a self-contained processor with defined edge structure and state requirements:

| Primitive | Incoming | Outgoing | State | Handler |
|---|---|---|---|---|
| **Trigger** | 0 | 1 | — | Required |
| **Map** | 1 | 1 | — | Required |
| **Fork** | 1 | N | — | None |
| **Join** | N | 1 | Valkey | Optional (merge strategy) |
| **Choice** | 1 | 1 of N | — | Required |
| **Merge** | N | 1 | — | None |
| **Filter** | 1 | 0–N | Valkey | Required |
| **Wait** | 1 | 1 | Valkey | Required |
| **Terminate** | 1 | 0 | — | Optional |

### 5.3 Level 2 — Publisher internals

The Publisher's `publish()` call orchestrates four infrastructure operations:

1. **Kafka write:** Resolve target node IDs to versioned topic names; produce envelope to each topic with `processInstanceId` as partition key.
2. **Valkey update:** If the publishing node is Fork, increment the expected-branch counter at the downstream Join.
3. **Audit event write (async):** Persist `EXITED` event with payload snapshot to TimescaleDB. Fire-and-forget.
4. **Metrics emit:** Increment throughput counter; record processing latency to VictoriaMetrics.

### 5.4 Level 2 — Envelope

```java
record Envelope<T>(
    String envelopeId,           // unique per envelope instance
    String processInstanceId,    // shared across all envelopes in one process instance
    String processDefinitionId,
    String processVersion,
    String previousEnvelopeHash, // hash chain link (ADR-028)
    Instant createdAt,           // process instance start time
    Instant nodeEnteredAt,       // current node entry time (auto-latency measurement)
    Map<String, Object> payload  // immutable — Map.copyOf() on construction
) {}
```

The `_hash` inside `payload` is the base payload hash computed at the Trigger node. It remains unchanged through the process instance and is used by Join to verify merge compatibility.

`previousEnvelopeHash` is the ADR-028 chain hash — distinct from `_hash`. It chains envelope transitions cryptographically.

---

## 6. Runtime View

### 6.1 Happy path — single-branch process

```
External caller
      │
      ▼ HTTP POST /api/processes/loan-app/start
Trigger processor
  1. createPayload(request)         ← TriggerHandler
  2. compute _hash
  3. create Envelope (envelopeId, processInstanceId, ...)
  4. persist STARTED event (async)
  5. publish(envelope, ["validate"])
      │
      ▼ Kafka topic: loan-app-v1.3.0.edge.start-to-validate
Map processor (validate)
  1. persist ENTERED event (async)
  2. extract typed input from payload
  3. validate(input)                ← TaskHandler
  4. enrich payload with result
  5. publish(envelope, ["credit-check"])
      │
      ▼ Kafka topic: loan-app-v1.3.0.edge.validate-to-credit-check
… (continues through graph) …
      │
      ▼ Kafka topic: loan-app-v1.3.0.edge.decision-to-end
Terminate processor
  1. onTerminate(payload)           ← TerminateHandler (optional)
  2. persist END event with full payload (long-retention record)
  3. delete Valkey state for processInstanceId
  4. notify completion listeners
```

### 6.2 Parallel branch — Fork/Join

```
Fork processor
  1. publish(envelope, ["credit-check", "income-check", "fraud-check"])
     └─ all three Kafka topics receive the same envelope simultaneously

[Three Map processors run concurrently, each adding their result to payload]

Join processor (on each arrival)
  1. store envelope in Valkey: key = processInstanceId + joinNodeId
  2. increment arrival counter
  3. if counter < expectedBranches: wait
  4. if counter == expectedBranches:
     a. retrieve all three envelopes from Valkey
     b. verify all share same _hash
     c. merge payloads (attribute-merge strategy)
     d. publish merged envelope downstream
     e. delete Valkey state for this join
```

### 6.3 Suspension — Wait node

```
Wait processor (suspension)
  1. assign taskId = UUID
  2. store {taskId → envelope} in Valkey
  3. onSuspend(taskId, context)     ← WaitHandler (e.g. send approval email)
  4. [processor stops — envelope is parked in Valkey]

[Human or external system completes the task]

POST /api/tasks/{taskId}/complete  { "outcome": "approved" }
  │
Wait processor (resumption, via Valkey poll)
  1. retrieve envelope from Valkey by taskId
  2. enrichedPayload = onResume(taskId, completionData)  ← WaitHandler
  3. publish(enrichedEnvelope, ["next-node"])
  4. delete taskId from Valkey
```

### 6.4 Zero-downtime version upgrade

```
Deploy v1.3.0 alongside v1.2.0:
  1. Process compiler creates new topics: loan-app-v1.3.0.edge.*
  2. New v1.3.0 processors start, subscribe to new topics
  3. Trigger node for v1.2.0 is paused — no new instances start on v1.2.0
  4. In-flight v1.2.0 instances continue on v1.2.0 topics until complete
  5. Monitor: instanceRepo.countActive("loan-app", "1.2.0")
  6. When count == 0: remove v1.2.0 processors, delete v1.2.0 topics
```

---

## 7. Deployment View

### 7.1 Community edition (Docker Compose)

The standard runtime image accepts handler artifacts at startup via classpath injection, volume mount, or S3 retrieval. No Docker rebuild is required for handler changes.

```yaml
services:
  negotex-runtime:
    image: negotex/java-runtime:latest
    environment:
      NEGOTEX_HANDLERS_PATH: /handlers/loan-app.jar
      NEGOTEX_PROCESS_CONFIG: /config/loan-app.yaml
      KAFKA_BROKERS: kafka:9092
      TIMESCALEDB_URL: jdbc:postgresql://timescaledb:5432/negotex
      VALKEY_URL: valkey:6379
    volumes:
      - ./handlers/loan-app.jar:/handlers/loan-app.jar
      - ./config/loan-app.yaml:/config/loan-app.yaml

  kafka:
    image: apache/kafka:4.0.0

  timescaledb:
    image: timescale/timescaledb:latest-pg16

  valkey:
    image: valkey/valkey:latest

  victoriametrics:
    image: victoriametrics/victoria-metrics:latest

  oss-console:
    image: negotex/oss-console:latest
    ports:
      - "8080:8080"
```

### 7.2 Enterprise edition (Kubernetes)

In Kubernetes, each node type runs as an independent Deployment, scaling via HPA based on Kafka consumer lag (reported to VictoriaMetrics). The Enterprise Control Plane manages deployments, monitors multi-cluster topology, and enforces governance policies.

```
┌─────────────────────────── Kubernetes cluster ─────────────────────────────┐
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐          │
│  │  map-processor   │  │  join-processor  │  │  wait-processor  │          │
│  │  Deployment      │  │  Deployment      │  │  Deployment      │          │
│  │  replicas: 4     │  │  replicas: 2     │  │  replicas: 3     │          │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │               Enterprise Control Plane (Rust)                        │   │
│  │  Multi-cluster · Blue/Green · Compliance reporting · Governance      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Kafka (KRaft) │ TimescaleDB │ Valkey │ VictoriaMetrics                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Scaling model:** Kafka consumer lag per node topic drives HPA. A Map processor pool with 4 replicas distributes partition assignments; adding replicas absorbs lag without any configuration change to the process definition.

### 7.3 Multilanguage runtimes

A process may distribute nodes across multiple runtimes in different languages:

```yaml
runtimes:
  order-core:
    language: java
    base: negotex/java-runtime:1.0
    handlers: s3://acme/order-handlers-1.0.jar
    replicas: 2
  risk-engine:
    language: fsharp
    image: acme/risk-engine-fsharp:1.0
    replicas: 4

nodes:
  - id: validate-order
    type: map
    runtime: order-core
    handler: com.acme.order.ValidateOrderHandler
  - id: calculate-risk
    type: map
    runtime: risk-engine
    handler: Acme.Risk.RiskCalculation.evaluate
```

Each runtime communicates via the shared Kafka topology. Language choice is a node-level decision — it does not affect any other node in the process.

---

## 8. Cross-Cutting Concepts

### 8.1 Audit trail and compliance

Every node transition writes an `EnvelopeEvent` (ENTERED, EXITED, FAILED, END) to TimescaleDB asynchronously. The `END` event from the Terminate node carries the complete final payload and is the primary record for compliance queries.

**Content-addressable deduplication:** Payload attributes ≥ 256 bytes are deduplicated via SHA-256 hash. Each `envelope_events` row stores small attributes inline and large attributes as hash references to `payload_attributes`. A 10-node process that carries a 1 KB base payload through all nodes stores one copy of that payload, not ten.

**Retention classification:** Every event record carries compact bitfield metadata:
- `classification_flags` (32-bit): data category — `ACCOUNTING`, `AML`, `PII`, `FINANCIAL`, `AUDIT_LOG`, etc.
- `security_flags` (16-bit): confidentiality level
- `anchor_flags` (16-bit): when retention duration starts (event time, contract end, fiscal year end, etc.)
- `lifecycle_flags` (16-bit): current state — `ACTIVE`, `LEGAL_HOLD`, `PENDING_DELETION`, `REDACTED`
- `jurisdictions` (text array): multi-jurisdiction support

A `RetentionPolicyProvider` SPI resolves flag combinations to concrete retention periods per jurisdiction. Retention decisions are logged to `retention_decisions` for audit purposes.

**GDPR erasure:** PII attributes in `payload_attributes` are redacted by setting `REDACTED` in `lifecycle_flags` and replacing `attr_value` with a tombstone. The audit trail structure (hashes, timestamps, event shape) is preserved.

### 8.2 Envelope hash chains

Each envelope carries a `previousEnvelopeHash`:

```
EnvelopeHash = SHA-256(
    previousEnvelopeHash
    || executionResult
    || timestamp
    || handlerVersion
)
```

The Audit Verifier consumer reads a process instance's event chain from TimescaleDB and recomputes each hash. Any tampering — deletion, reordering, or modification of records — breaks the chain at the tampered point. Verification runs on-demand or as a continuous background consumer.

### 8.3 Execution contracts

Every node definition may carry a governance policy:

```json
{
  "maxExecutionTime": "2s",
  "maxMemoryUsage": "64MB",
  "allowedExternalCalls": ["payment-gateway"],
  "retryPolicy": "idempotent"
}
```

Every handler declares its required runtime capabilities:

```json
{
  "networkAccess": true,
  "allowedOutboundServices": ["payment-gateway"],
  "memoryLimit": "64MB"
}
```

At deployment, the process compiler verifies that every handler's declared capabilities are a subset of the permissions in its node's governance policy. Deployment fails if any handler exceeds its policy.

### 8.4 Consistency contracts

Each node declares its consistency model:

- **`deterministic`:** Pure function — same input always produces same output. No network calls, no time-dependent behaviour. The node processor may enforce this by restricting the handler's execution environment.
- **`eventual`:** Side effects are permitted. Idempotency is the handler author's responsibility.

**Audit Certification Mode** (deployment flag): All nodes must declare `deterministic`. Any `eventual` node blocks deployment. In this mode, every process instance is fully reproducible from the TimescaleDB audit trail.

### 8.5 Error handling

| Scenario | Handling |
|---|---|
| Handler throws exception | Processor catches, persists `FAILED` event, retries per retry policy |
| Retry budget exhausted | Envelope routed to Dead Letter Queue (DLQ) topic |
| Join branch permanently lost | Join never fires; process-level timeout triggers cleanup |
| Condition evaluation fails | Envelope routed to DLQ |
| Publisher write fails (Kafka) | Publisher retries with exponential backoff; envelope held in memory |
| Audit write fails (TimescaleDB) | Async write retried independently; does not affect envelope routing |

### 8.6 Observability

**Metrics (VictoriaMetrics):**
- Envelope throughput per node (envelopes/sec)
- Processing latency per node (p50, p95, p99) — derived from `nodeEnteredAt` on each envelope
- Kafka consumer lag per topic — primary scaling signal
- Valkey key count (Join/Wait pending state size)
- DLQ depth per process

**Audit queries (TimescaleDB):**
- Process instance history: all events for a `processInstanceId`, ordered by timestamp
- Node performance: average `duration_ms` per `node_id` across instances
- Failed instances: all instances with at least one `FAILED` event
- Retention sweep: events with `retention_until < NOW()` eligible for deletion

**OSS console:** Read-only monitoring of running instances, process topology view, live consumer lag per node. Direct process definition deployment (no Enterprise Control Plane required).

---

## 9. Architecture Decisions

All architecture decisions are recorded as individual ADRs. See [`docs/adr/index.md`](adr/index.md) for the complete index organised by level.

**Summary by level:**

| Level | ADRs | Focus |
|---|---|---|
| 1 — Fundamental | ADR-001 to ADR-004 | Orchestrator-free design, immutable envelope, unified stack, async publishing |
| 2 — Node processing | ADR-005 to ADR-017 | Handler model, nine primitives, conditions, plugin architecture |
| 3 — Multilanguage | ADR-018 to ADR-023 | Language kits, runtime assignment, packaging, zero-downtime versioning |
| 4 — Infrastructure | ADR-024 to ADR-027 | Java/Kafka baseline, Publisher interface, topic naming, audit storage |
| 5 — Compliance | ADR-028 to ADR-030 | Hash chains, execution contracts, consistency contracts |
| 6 — Business | ADR-031 to ADR-033 | Open-core model, console vs CP, pricing |
| 7 — Tooling | ADR-034 to ADR-036 | Project generator, distribution, console UI architecture |

---

## 10. Quality Requirements

### 10.1 Quality scenarios

| Quality goal | Scenario | Measure |
|---|---|---|
| Horizontal scalability | Consumer lag on a Map node rises above threshold | HPA adds replicas; lag returns to normal within 60 seconds |
| Throughput | 1 million process instances are submitted concurrently | Hot path processes envelopes without DB writes blocking; p99 latency < 100ms per node |
| Compliance | Regulator requests full audit trail for a process instance | Complete event history reconstructed from TimescaleDB in < 1 second per instance |
| Compliance | Regulator requests hash chain verification | Audit Verifier processes full chain for a 10-node instance; result returned in < 5 seconds |
| Handler simplicity | A developer new to Negotex implements their first handler | Handler implemented as a plain Java class with no imported Negotex types; unit-tested without any infrastructure |
| Zero-downtime versioning | A new process version is deployed while 10,000 instances are in-flight on v1.2.0 | All v1.2.0 instances complete on v1.2.0 topics; new instances start on v1.3.0 topics; no envelope is lost or misrouted |

### 10.2 Fitness functions

- **Handler isolation:** Any handler that imports a Negotex infrastructure class fails the CI build.
- **Hot path DB access:** Any processor that calls TimescaleDB synchronously in its `process()` method fails the architecture fitness test.
- **Topic naming compliance:** Any topic name that does not match `{processId}-v{version}.edge.{source}-to-{target}` is rejected by the process compiler.

---

## 11. Risks and Technical Debt

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Kafka becomes a single point of failure | Low (Kafka is replicated) | High | Multi-broker cluster with RF ≥ 3; monitoring on under-replicated partitions |
| Async audit lag causes compliance gap | Medium | High | Audit consumer lag alert; SLA on audit write delay; hash chain verifier detects gaps |
| Join never fires (lost branch) | Medium | Medium | Process-level timeout; DLQ monitoring; compensating transaction handler |
| Valkey data loss (Wait/Join state) | Low (Valkey AOF) | High | Valkey AOF persistence enabled; periodic RDB snapshots; Valkey Sentinel / Cluster for HA |
| Handler version mismatch in hash chain | Low | Medium | `handlerVersion` tracked per node in Runtime Manifest; version is immutable once deployed |
| Large payload memory pressure at Fork | Medium | Medium | Payload size limit enforced by Publisher; large binary data stored by reference (not inline) |

**Technical debt:**

- Consistency contract enforcement (ADR-030) is declaration-only in PoC phase; runtime enforcement of `deterministic` constraints (network blocking, etc.) requires additional work per language.
- `gradual-rollout` migration strategy (percentage-based traffic split) is planned for the Enterprise Control Plane; only `drain-and-switch` is implemented in PoC phase.

---

## 12. Glossary

### BPMN terms

| Term | Definition |
|---|---|
| **Token** | BPMN concept for the execution unit flowing through the process graph. Implemented in Negotex as `Envelope<T>`. |
| **Sequence Flow** | BPMN directed connection between nodes. Implemented as a Kafka topic. |
| **Process instance** | One execution of a process definition. Identified by `processInstanceId`. |
| **Process definition** | Blueprint for a workflow: nodes, edges, handler references, governance policies. |
| **Service Task** | BPMN node that executes business logic. Implemented as the Map primitive. |
| **Parallel Gateway** | Split (Fork): activates all outgoing branches. Join: waits for all incoming branches. |
| **Exclusive Gateway** | Split (Choice): activates exactly one outgoing branch. Join (Merge): first-arrival pass-through. |
| **Inclusive Gateway** | Split (Filter): activates 0–N outgoing branches. Join: waits for the branches that were activated. |
| **End Event** | Terminate node — ends the process instance. |
| **Start Event** | Trigger node — creates the initial envelope. |

### Negotex terms

| Term | Definition |
|---|---|
| **Envelope\<T\>** | Immutable execution unit. Carries `processInstanceId`, `payload` (Map), `_hash`, hash chain link, and timing metadata. |
| **Node processor** | Infrastructure wrapper per node type. Consumes from Kafka, invokes the handler, delegates outbound work to the Publisher. |
| **Handler** | Pure function implementing business logic. Has no access to Kafka, Valkey, or any infrastructure client. |
| **Publisher** | Internal component with a single public method. Orchestrates Kafka writes, Valkey updates, and async audit event persistence. |
| **Topology** | The compiled, executable form of a process definition — the set of Kafka topics and processor instances it produces. |
| **Runtime Manifest** | Generated document per runtime instance. Contains process definition, infrastructure endpoints, topic names, handler artifact locations, and secret references. |
| **`_hash`** | SHA-256 of the initial payload, computed at the Trigger node. Persists unchanged through the process instance. Used by Join to verify that merging envelopes share the same origin. |
| **Envelope hash chain** | Cryptographic chain across all envelope transitions: `Hash(previousHash \|\| result \|\| timestamp \|\| handlerVersion)`. Enables tamper detection over the audit trail. |
| **Execution contract** | Combination of a node's governance policy and a handler's capability declaration. Validated at deployment time. |
| **Consistency contract** | Per-node declaration: `deterministic` (pure function, reproducible) or `eventual` (side effects permitted). |
| **Audit Certification Mode** | Deployment flag requiring all nodes to declare `deterministic`. Enables full process replay verification. |
| **DLQ (Dead Letter Queue)** | Kafka topic receiving envelopes that exhausted their retry budget. Requires manual intervention. |
| **Drain-and-switch** | Zero-downtime deployment strategy: new version starts on new topics; old version drains until all in-flight instances complete. |
| **Processor kit** | Language-specific SDK implementing the Negotex processor protocol (Kafka, Valkey, TimescaleDB, VictoriaMetrics clients + handler invocation). Available for Java, F# (Phase 2), Rust (Phase 3). |

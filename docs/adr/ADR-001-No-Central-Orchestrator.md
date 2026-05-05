# ADR-001 — No central orchestrator

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 1 — Fundamental architecture |

## Context

Traditional workflow engines (Camunda, Temporal, Conductor) share a common pattern: a central orchestrator polls a database and dispatches tasks to workers. This creates two structural problems:

- **Bottleneck:** All process state flows through one component. Throughput is bounded by the orchestrator, not by the workload.
- **Single point of failure:** If the orchestrator is unavailable, no process can make progress, regardless of whether the individual processors are healthy.

Horizontal scaling is only possible behind the orchestrator — the orchestrator itself cannot be distributed without significant coordination overhead.

## Decision

The process graph is mapped directly to a message topology. There is no orchestrator.

- Each BPMN edge becomes a Kafka topic.
- Each node type becomes a stateless stream processor that consumes from its incoming topic(s) and publishes to its outgoing topic(s).
- Each BPMN token becomes an `Envelope<T>` message.
- Kafka owns durability and delivery guarantees. No component owns the process.

A process definition compiles to a topology. Deploying a new version means deploying new topics and new processor instances — not updating a central runtime.

## Consequences

**Positive:**
- No bottleneck. Each node type scales independently via Kafka consumer group parallelism.
- No SPOF. Any processor can fail and restart without blocking other nodes.
- Throughput is limited by Kafka partition count and processor count, not by a shared coordinator.
- Stateless processors are trivial to scale horizontally.

**Negative:**
- Debugging is harder. There is no single place that holds the full state of a running process instance; it must be reconstructed from audit events in TimescaleDB.
- Eventual consistency on process state queries — the audit trail may lag behind the current execution position.

## Alternatives considered

**Central orchestrator (rejected):** Familiar pattern, easier debugging. Rejected because it creates the bottleneck and SPOF that Negotex explicitly aims to eliminate.

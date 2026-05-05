# ADR-024 — Java 21+ and Kafka 4 (KRaft) baseline

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 4 — Infrastructure |

## Decision

**Java 21+ is the minimum runtime version.** Node processors handle high-concurrency envelope streams. Classic thread pools scale poorly under I/O-bound workloads. Java 21 Virtual Threads (Project Loom) enable millions of concurrent envelopes without thread pool tuning — each envelope can be processed on its own virtual thread with blocking I/O at negligible overhead.

**Kafka 4+ with KRaft mode is the minimum broker version.** Kafka before version 4 required ZooKeeper for cluster coordination — an additional system to operate, monitor, and secure. Kafka 4 KRaft mode removes ZooKeeper entirely. Controller failover is faster, deployment is simpler, and there is one fewer operational dependency.

ZooKeeper-based Kafka deployments are not supported.

## Consequences

**Positive:**
- Virtual Threads eliminate thread pool sizing as a performance tuning concern.
- KRaft simplifies Kafka deployment and reduces operational surface.

**Negative:**
- Java 21 is a hard requirement. Libraries or frameworks that require older JVM versions cannot be used.
- Kafka clusters on versions prior to 4 must be upgraded before adopting Negotex.

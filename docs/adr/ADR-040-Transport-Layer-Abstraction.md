# ADR-040 — Transport layer abstraction

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-001 (no central orchestrator), ADR-003 (unified infrastructure stack), ADR-026 (Kafka topic configuration) |

## Context

The initial architecture mandated Kafka as the sole envelope transport (ADR-003). This was the right call for eliminating deployment profiles — but it creates implicit vendor lock-in at the transport level. Organisations that cannot operate Kafka (regulatory constraints, existing Pub/Sub infrastructure, or Oracle Service Bus mandates) are excluded.

Additionally, a lightweight in-process transport would allow Negotex to run as an embedded library within a single JVM — useful for smaller deployments that do not need horizontal scaling across machines.

The core insight: the Runtime knows only three things about transport:
- Send an envelope to a named topic
- Subscribe a listener to a named topic
- Acknowledge receipt after successful processing

None of these require Kafka specifically.

## Decision

Introduce `EnvelopeTransport` as an interface in `negotex-runtime-api`. Concrete implementations ship as separate Maven modules. The Runtime has no compile-time dependency on any transport implementation.

### Interface

```java
public interface EnvelopeTransport {
    void send(Envelope envelope, String topic);
    Subscription subscribe(String topic, String consumerGroup, EnvelopeListener listener);
}
```

### Factory pattern

Each transport module provides exactly one `EnvelopeTransportFactory` implementation as a Spring `@Service`:

```java
// in negotex-transport-kafka
@Service
public class KafkaEnvelopeTransportFactory implements EnvelopeTransportFactory {
    public EnvelopeTransport create(InfrastructureManifest infrastructure,
                                    EnvelopeSerialiser serialiser) {
        return new KafkaEnvelopeTransport(infrastructure.kafkaBrokers(), serialiser);
    }
}
```

The Runtime injects `EnvelopeTransportFactory` and calls `create()` at startup. Only one transport module may be active — Spring throws `NoUniqueBeanDefinitionException` if multiple factories are present, forcing an explicit choice.

### Module structure

| Module | Description |
|---|---|
| `negotex-transport-kafka` | Kafka 4+ (KRaft), partitioned by `processInstanceId`, idempotent producer |
| `negotex-transport-inprocess` | In-process blocking queues, single JVM, no broker required |

### Starter modules

Profile selection via a single dependency:

```xml
<!-- Standard deployment -->
<dependency>
    <groupId>dev.negotex</groupId>
    <artifactId>negotex-starter-standard</artifactId>
</dependency>

<!-- Micro / embedded deployment -->
<dependency>
    <groupId>dev.negotex</groupId>
    <artifactId>negotex-starter-micro</artifactId>
</dependency>
```

`negotex-starter-standard` pulls in `negotex-transport-kafka` + `negotex-persistence-timescale`.
`negotex-starter-micro` pulls in `negotex-transport-inprocess` + `negotex-persistence-noop`.

### Kafka headers

The Kafka transport writes two headers per message:

| Header | Type | Purpose |
|---|---|---|
| `x-negotex-retry-count` | int (4 bytes) | Current retry attempt — read by the processor |
| `x-negotex-trace-id` | UTF-8 string | Envelope ID for distributed tracing |

## Consequences

**Positive:**
- Infrastructure-agnostic by design. Swap Kafka for Google Pub/Sub, Oracle Service Bus, or any other messaging system by implementing `EnvelopeTransportFactory` — no process definition changes, no migration scripts.
- In-process transport enables embedded use cases and zero-infrastructure testing.
- Starter modules make profile selection a one-line dependency change.
- The process graph topology (ADR-026 topic naming) is unchanged — topic names remain the canonical edge identifiers regardless of the underlying transport.

**Negative:**
- The in-process transport does not support horizontal scaling across JVMs — it is intentionally single-node.
- Consumer group semantics (Kafka partition rebalancing) have no equivalent in the in-process transport. Scaling in micro mode is vertical only (Virtual Threads).
- Third-party transport implementations must implement the full `EnvelopeTransport` contract including acknowledgement semantics — incorrect ack behaviour will cause message loss or redelivery loops.

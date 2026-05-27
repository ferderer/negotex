package dev.negotex.transport;

import dev.negotex.envelope.Envelope;

/**
 * Transport abstraction for envelope routing between node processors.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code KafkaEnvelopeTransport} — distributed, partitioned by
 *       {@code processInstanceId}. Standard deployment.</li>
 *   <li>{@code InProcessEnvelopeTransport} — single JVM, no broker.
 *       Micro mode and integration tests.</li>
 * </ul>
 */
public interface EnvelopeTransport {

    /**
     * Send an envelope to the named topic (normal downstream routing).
     * Retry count in the outgoing header is always 0.
     */
    void send(Envelope envelope, String topic);

    /**
     * Re-queue an envelope to the same topic with an incremented retry count.
     * Used by stateless-node failure handling only — never for stateful nodes.
     *
     * @param retryCount the retry attempt number to write into the header
     */
    void sendRetry(Envelope envelope, String topic, int retryCount);

    /**
     * Subscribe a listener to receive envelopes from the named topic.
     * The transport passes a {@link ReceivedEnvelope} carrying both the
     * business envelope and the delivery metadata — no metadata is written
     * into the envelope payload.
     */
    Subscription subscribe(String topic, String consumerGroup, EnvelopeListener listener);

    /**
     * Acknowledge the current envelope delivery.
     * Kafka: commits the consumer offset (ThreadLocal ack set in subscribe).
     * In-process: no-op.
     */
    void commit();
}

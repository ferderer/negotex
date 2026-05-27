package dev.negotex.transport;

import dev.negotex.envelope.Envelope;
import dev.negotex.error.NegotexException;
import dev.negotex.error.NegotexRuntimeError;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka implementation of {@link EnvelopeTransport}.
 *
 * <p>On subscribe: uses {@link AcknowledgingMessageListener} with
 * {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE}.

 * so the processor can commit via {@code publisher.commit()}.
 *
 * Join processors can identify which branch arrived.
 *
 * <p>Retry count is read from the {@code x-negotex-retry-count} header
 */
public class KafkaEnvelopeTransport implements EnvelopeTransport {

    private static final Logger log = LoggerFactory.getLogger(KafkaEnvelopeTransport.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    public static final String HEADER_RETRY_COUNT = "x-negotex-retry-count";
    public static final String HEADER_TRACE_ID    = "x-negotex-trace-id";

    /** ThreadLocal ack — set per-delivery in subscribe(), consumed by commit(). */
    private static final ThreadLocal<Runnable> PENDING_ACK = new ThreadLocal<>();

    /** Internal payload key for the incoming edge ID — reserved namespace. */
    /** Internal payload key for the full incoming topic name — used for retry re-queuing. */
    /** Internal payload key for the retry count — reserved namespace. */

    private final String brokers;
    private final EnvelopeSerializer serialiser;
    private final KafkaProducer<String, byte[]> producer;

    public KafkaEnvelopeTransport(String brokers,
                                  EnvelopeSerializer serialiser) {
        this.brokers    = brokers;
        this.serialiser = serialiser;
        this.producer   = buildProducer(brokers);
    }

    @Override
    public void send(Envelope envelope, String topic) {
        doSend(envelope, topic, 0);
    }

    /** Re-queue for retry with an explicit retry count written into the header. */
    public void sendRetry(Envelope envelope, String topic, int retryCount) {
        doSend(envelope, topic, retryCount);
    }

    private void doSend(Envelope envelope, String topic, int retryCount) {
        byte[] payload = serialiser.serialise(envelope);
        var record = new ProducerRecord<>(topic, envelope.processInstanceId(), payload);

        record.headers()
                .add(HEADER_RETRY_COUNT,
                        ByteBuffer.allocate(4).putInt(retryCount).array())
                .add(HEADER_TRACE_ID,
                        envelope.envelopeId().getBytes(StandardCharsets.UTF_8));

        try {
            producer.send(record).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            log.debug("Sent envelope {} to topic {}", envelope.envelopeId(), topic);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new NegotexException(NegotexRuntimeError.KAFKA_CONNECTION_FAILED, e)
                    .with("topic", topic)
                    .with("envelopeId", envelope.envelopeId())
                    .with("cause", e.getMessage());
        }
    }

    @Override
    public Subscription subscribe(String topic, String consumerGroup,
                                  EnvelopeListener listener) {
        var containerProps = new ContainerProperties(topic);
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        containerProps.setMessageListener(
                (AcknowledgingMessageListener<String, byte[]>) (record, acknowledgment) -> {
                    Envelope envelope = serialiser.deserialise(record.value());
                    int retryCount = readRetryCount(
                            record.headers().lastHeader(HEADER_RETRY_COUNT));

                    // Build delivery context — stays out of the business payload
                    DeliveryContext delivery = new DeliveryContext(
                            edgeIdFromTopic(topic), topic, retryCount);

                    // Register the ack for this delivery — consumed by commit()
                    PENDING_ACK.set(acknowledgment != null
                            ? acknowledgment::acknowledge
                            : () -> {});

                    listener.onEnvelope(new ReceivedEnvelope(envelope, delivery));
                });

        var consumerFactory = new DefaultKafkaConsumerFactory<String, byte[]>(
                consumerConfig(consumerGroup));
        var container = new ConcurrentMessageListenerContainer<>(
                consumerFactory, containerProps);
        container.start();

        log.info("Subscribed to topic '{}' with group '{}'", topic, consumerGroup);
        return () -> {
            container.stop();
            log.info("Cancelled subscription to topic '{}'", topic);
        };
    }

    @Override
    public void commit() {
        Runnable ack = PENDING_ACK.get();
        if (ack != null) {
            ack.run();
            PENDING_ACK.remove();
        } else {
            log.warn("commit() called but no pending ack registered on this thread");
        }
    }

    @PreDestroy
    public void close() {
        log.info("Closing Kafka producer");
        producer.close(Duration.ofSeconds(30));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Derive a stable edge ID from the topic name.
     * Topics follow: {processId}-v{version}.edge.{edgeId}
     * We take everything after the last ".edge." separator.
     */
    private String edgeIdFromTopic(String topic) {
        int idx = topic.lastIndexOf(".edge.");
        return idx >= 0 ? topic.substring(idx + 6) : topic;
    }

    private int readRetryCount(org.apache.kafka.common.header.Header header) {
        if (header == null || header.value() == null || header.value().length < 4) return 0;
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private KafkaProducer<String, byte[]> buildProducer(String brokers) {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      brokers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName(),
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,     true,
                ProducerConfig.ACKS_CONFIG,                   "all",
                ProducerConfig.RETRIES_CONFIG,                Integer.MAX_VALUE,
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG,       100));
    }

    private Map<String, Object> consumerConfig(String consumerGroup) {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        brokers,
                ConsumerConfig.GROUP_ID_CONFIG,                 consumerGroup,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,        "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,       false);
    }
}

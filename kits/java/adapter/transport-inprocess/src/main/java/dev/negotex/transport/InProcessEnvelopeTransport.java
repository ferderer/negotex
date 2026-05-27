package dev.negotex.transport;

import dev.negotex.envelope.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process {@link EnvelopeTransport} — single JVM, no broker, no serialisation.
 *
 * <p>Retry count survives re-queuing via the {@link QueuedEnvelope} wrapper —
 * no payload mutation, no header concept needed.
 *
 * <p>{@link #commit()} is a no-op — there is no offset to acknowledge in-process.
 */
public class InProcessEnvelopeTransport implements EnvelopeTransport {

    private static final Logger log = LoggerFactory.getLogger(InProcessEnvelopeTransport.class);

    /** Carrier that keeps the retry count alongside the envelope without touching its payload. */
    private record QueuedEnvelope(Envelope envelope, int retryCount) {}

    private final Map<String, BlockingQueue<QueuedEnvelope>> queues = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void send(Envelope envelope, String topic) {
        enqueue(envelope, topic, 0);
    }

    @Override
    public void sendRetry(Envelope envelope, String topic, int retryCount) {
        enqueue(envelope, topic, retryCount);
    }

    private void enqueue(Envelope envelope, String topic, int retryCount) {
        queues.computeIfAbsent(topic, t -> new LinkedBlockingQueue<>())
              .add(new QueuedEnvelope(envelope, retryCount));
        log.debug("Queued envelope {} to topic {} (attempt {})",
                envelope.envelopeId(), topic, retryCount + 1);
    }

    @Override
    public Subscription subscribe(String topic, String consumerGroup,
                                  EnvelopeListener listener) {
        BlockingQueue<QueuedEnvelope> queue =
                queues.computeIfAbsent(topic, t -> new LinkedBlockingQueue<>());
        AtomicBoolean active = new AtomicBoolean(true);

        executor.submit(() -> {
            log.info("In-process listener started for topic '{}'", topic);
            while (active.get()) {
                try {
                    QueuedEnvelope queued = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (queued != null) {
                        DeliveryContext delivery = new DeliveryContext(
                                edgeIdFromTopic(topic), topic, queued.retryCount());
                        listener.onEnvelope(new ReceivedEnvelope(queued.envelope(), delivery));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Error processing envelope on topic '{}': {}",
                            topic, e.getMessage(), e);
                }
            }
            log.info("In-process listener stopped for topic '{}'", topic);
        });

        return () -> {
            active.set(false);
            log.info("Cancelled in-process subscription for topic '{}'", topic);
        };
    }

    @Override
    public void commit() {
        // No-op — no offset to acknowledge in-process
    }

    private String edgeIdFromTopic(String topic) {
        int idx = topic.lastIndexOf(".edge.");
        return idx >= 0 ? topic.substring(idx + 6) : topic;
    }
}

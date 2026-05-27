package dev.negotex.publisher;

import dev.negotex.persistence.EnvelopeEvent;

/**
 * The single outbound interface available to node processors.
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li>{@link #publish} — sends the envelope downstream and persists the event</li>
 *   <li>{@link #commit} — delegates to {@link dev.negotex.transport.EnvelopeTransport#commit()}</li>
 * </ul>
 *
 * <p>The transport owns its own ack lifecycle (ThreadLocal in Kafka, no-op
 * in-process). Processors never interact with the transport directly.
 *
 * <p>Correct processor sequence:
 * <pre>
 *   publish(completionEvent);   // Kafka send + persist
 *   correlationStore.complete() // state cleanup (Join/Wait)
 *   commit();                   // offset commit
 * </pre>
 */
public interface Publisher {


    /**
     * Publish an envelope processing event.
     * For {@link dev.negotex.persistence.EnvelopeEvent.Completion}: sends
     * the envelope to all target topics, then persists asynchronously.
     * For {@link dev.negotex.persistence.EnvelopeEvent.Failure},
     * {@link dev.negotex.persistence.EnvelopeEvent.Termination},
     * {@link dev.negotex.persistence.EnvelopeEvent.Suspension}: persists only.
     *
     * @param event the event to publish
     */
    void publish(EnvelopeEvent event);

    /**
     * Acknowledge the current delivery to the transport.
     * Must be called after all processing and state cleanup is complete.
     * Kafka: commits the consumer offset.
     * In-process: no-op.
     */
    void commit();
}

package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.error.HandlerError;
import dev.negotex.error.NegotexHandlerException;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;
import dev.negotex.transport.EnvelopeListener;
import dev.negotex.transport.ReceivedEnvelope;
import dev.negotex.transport.StatefulProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base for all nine Negotex node processors.
 *
 * <p>Implements {@link EnvelopeListener} — registered with
 * {@link dev.negotex.transport.EnvelopeTransport} at startup by
 * {@link ProcessorStarter}.
 *
 * <p>Transport metadata ({@link DeliveryContext}) is unpacked here and
 * passed to {@link #process}. It is never written into the business payload.
 *
 * <p>Correct sequence in {@link #process}:
 * <pre>
 *   publish(event);              // Kafka send + persist
 *   correlationStore.complete()  // state cleanup if applicable
 *   commit();                    // offset commit
 * </pre>
 *
 * <p>Retry eligibility:
 * <ul>
 *   <li>Stateless nodes (Map, Choice, Filter, Fork, Merge, Trigger,
 *       Terminate) — retried via {@code sendRetry} to the same topic.</li>
 *   <li>Stateful nodes ({@link StatefulProcessor}: Join, Wait) — routed
 *       directly to DLQ on failure; recovery is via claim-timeout expiry
 *       or operator-triggered replay.</li>
 * </ul>
 */
public abstract class NodeProcessor implements EnvelopeListener {

    private static final Logger log = LoggerFactory.getLogger(NodeProcessor.class);

    protected final ProcessorContext ctx;

    protected NodeProcessor(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    // ── EnvelopeListener ──────────────────────────────────────────────────────

    @Override
    public final void onEnvelope(ReceivedEnvelope received) {
        Envelope envelope = received.envelope();
        DeliveryContext delivery = received.delivery();
        long startedAt = System.currentTimeMillis();

        log.debug("Processing envelope {} at node {} (attempt {})",
                envelope.envelopeId(), ctx.nodeId(), delivery.retryCount() + 1);

        try {
            process(envelope, delivery, startedAt);
        } catch (NegotexHandlerException e) {
            handleFailure(envelope, delivery, e);
        } catch (Exception e) {
            handleFailure(envelope, delivery,
                    new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE, e));
        }
    }

    // ── Template method ───────────────────────────────────────────────────────

    /**
     * Process the incoming envelope.
     *
     * <p>Subtypes must call {@link #publish(EnvelopeEvent)} and
     * {@link #commit()} in the correct order.
     *
     * <p>For partial Join arrivals (not all branches yet): call only
     * {@link #commit()} — no publish, no forward.
     *
     * @param envelope  the business envelope — payload is clean, no transport keys
     * @param delivery  transport metadata for this delivery (edge, topic, retryCount)
     * @param startedAt {@code System.currentTimeMillis()} at entry
     */
    protected abstract void process(Envelope envelope, DeliveryContext delivery, long startedAt);

    // ── Protected helpers ─────────────────────────────────────────────────────

    protected void publish(EnvelopeEvent event) {
        ctx.publisher().publish(event);
    }

    protected void commit() {
        ctx.publisher().commit();
    }

    // ── Failure handling ──────────────────────────────────────────────────────

    private void handleFailure(Envelope envelope, DeliveryContext delivery,
                               NegotexHandlerException e) {
        int retryCount = delivery.retryCount();
        boolean stateful = this instanceof StatefulProcessor;
        boolean retryable = !stateful && e.error().isRetryable();
        boolean exhausted = retryCount >= ctx.maxRetries();
        String outcome = (retryable && !exhausted) ? "RETRYING" : "DLQ";

        log.warn("Handler failure at node {} — envelope={} error={} stateful={} outcome={}",
                ctx.nodeId(), envelope.envelopeId(), e.error().code(), stateful, outcome);

        ctx.publisher().publish(new EnvelopeEvent.Failure(
                envelope, ctx.nodeId(), ctx.handlerVersion(), e, retryCount, ctx.maxRetries(), outcome));

        if (retryable && !exhausted) {
            // Retry-topic pattern: re-queue to the same incoming topic with
            // count+1 in the transport header. Commit the current offset so
            // Kafka does not redeliver the original record.
            String incomingTopic = delivery.incomingTopic();
            if (incomingTopic != null) {
                ctx.transport().sendRetry(envelope, incomingTopic, retryCount + 1);
            } else {
                log.warn("No incomingTopic in DeliveryContext at node {} — falling through to DLQ",
                        ctx.nodeId());
            }
        }
        // Commit in all cases: retry re-queued above, or DLQ (no infinite loop)
        commit();
    }
}

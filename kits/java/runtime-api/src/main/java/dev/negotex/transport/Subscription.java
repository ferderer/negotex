package dev.negotex.transport;

/**
 * Handle for an active {@link EnvelopeTransport} subscription.
 *
 * <p>Returned by {@link EnvelopeTransport#subscribe} — call {@link #cancel}
 * to stop receiving envelopes on graceful shutdown.
 */
public interface Subscription {

    /**
     * Stop receiving envelopes and release transport resources.
     * Blocks until in-flight deliveries have completed.
     */
    void cancel();
}

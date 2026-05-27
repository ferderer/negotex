package dev.negotex.transport;

/**
 * Receives envelopes from an {@link EnvelopeTransport} subscription.
 *
 * <p>The transport calls {@link #onEnvelope(ReceivedEnvelope)} for each
 * delivered message. Transport metadata ({@link DeliveryContext}) is
 * passed alongside the business envelope but never written into it.
 *
 * <p>Implementations must be thread-safe.
 */
@FunctionalInterface
public interface EnvelopeListener {
    void onEnvelope(ReceivedEnvelope received);
}

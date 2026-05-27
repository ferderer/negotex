package dev.negotex.transport;

import dev.negotex.envelope.Envelope;

/**
 * Inbound-only pairing of a business {@link Envelope} and its
 * transport-level {@link DeliveryContext}.
 *
 * <p>Exists only on the receive path — between the transport's
 * {@code subscribe()} callback and the processor's {@code process()} method.
 * Never forwarded downstream; only {@link #envelope()} is published onward.
 *
 * <p>Keeps transport metadata strictly out of the business payload.
 */
public record ReceivedEnvelope(Envelope envelope, DeliveryContext delivery) {}

package dev.negotex.transport;

/**
 * Transport-level delivery metadata for a single envelope receipt.
 *
 * <p>Carries information that is meaningful only within the receive path:
 * which edge the envelope arrived on, which topic it came from, and how many
 * times it has been attempted. These are never written into the business
 * {@link dev.negotex.envelope.Envelope} payload.
 *
 * @param incomingEdge  edge ID derived from the topic suffix
 *                      ({@code sourceNodeId-to-targetNodeId})
 * @param incomingTopic full topic name — used by the retry path to re-queue
 *                      to the same topic
 * @param retryCount    number of previous delivery attempts for this envelope
 */
public record DeliveryContext(
        String incomingEdge,
        String incomingTopic,
        int retryCount
) {
    public static final DeliveryContext NONE = new DeliveryContext(null, null, 0);
}

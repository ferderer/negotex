package dev.negotex.transport;

/**
 * Marker interface for node processors that own explicit correlation state
 * ({@link dev.negotex.state.CorrelationStore} entries with claim/lease semantics).
 *
 * <p>Implementations: {@code JoinProcessor}, {@code WaitProcessor}.
 *
 * <p>The generic retry path in {@code NodeProcessor} does not apply to
 * stateful processors. When a stateful processor fails after acquiring a
 * claim, retrying the same envelope immediately would conflict with the
 * live lease. Instead, stateful processors route directly to DLQ on failure;
 * recovery is via claim-timeout expiry, operator-triggered replay, or
 * explicit resume (Wait).
 */
public interface StatefulProcessor {}

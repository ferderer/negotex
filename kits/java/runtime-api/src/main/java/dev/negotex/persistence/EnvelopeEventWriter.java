package dev.negotex.persistence;

/**
 * Persists {@link EnvelopeEvent} instances to TimescaleDB.
 *
 * <p>All writes are asynchronous — must never block the hot path (ADR-004).
 * Implementations must be thread-safe.
 *
 * <p>Each event type maps to a dedicated table:
 * <ul>
 *   <li>{@link EnvelopeEvent.Completion}  → {@code node_completions}</li>
 *   <li>{@link EnvelopeEvent.Failure}     → {@code node_failures}</li>
 *   <li>{@link EnvelopeEvent.Termination} → {@code process_terminations}</li>
 * </ul>
 */
public interface EnvelopeEventWriter {

    /**
     * Persist an envelope processing event asynchronously.
     *
     * @param event the event to persist — one of
     *              {@link EnvelopeEvent.Completion},
     *              {@link EnvelopeEvent.Failure},
     *              {@link EnvelopeEvent.Termination}
     */
    void write(EnvelopeEvent event);
}

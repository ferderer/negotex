package dev.negotex.persistence;

import dev.negotex.envelope.Envelope;

import java.util.List;

/**
 * Sealed hierarchy of envelope processing events persisted by
 * {@link EnvelopeEventWriter}.
 */
public sealed interface EnvelopeEvent
        permits EnvelopeEvent.Completion,
                EnvelopeEvent.Failure,
                EnvelopeEvent.Termination,
                EnvelopeEvent.Suspension {

    /**
     * Successful node processing — envelope forwarded downstream.
     *
     * <p>{@code handlerVersion} is persisted alongside the event so the
     * Audit Verifier can reproduce the hash chain without relying on
     * implicit manifest knowledge (ADR-028 requirement).
     *
     * <p>Empty {@code targetTopics} means envelope was dropped (Filter, 0 matches).
     */
    record Completion(
        Envelope envelope,
        String nodeId,
        String handlerVersion,
        List<String> targetTopics,
        long durationMs
    ) implements EnvelopeEvent {}

    /** Handler exception — retry or DLQ. */
    record Failure(
        Envelope envelope,
        String nodeId,
        String handlerVersion,
        Exception error,
        int retryCount,
        int maxRetries,
        String outcome
    ) implements EnvelopeEvent {}

    /**
     * Process instance ended at a Terminate node.
     * Permanent compliance record.
     */
    record Termination(
        Envelope envelope,
        String terminalNodeId,
        String handlerVersion,
        long durationMs
    ) implements EnvelopeEvent {}

    /**
     * Process instance suspended at a Wait node.
     * Envelope parked in Valkey, external actor notified.
     */
    record Suspension(
        Envelope envelope,
        String nodeId,
        String handlerVersion,
        String taskId,
        long durationMs
    ) implements EnvelopeEvent {}
}

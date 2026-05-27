package dev.negotex.state;

import dev.negotex.envelope.Envelope;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores and claims correlation state for stateful node processors.
 *
 * <p>Used by Join nodes (parallel branch accumulation) and Wait nodes
 * (envelope parking). The same interface serves both cases via a unified
 * key model:
 * <ul>
 *   <li>Join: {@code correlationId = processInstanceId + "." + nodeId},
 *       {@code edgeId = incomingEdgeId}</li>
 *   <li>Wait: {@code correlationId = taskId}, {@code edgeId = taskId}</li>
 * </ul>
 *
 * <p>The three-phase protocol (store → claim → complete) ensures:
 * <ul>
 *   <li><b>Idempotency</b> — storing the same edgeId twice is a no-op</li>
 *   <li><b>Exclusive processing</b> — only one caller can claim a complete set</li>
 *   <li><b>No data loss on failure</b> — state is deleted only after
 *       {@link #complete}, not after {@link #claim}</li>
 * </ul>
 *
 * <p>All operations must be thread-safe.
 */
public interface CorrelationStore {

    /**
     * Store an envelope under the given correlation ID and edge ID.
     *
     * <p>Idempotent per {@code edgeId} — storing the same edge twice
     * replaces the previous value without incrementing the count.
     *
     * @param correlationId the correlation scope (processInstanceId+nodeId for Join,
     *                      taskId for Wait)
     * @param edgeId        the unique identifier within the scope (incomingEdgeId
     *                      for Join, taskId for Wait)
     * @param envelope      the envelope to store
     * @return the set of edgeIds currently stored for this correlationId
     */
    Set<String> store(String correlationId, String edgeId, Envelope envelope);

    /**
     * Claim exclusive access to all stored envelopes for a correlation ID.
     *
     * <p>Atomically marks the correlation as claimed so no other caller
     * can process it. Returns empty if the correlation ID is unknown,
     * already claimed, or already completed.
     *
     * @param correlationId the correlation scope to claim
     * @return the claim containing a token and all stored entries, or empty
     */
    Optional<CorrelationClaim> claim(String correlationId);

    /**
     * Delete the claimed state after successful processing.
     *
     * <p>Must be called after a successful {@link #claim} and after
     * publishing the resulting event. If the processor fails after
     * claiming but before completing, the claim eventually expires
     * (TTL-based) and can be re-claimed on retry.
     *
     * @param claimToken the token from {@link CorrelationClaim#claimToken()}
     */
    void complete(String claimToken);

    /**
     * Result of a successful {@link #claim} operation.
     *
     * @param claimToken opaque token required to call {@link #complete}
     * @param entries    all stored envelopes, keyed by edgeId
     */
    record CorrelationClaim(
            String claimToken,
            Map<String, Envelope> entries
    ) {}
}

package dev.negotex.envelope;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Immutable execution unit flowing through a Negotex process graph.
 *
 * <p>{@code _hash} — SHA-256 of the initial payload, computed at the Trigger node.
 * Persists unchanged through the process instance. Used by Join for merge verification.
 *
 * <p>{@code previousEnvelopeHash} — ADR-028 chain hash. Advanced by
 * {@link #enterNode(HashChainStep)} at every node transition:
 * {@code SHA-256(previousHash || executionResult || timestamp || handlerVersion)}.
 * {@code processVersion} is NOT used in the hash — {@code handlerVersion} from the
 * {@link HashChainStep} is the sole version input, as required by ADR-028.
 */
public record Envelope(
        String envelopeId,
        String processInstanceId,
        String processDefinitionId,
        String processVersion,
        String previousEnvelopeHash,
        Instant createdAt,
        Instant nodeEnteredAt,
        Map<String, Object> payload
) {
    public Envelope {
        payload = Map.copyOf(payload);
    }

    /** Return a new envelope with an updated payload (additive enrichment). */
    public Envelope withPayload(Map<String, Object> newPayload) {
        return new Envelope(
                envelopeId, processInstanceId, processDefinitionId, processVersion,
                previousEnvelopeHash, createdAt, nodeEnteredAt, newPayload);
    }

    /**
     * Return a new envelope stamped with the current node entry time and with
     * the hash chain advanced (ADR-028).
     *
     * <p>Hash formula:
     * {@code SHA-256(previousEnvelopeHash || step.executionResult() || timestamp || step.handlerVersion())}
     *
     * <p>Both fields of {@link HashChainStep} are required — the type enforces
     * this so processors cannot accidentally omit either.
     *
     * @param step the execution result and handler version for this transition
     */
    public Envelope enterNode(HashChainStep step) {
        Instant now = Instant.now();
        String newHash = advanceHash(previousEnvelopeHash, step.executionResult(),
                now, step.handlerVersion());
        return new Envelope(
                envelopeId, processInstanceId, processDefinitionId, processVersion,
                newHash, createdAt, now, payload);
    }

    // ── Hash chain ────────────────────────────────────────────────────────────

    private static String advanceHash(String previousHash,
                                      String executionResult,
                                      Instant timestamp,
                                      String handlerVersion) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // Delimiter chosen to be unambiguous: fields cannot contain §
            String input = previousHash + "§" + executionResult
                    + "§" + timestamp.toString() + "§" + handlerVersion;
            return HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

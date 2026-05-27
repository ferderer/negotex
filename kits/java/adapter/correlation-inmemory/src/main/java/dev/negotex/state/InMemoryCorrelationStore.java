package dev.negotex.state;

import dev.negotex.envelope.Envelope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link CorrelationStore} — for testing and micro mode.
 *
 * <p>Claims expire after {@link #CLAIM_TTL_SECONDS} to prevent permanent
 * deadlock after a failure between claim and complete.
 */
@Service
public class InMemoryCorrelationStore implements CorrelationStore {

    static final long CLAIM_TTL_SECONDS = 300L;

    private final ConcurrentHashMap<String, Map<String, Envelope>> entries =
            new ConcurrentHashMap<>();

    private record ClaimEntry(String correlationId, Instant expiresAt) {}
    private final ConcurrentHashMap<String, ClaimEntry> claims = new ConcurrentHashMap<>();

    @Override
    public Set<String> store(String correlationId, String edgeId, Envelope envelope) {
        synchronized (entries) {
            entries.computeIfAbsent(correlationId, k -> new HashMap<>())
                   .put(edgeId, envelope);
            return Set.copyOf(entries.get(correlationId).keySet());
        }
    }

    @Override
    public Optional<CorrelationClaim> claim(String correlationId) {
        synchronized (entries) {
            evictExpiredClaims();

            // Already claimed?
            boolean alreadyClaimed = claims.values().stream()
                    .anyMatch(c -> correlationId.equals(c.correlationId()));
            if (alreadyClaimed) return Optional.empty();

            Map<String, Envelope> stored = entries.get(correlationId);
            if (stored == null || stored.isEmpty()) return Optional.empty();

            String token = UUID.randomUUID().toString();
            claims.put(token, new ClaimEntry(correlationId,
                    Instant.now().plusSeconds(CLAIM_TTL_SECONDS)));

            return Optional.of(new CorrelationClaim(token, Map.copyOf(stored)));
        }
    }

    @Override
    public void complete(String claimToken) {
        synchronized (entries) {
            ClaimEntry claim = claims.remove(claimToken);
            if (claim != null) {
                entries.remove(claim.correlationId());
            }
        }
    }

    private void evictExpiredClaims() {
        Instant now = Instant.now();
        claims.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}

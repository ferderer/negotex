package dev.negotex.state;

import dev.negotex.envelope.Envelope;
import dev.negotex.manifest.InfrastructureManifest;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Valkey-backed {@link CorrelationStore} using Lettuce.
 *
 * <p>Key structure:
 * <pre>
 * negotex:corr:{correlationId}:entry:{edgeId}  → serialised Envelope (TTL)
 * negotex:corr:{correlationId}:keys            → Set of stored edgeIds (TTL)
 * negotex:claim:{claimToken}                   → correlationId (short TTL)
 * </pre>
 *
 * <p>Protocol:
 * <ul>
 *   <li>{@link #store} — HSET idempotent per edgeId, returns current key set</li>
 *   <li>{@link #claim} — Lua script: SET NX claim key → atomic exclusive access</li>
 *   <li>{@link #complete} — DEL all correlation keys and the claim key</li>
 * </ul>
 */
@Service
public class ValkeyCorrelationStore implements CorrelationStore {

    private static final Logger log = LoggerFactory.getLogger(ValkeyCorrelationStore.class);

    private static final String CORR_PREFIX  = "negotex:corr:";
    private static final String CLAIM_PREFIX = "negotex:claim:";
    private static final long   TTL_SECONDS  = 86400L; // 24h
    private static final long   CLAIM_TTL    = 300L;   // 5min claim window

    /**
     * Lua script for atomic claim:
     * Returns 1 if claim succeeded (key did not exist), 0 if already claimed.
     */
    private static final String CLAIM_SCRIPT = """
            local claimKey = KEYS[1]
            local corrId   = ARGV[1]
            local ttl      = tonumber(ARGV[2])
            if redis.call('SET', claimKey, corrId, 'NX', 'EX', ttl) then
                return 1
            else
                return 0
            end
            """;

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final ObjectMapper mapper;

    public ValkeyCorrelationStore(RedisClient client) {
        this.client     = client;
        this.connection = client.connect();
        this.mapper     = JsonMapper.builder().build();
    }

    // ── CorrelationStore ──────────────────────────────────────────────────────

    @Override
    public Set<String> store(String correlationId, String edgeId, Envelope envelope) {
        RedisCommands<String, String> cmd = connection.sync();
        String entryKey = entryKey(correlationId, edgeId);
        String keysKey  = keysKey(correlationId);

        try {
            String serialised = mapper.writeValueAsString(envelope);
            cmd.setex(entryKey, TTL_SECONDS, serialised);
            cmd.sadd(keysKey, edgeId);
            cmd.expire(keysKey, TTL_SECONDS);

            Set<String> stored = cmd.smembers(keysKey);
            log.debug("Stored edgeId={} for correlationId={} ({} total)",
                    edgeId, correlationId, stored.size());
            return Set.copyOf(stored);

        } catch (Exception e) {
            log.error("store failed for correlationId={} edgeId={}: {}",
                    correlationId, edgeId, e.getMessage(), e);
            return Set.of();
        }
    }

    @Override
    public Optional<CorrelationClaim> claim(String correlationId) {
        RedisCommands<String, String> cmd = connection.sync();
        String claimToken = UUID.randomUUID().toString();
        String claimKey   = claimKey(claimToken);

        try {
            Long result = cmd.eval(
                    CLAIM_SCRIPT,
                    ScriptOutputType.INTEGER,
                    new String[]{claimKey},
                    correlationId, String.valueOf(CLAIM_TTL));

            if (result == null || result == 0L) {
                log.debug("claim failed (already claimed) for correlationId={}", correlationId);
                return Optional.empty();
            }

            // Load all entries
            Set<String> edgeIds = cmd.smembers(keysKey(correlationId));
            if (edgeIds.isEmpty()) {
                // Race: keys expired between store and claim
                cmd.del(claimKey);
                return Optional.empty();
            }

            Map<String, Envelope> entries = new HashMap<>();
            for (String edgeId : edgeIds) {
                String raw = cmd.get(entryKey(correlationId, edgeId));
                if (raw != null) {
                    entries.put(edgeId, mapper.readValue(raw, Envelope.class));
                }
            }

            log.debug("Claimed correlationId={} with {} entries token={}",
                    correlationId, entries.size(), claimToken);
            return Optional.of(new CorrelationClaim(claimToken, Map.copyOf(entries)));

        } catch (Exception e) {
            log.error("claim failed for correlationId={}: {}", correlationId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void complete(String claimToken) {
        RedisCommands<String, String> cmd = connection.sync();
        String claimKey     = claimKey(claimToken);

        try {
            // Read correlationId from claim key
            String correlationId = cmd.get(claimKey);
            if (correlationId == null) {
                log.warn("complete called for unknown or expired claimToken={}", claimToken);
                return;
            }

            // Delete all entry keys
            Set<String> edgeIds = cmd.smembers(keysKey(correlationId));
            for (String edgeId : edgeIds) {
                cmd.del(entryKey(correlationId, edgeId));
            }
            cmd.del(keysKey(correlationId));
            cmd.del(claimKey);

            log.debug("Completed correlationId={} token={}", correlationId, claimToken);

        } catch (Exception e) {
            log.error("complete failed for claimToken={}: {}", claimToken, e.getMessage(), e);
        }
    }

    // ── Key helpers ───────────────────────────────────────────────────────────

    private String entryKey(String correlationId, String edgeId) {
        return CORR_PREFIX + correlationId + ":entry:" + edgeId;
    }

    private String keysKey(String correlationId) {
        return CORR_PREFIX + correlationId + ":keys";
    }

    private String claimKey(String claimToken) {
        return CLAIM_PREFIX + claimToken;
    }

    @PreDestroy
    public void close() {
        connection.close();
        client.shutdown();
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    @Service
    public static class Factory implements CorrelationStoreFactory {

        @Override
        public CorrelationStore create(InfrastructureManifest infrastructure) {
            RedisURI uri = RedisURI.create(infrastructure.valkeyUrl());
            RedisClient client = RedisClient.create(uri);
            return new ValkeyCorrelationStore(client);
        }
    }
}

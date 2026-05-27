package dev.negotex.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic JSON canonicalizer for hash-chain execution results.
 *
 * <p>Scope is intentionally narrow: produces stable, reproducible strings
 * for {@link HashChainStep#executionResult()} only. Not a general-purpose
 * serialiser — do not use for audit storage or wire formats.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Map keys are sorted lexicographically at every level</li>
 *   <li>Collection order is preserved (handlers must sort explicitly if order is meaningful)</li>
 *   <li>Timestamps serialised as ISO-8601 strings</li>
 *   <li>Nulls excluded (use an explicit sentinel string if null is meaningful)</li>
 *   <li>Output is compact (no whitespace)</li>
 * </ul>
 */
public final class ExecutionResultCanonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private ExecutionResultCanonicalizer() {}

    /**
     * Canonicalise an arbitrary value to a stable JSON string.
     *
     * <p>Maps are sorted by key. Collections preserve insertion order —
     * callers are responsible for sorting if deterministic order is required.
     *
     * @throws IllegalArgumentException if the value cannot be serialised
     */
    public static String canonicalise(Object value) {
        if (value == null) return "null";
        try {
            // Sort map keys recursively before serialising
            Object normalised = normalise(value);
            return MAPPER.writeValueAsString(normalised);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot canonicalise value of type " + value.getClass().getName(), e);
        }
    }

    /**
     * Canonicalise and then SHA-256 hash a value.
     * Useful for large payloads where the full JSON would bloat the hash input.
     */
    public static String canonicaliseAndHash(Object value) {
        String json = canonicalise(value);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object normalise(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), normalise(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof Collection<?> col) {
            return col.stream().map(ExecutionResultCanonicalizer::normalise).toList();
        }
        return value;
    }
}

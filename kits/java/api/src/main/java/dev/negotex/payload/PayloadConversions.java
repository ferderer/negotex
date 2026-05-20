package dev.negotex.payload;

import java.util.Map;

import dev.negotex.error.NegotexHandlerException;

/**
 * Type coercion contract used by generated {@link PayloadExtractor} and
 * {@link PayloadInserter} implementations.
 *
 * <p>This interface has no implementation in {@code negotex-java-api}. The runtime
 * provides {@code JacksonPayloadConversions} and injects it into all generated
 * extractor and inserter instances at startup via {@code ExtractorRegistry}.
 *
 * <p>Generated extractor constructors accept a {@code PayloadConversions} instance:
 * <pre>{@code
 * // Generated — do not edit
 * public final class CreditCheckInputExtractor implements PayloadExtractor<CreditCheckInput> {
 *
 *     private final PayloadConversions conversions;
 *
 *     public CreditCheckInputExtractor(PayloadConversions conversions) {
 *         this.conversions = conversions;
 *     }
 *
 *     public CreditCheckInput extract(Map<String, Object> payload) {
 *         return new CreditCheckInput(
 *             conversions.convert(payload.get("application"), LoanApplication.class, "application"),
 *             conversions.convert(payload.get("customerProfile"), CustomerProfile.class, "customerProfile")
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p>Implementations must be stateless and safe for concurrent use across virtual threads.
 *
 * @see PayloadExtractor
 * @see PayloadInserter
 */
public interface PayloadConversions {

    /**
     * Convert a raw payload value to the target type.
     *
     * @param value      the raw value from the envelope payload map; may be null
     * @param targetType the target handler input type
     * @param key        the payload attribute key, used in exception messages
     * @param <T>        the target type
     * @return the converted value, never null
     * @throws NegotexHandlerException if the value is null or cannot be converted to the target type
     */
    <T> T convert(Object value, Class<T> targetType, String key);

    /**
     * Insert a single key-value pair into a payload map, returning an enriched
     * immutable copy.
     *
     * <p>Insertion is additive — existing entries are never modified. Throws if the
     * key already exists (silent overwrite would violate ADR-002) or if the key
     * uses the reserved {@code _} prefix.
     *
     * @param key     the payload attribute key to insert
     * @param value   the value to insert
     * @param payload the current immutable payload map
     * @return a new immutable payload map with the entry added
     * @throws NegotexHandlerException if the key already exists or starts with {@code _}
     */
    Map<String, Object> insert(String key, Object value, Map<String, Object> payload);
}


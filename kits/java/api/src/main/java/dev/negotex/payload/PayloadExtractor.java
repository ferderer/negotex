package dev.negotex.payload;

import java.util.Map;

import dev.negotex.error.NegotexHandlerException;

/**
 * Extracts a typed handler input from an envelope payload map.
 *
 * <p>Implementations are generated at compile time by the
 * {@code negotex-annotation-processor} for types annotated with {@link PayloadInput}
 * or handler methods whose parameter carries {@link PayloadKey}.
 * No implementation should be written by hand.
 *
 * <p>The generated implementation is named {@code {TypeName}Extractor} and located
 * in the same package as the annotated type. The runtime locates it by convention.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li><b>Stateless</b> — safe to share across virtual threads</li>
 *   <li><b>Null-safe</b> — missing payload keys throw {@link PayloadExtractionException}
 *       with the missing key name</li>
 * </ul>
 *
 * @param <T> the handler input type this extractor produces
 * @see PayloadInput
 * @see PayloadKey
 * @see PayloadConversions
 */
@FunctionalInterface
public interface PayloadExtractor<T> {

    /**
     * Extract a typed value from the envelope payload map.
     *
     * @param payload the immutable envelope payload
     * @return the extracted and converted handler input
     * @throws NegotexHandlerException if a required key is absent or
     *                                    the value cannot be converted to the target type
     */
    T extract(Map<String, Object> payload);
}

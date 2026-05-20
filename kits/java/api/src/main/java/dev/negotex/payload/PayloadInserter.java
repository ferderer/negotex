package dev.negotex.payload;

import java.util.Map;

import dev.negotex.error.NegotexHandlerException;

/**
 * Inserts a typed handler output into an envelope payload map.
 *
 * <p>Implementations are generated at compile time by the
 * {@code negotex-annotation-processor} for types annotated with {@link PayloadOutput}
 * or handler methods annotated with {@code @PayloadOutput("key")}.
 * No implementation should be written by hand.
 *
 * <p>The generated implementation is named {@code {TypeName}Inserter} and located
 * in the same package as the annotated type. The runtime locates it by convention.
 *
 * <p>Insertion is <em>additive</em> — consistent with the envelope enrichment model
 * (ADR-002). The inserter adds new keys to the payload; it never modifies or removes
 * existing keys. Attempting to insert a key that already exists throws
 * {@link PayloadInsertionException} to prevent silent overwrites.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li><b>Stateless</b> — safe to share across virtual threads</li>
 *   <li><b>Non-destructive</b> — existing payload entries are never modified</li>
 * </ul>
 *
 * @param <T> the handler output type this inserter consumes
 * @see PayloadOutput
 * @see PayloadKey
 */
@FunctionalInterface
public interface PayloadInserter<T> {

    /**
     * Insert the handler output into the payload map, returning an enriched copy.
     *
     * <p>The returned map is a new immutable map containing all existing entries
     * plus the inserted output attributes. The input {@code payload} is not modified.
     *
     * @param output  the handler output to insert
     * @param payload the current immutable envelope payload
     * @return a new immutable payload map with the output attributes added
     * @throws NegotexHandlerException if any output key already exists in the payload
     *                                   or if a key starts with {@code _} (reserved)
     */
    Map<String, Object> insert(T output, Map<String, Object> payload);
}

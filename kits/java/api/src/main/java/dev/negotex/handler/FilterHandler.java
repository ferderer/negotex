package dev.negotex.handler;

import java.util.Map;
import java.util.Set;

/**
 * Handler for Filter nodes — selects 0–N outgoing edges.
 *
 * <p>Returning an empty set is valid: the envelope is consumed and dropped.
 */
@FunctionalInterface
public interface FilterHandler {
    Set<String> selectEdges(Map<String, Object> payload);
}

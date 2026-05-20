package dev.negotex.handler;

import java.util.Map;

/**
 * Handler for Choice nodes — selects exactly one outgoing edge.
 *
 * @return the edge ID to activate, or {@code null} to activate the default edge
 */
@FunctionalInterface
public interface ChoiceHandler {
    String selectEdge(Map<String, Object> payload);
}

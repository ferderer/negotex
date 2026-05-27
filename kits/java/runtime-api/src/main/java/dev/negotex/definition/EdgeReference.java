package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A reference to an edge from within a node definition.
 *
 * <p>Used for both incoming and outgoing edge lists. Outgoing edges on
 * Choice and Filter nodes carry an optional condition expression and
 * a default flag.
 *
 * <p>Short form (no condition): the YAML value is just the edge ID string.
 * Long form (with condition): a mapping with {@code id} and {@code condition}.
 *
 * <pre>{@code
 * # Short form — unconditional edge
 * outgoing: [validate-to-fork]
 *
 * # Long form — conditional edge on a Choice node
 * outgoing:
 *   - id: route-to-small-loan
 *     condition: "amount < 1000"
 *   - id: route-to-large-loan
 *     default: true
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgeReference(

        /** Edge ID — must match an entry in the top-level {@code edges} list. */
        String id,

        /**
         * Condition expression for Choice and Filter outgoing edges.
         * Evaluated against the envelope payload.
         * Null for unconditional edges.
         */
        String condition,

        /**
         * True if this is the default edge for a Choice node.
         * Activates when no other condition matches.
         * Every Choice node must have exactly one default edge.
         */
        boolean isDefault
) {
    /** Convenience constructor for unconditional edges. */
    public static EdgeReference of(String id) {
        return new EdgeReference(id, null, false);
    }

    /** Convenience constructor for conditional edges. */
    public static EdgeReference conditional(String id, String condition) {
        return new EdgeReference(id, condition, false);
    }

    /** Convenience constructor for default edges. */
    public static EdgeReference defaultEdge(String id) {
        return new EdgeReference(id, null, true);
    }
}

package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Incoming and outgoing edge references for a node.
 *
 * <p>Each entry is an {@link EdgeReference} — either a plain edge ID string
 * (unconditional) or a mapping with an optional condition and default flag
 * (for Choice and Filter outgoing edges).
 *
 * <pre>{@code
 * # Unconditional edges (Map, Fork, Join, Wait, Trigger, Terminate)
 * edges:
 *   incoming: [validate-to-fork]
 *   outgoing: [fork-to-credit, fork-to-income, fork-to-fraud]
 *
 * # Conditional outgoing edges (Choice)
 * edges:
 *   incoming: [join-to-decision]
 *   outgoing:
 *     - id: decision-to-approve
 *       condition: "creditScore >= 650 && income >= 30000"
 *     - id: decision-to-reject
 *       default: true
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeEdges(

        /**
         * Incoming edge references.
         * Empty for Trigger nodes (no incoming edges).
         */
        List<EdgeReference> incoming,

        /**
         * Outgoing edge references.
         * Empty for Terminate nodes (no outgoing edges).
         * For Choice nodes: exactly one must have {@code default: true}.
         * For Filter nodes: at most one may have {@code default: true}
         * (activates when no condition matches).
         */
        List<EdgeReference> outgoing
) {}

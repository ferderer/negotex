package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A top-level edge declaration in the process definition.
 *
 * <p>Edges are first-class in Negotex — each compiles to a Kafka topic.
 * The top-level {@code edges} list declares all edges in the process.
 * Conditions live on the node's {@link EdgeReference}, not here.
 *
 * <p>The edge ID must be unique within the process and follow kebab-case
 * convention. It must not contain dots (dots are reserved as separators
 * in Kafka topic names).
 *
 * <pre>{@code
 * edges:
 *   - id: start-to-validate
 *   - id: validate-to-fork
 *   - id: fork-to-credit-check
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgeDefinition(

        /**
         * Edge identifier — unique within the process.
         * Kebab-case. No dots.
         * Used to construct the Kafka topic name per ADR-026:
         * {@code {processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}}
         */
        String id
) {}

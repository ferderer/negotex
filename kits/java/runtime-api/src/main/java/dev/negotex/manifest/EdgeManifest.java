package dev.negotex.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single directed edge in the compiled process topology.
 *
 * <p>Each edge corresponds to one Kafka topic. The topic name is computed
 * by the compiler from the process ID, version, source node ID, and
 * target node ID per the convention in ADR-026:
 * {@code {processId}-v{version}.edge.{sourceNodeId}-to-{targetNodeId}}
 *
 * <p>For Choice and Filter nodes, edges carry an optional condition
 * expression and a flag indicating the default edge.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgeManifest(

        /** Edge identifier — unique within the process definition. */
        String id,

        /** Source node ID. */
        String sourceNodeId,

        /** Target node ID. */
        String targetNodeId,

        /**
         * Compiled Kafka topic name for this edge.
         * Set by the compiler — not present in the source process definition.
         */
        String topicName,

        /**
         * Condition expression for Choice and Filter outgoing edges.
         * Null for unconditional edges (Map, Fork, Join, Merge, Wait, Trigger, Terminate).
         */
        String condition,

        /**
         * True if this is the default edge for a Choice node.
         * The default edge activates when no other condition matches.
         */
        boolean defaultEdge
) {}

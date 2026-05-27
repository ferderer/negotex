package dev.negotex.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single compiled node in the process topology.
 *
 * <p>Contains everything the runtime needs to set up a node processor:
 * the node's type, its handler wiring, its incoming and outgoing edge
 * topic names, and its operational contracts.
 *
 * <p>Primitive-specific fields (e.g. {@code mergeStrategy} for Join,
 * {@code expectedBranches} for Join, {@code timerExpression} for Wait)
 * are null for nodes where they don't apply.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeManifest(

        /** Node identifier — unique within the process definition. */
        String id,

        /** Node type — one of the nine Negotex primitives (ADR-006). */
        NodeType type,

        /** Handler execution mode (ADR-005). Null for handler-less nodes (Fork, Merge). */
        HandlerMode mode,

        /** Behavioural consistency contract (ADR-030). Null for handler-less nodes. */
        ConsistencyContract consistency,

        /** Handler wiring — class name, version, extractor, inserter. Null for handler-less nodes. */
        HandlerManifest handler,

        /** Governance policy — required for DIRTY nodes (ADR-029). */
        GovernancePolicy governance,

        /** IDs of incoming edges. Empty for Trigger nodes (no incoming edges). */
        List<String> incomingEdgeIds,

        /** IDs of outgoing edges. Empty for Terminate nodes (no outgoing edges). */
        List<String> outgoingEdgeIds,

        // ── Primitive-specific fields ──────────────────────────────────────

        /**
         * Join: number of incoming branches to wait for before merging.
         * Null for all other node types.
         * For Filter-paired Joins, this is resolved dynamically from Valkey
         * at runtime rather than from this static value.
         */
        Integer expectedBranches,

        /**
         * Join: payload merge strategy (ADR-009).
         * Defaults to {@link MergeStrategy#ATTRIBUTE_MERGE} if null.
         */
        MergeStrategy mergeStrategy,

        /**
         * Choice/Filter: condition evaluation strategy.
         * e.g. {@code "expression"}, {@code "handler"}, {@code "script-groovy"}.
         * Null for node types that don't evaluate conditions.
         */
        String conditionStrategy,

        /**
         * Wait (timer variant): cron expression or ISO-8601 duration for automatic
         * resumption. e.g. {@code "PT24H"} (24-hour timeout).
         * Null for non-timer Wait nodes and all other node types.
         */
        String timerExpression,

        /**
         * Trigger: activation type.
         * e.g. {@code "api"}, {@code "message"}, {@code "timer"}, {@code "signal"}.
         * Null for non-Trigger nodes.
         */
        String triggerType,

        /**
         * Trigger (timer variant): cron expression for scheduled activation.
         * e.g. {@code "0 9 * * MON-FRI"} (weekdays at 09:00).
         * Null for non-timer Trigger nodes.
         */
        String cronExpression,

        /**
         * Trigger (message variant): Kafka topic to subscribe to for message-triggered starts.
         * Null for non-message Trigger nodes.
         */
        String messageTopic,

        /**
         * Maximum number of retry attempts before routing to DLQ.
         * Overrides process-level default. Null means use process default.
         */
        Integer maxRetries
) {}

package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.negotex.manifest.ConsistencyContract;
import dev.negotex.manifest.HandlerMode;
import dev.negotex.manifest.MergeStrategy;
import dev.negotex.manifest.NodeType;

/**
 * A single node in the process definition.
 *
 * <p>Primitive-specific fields (e.g. {@code expectedBranches} for Join,
 * {@code triggerType} for Trigger) are null for node types where they
 * don't apply. The compiler validates that required fields are present
 * for each node type.
 *
 * <pre>{@code
 * # Map node — pure handler
 * - id: validate
 *   type: map
 *   runtime: order-core
 *   handler: ValidateApplicationHandler
 *   mode: pure
 *   consistency: deterministic
 *   edges:
 *     incoming: [start-to-validate]
 *     outgoing: [validate-to-fork]
 *
 * # Join node — no handler
 * - id: join-checks
 *   type: join
 *   expectedBranches: 3
 *   mergeStrategy: attribute-merge
 *   edges:
 *     incoming: [credit-to-join, income-to-join, fraud-to-join]
 *     outgoing: [join-to-decision]
 *
 * # Choice node — conditional routing
 * - id: route-decision
 *   type: choice
 *   runtime: order-core
 *   handler: LoanDecisionHandler
 *   mode: pure
 *   consistency: deterministic
 *   conditionStrategy: handler
 *   edges:
 *     incoming: [join-to-decision]
 *     outgoing:
 *       - id: decision-to-approve
 *         condition: "approved"
 *       - id: decision-to-reject
 *         default: true
 *
 * # Trigger node — API-activated
 * - id: start
 *   type: trigger
 *   runtime: order-core
 *   handler: LoanApplicationTriggerHandler
 *   mode: pure
 *   consistency: deterministic
 *   triggerType: api
 *   edges:
 *     outgoing: [start-to-validate]
 *
 * # Wait node — human approval with timeout
 * - id: await-approval
 *   type: wait
 *   runtime: order-core
 *   handler: ApprovalWaitHandler
 *   mode: dirty
 *   consistency: eventual
 *   timerExpression: PT48H
 *   governance:
 *     allowedExternalCalls: [notification-service]
 *     retryPolicy: idempotent
 *   edges:
 *     incoming: [route-to-wait]
 *     outgoing: [wait-to-terminate]
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeDefinition(

        /** Node identifier — unique within the process. Kebab-case. */
        String id,

        /** Node type — one of the nine Negotex primitives (ADR-006). */
        NodeType type,

        /**
         * Runtime instance name — must match an entry in {@code deployment.yaml}.
         * Null for handler-less nodes (Fork, Merge, Join without custom handler).
         */
        String runtime,

        /**
         * Fully qualified handler class name.
         * Null for handler-less primitives (Fork, Merge).
         * Language-specific format:
         *   Java: {@code dev.acme.loan.CreditCheckHandler}
         *   F#:   {@code Acme.Risk.RiskCalculation.evaluate}
         *   Rust: {@code acme_risk::risk::evaluate}
         */
        String handler,

        /**
         * Handler version. Tracked in the hash chain (ADR-028).
         * Must be bumped on every logic change even if process version
         * does not change.
         */
        String handlerVersion,

        /** Handler execution mode (ADR-005). Null for handler-less nodes. */
        HandlerMode mode,

        /** Behavioural consistency contract (ADR-030). Null for handler-less nodes. */
        ConsistencyContract consistency,

        /** Governance policy — required for dirty nodes (ADR-029). */
        GovernanceDefinition governance,

        /** Incoming and outgoing edge references with optional conditions. */
        NodeEdges edges,

        // ── Primitive-specific ─────────────────────────────────────────────

        /** Join: number of branches to wait for. Null for other node types. */
        Integer expectedBranches,

        /**
         * Join: merge strategy. Defaults to {@code attribute-merge} if null.
         * Null for other node types.
         */
        MergeStrategy mergeStrategy,

        /**
         * Choice/Filter: condition evaluation strategy.
         * e.g. {@code "expression"}, {@code "handler"}, {@code "script-groovy"}.
         * Null for other node types.
         */
        String conditionStrategy,

        /**
         * Trigger: activation type.
         * e.g. {@code "api"}, {@code "message"}, {@code "timer"}, {@code "signal"}.
         * Null for non-Trigger nodes.
         */
        String triggerType,

        /**
         * Trigger (timer): cron expression for scheduled activation.
         * e.g. {@code "0 9 * * MON-FRI"}
         * Null for non-timer Trigger nodes.
         */
        String cronExpression,

        /**
         * Trigger (message): Kafka topic to subscribe to.
         * Null for non-message Trigger nodes.
         */
        String messageTopic,

        /**
         * Wait: ISO-8601 duration for automatic timeout resumption.
         * e.g. {@code "PT48H"} (48-hour approval window).
         * Null for non-timer Wait nodes.
         */
        String timerExpression,

        /**
         * Maximum retry attempts for this node.
         * Overrides the process-level {@code defaultMaxRetries}.
         * Null means use process default.
         */
        Integer maxRetries
) {}

package dev.negotex.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Governance policy for a node — what the handler is permitted to do (ADR-029).
 *
 * <p>Required for all {@link HandlerMode#DIRTY} nodes. The process compiler
 * validates that the handler's actual behaviour (as far as it can be verified)
 * is within the declared bounds.
 *
 * <p>All fields are optional — only declare what applies to this node.
 * Missing fields apply no constraint on that dimension.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GovernancePolicy(

        /**
         * Maximum wall-clock execution time, e.g. {@code "2s"}, {@code "500ms"}.
         * The runtime cancels the handler invocation if it exceeds this limit
         * and records a {@code HandlerError.EXECUTION_TIMEOUT} failure.
         */
        String maxExecutionTime,

        /**
         * Maximum heap allocation hint, e.g. {@code "64MB"}.
         * Enforcement is JVM / language dependent — declaration-only in PoC.
         */
        String maxMemoryUsage,

        /**
         * Named external services this node is permitted to call.
         * Validated against the cluster's allowed service registry at deployment.
         * e.g. {@code ["payment-gateway", "credit-bureau"]}
         */
        List<String> allowedExternalCalls,

        /**
         * Retry semantics. Defaults to {@link RetryPolicy#IDEMPOTENT} if absent.
         */
        RetryPolicy retryPolicy,

        /**
         * Retention period override for audit events from this node.
         * Overrides the process-level default. e.g. {@code "10y"}.
         */
        String auditRetention
) {}

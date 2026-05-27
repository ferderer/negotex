package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Governance policy for a node in the process definition (ADR-029).
 *
 * <p>Required for all {@code mode: dirty} nodes. Declares what the handler
 * is permitted to do. Validated by the compiler at deployment time.
 *
 * <pre>{@code
 * governance:
 *   maxExecutionTime: 5s
 *   maxMemoryUsage: 64MB
 *   allowedExternalCalls:
 *     - payment-gateway
 *     - credit-bureau
 *   retryPolicy: idempotent
 *   auditRetention: 10y
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GovernanceDefinition(
        String maxExecutionTime,
        String maxMemoryUsage,
        List<String> allowedExternalCalls,
        String retryPolicy,
        String auditRetention
) {}

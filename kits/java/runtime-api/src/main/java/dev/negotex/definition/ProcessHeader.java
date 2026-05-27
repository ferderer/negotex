package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Top-level process metadata in the process definition.
 *
 * <pre>{@code
 * process:
 *   id: loan-application
 *   version: 1.3.0
 *   auditCertification: false
 *   defaultMaxRetries: 3
 *   defaultAuditRetention: 7y
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessHeader(

        /**
         * Process identifier — unique across all processes in the cluster.
         * Kebab-case. Used in Kafka topic names (ADR-026).
         * e.g. {@code "loan-application"}
         */
        String id,

        /**
         * Structural process version — semver.
         * Bumped when nodes are added, removed, or edges rewired.
         * Used in Kafka topic names (ADR-026).
         * Handler logic changes bump {@link NodeDefinition#handlerVersion()}
         * only, not this field.
         * e.g. {@code "1.3.0"}
         */
        String version,

        /**
         * When true, all nodes must declare {@code consistency: deterministic}
         * (or be {@code mode: pure}). Deployment fails if any node declares
         * {@code consistency: eventual}. Enables full process replay
         * verification (ADR-030).
         */
        boolean auditCertification,

        /**
         * Default maximum retry attempts for all nodes.
         * Individual nodes may override via {@link NodeDefinition#maxRetries()}.
         * Defaults to 3 if absent.
         */
        Integer defaultMaxRetries,

        /**
         * Default audit retention period for all nodes.
         * e.g. {@code "7y"}, {@code "10y"}.
         * Individual nodes may override via {@link GovernanceDefinition#auditRetention()}.
         */
        String defaultAuditRetention
) {}

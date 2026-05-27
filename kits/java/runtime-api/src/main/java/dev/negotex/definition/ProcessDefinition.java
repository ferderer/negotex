package dev.negotex.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Root model for {@code process.yaml} — the developer-authored process definition.
 *
 * <p>The process definition describes the process graph: nodes, edges, handler
 * references, governance policies, and consistency contracts. It contains no
 * environment-specific configuration — that belongs in {@code deployment.yaml}.
 *
 * <p>The process compiler reads this file alongside {@code deployment.yaml} and
 * produces one {@code RuntimeManifest} per runtime instance.
 *
 * <p>Full example — loan application process:
 * <pre>{@code
 * process:
 *   id: loan-application
 *   version: 1.3.0
 *   auditCertification: false
 *   defaultMaxRetries: 3
 *   defaultAuditRetention: 7y
 *
 * nodes:
 *   - id: start
 *     type: trigger
 *     runtime: loan-core
 *     handler: dev.acme.loan.LoanApplicationTriggerHandler
 *     handlerVersion: 1.0.0
 *     mode: pure
 *     consistency: deterministic
 *     triggerType: api
 *     edges:
 *       outgoing: [start-to-validate]
 *
 *   - id: validate
 *     type: map
 *     runtime: loan-core
 *     handler: dev.acme.loan.ValidateApplicationHandler
 *     handlerVersion: 1.1.0
 *     mode: pure
 *     consistency: deterministic
 *     edges:
 *       incoming: [start-to-validate]
 *       outgoing: [validate-to-fork]
 *
 *   - id: split-checks
 *     type: fork
 *     edges:
 *       incoming: [validate-to-fork]
 *       outgoing: [fork-to-credit, fork-to-income, fork-to-fraud]
 *
 *   - id: credit-check
 *     type: map
 *     runtime: loan-core
 *     handler: dev.acme.loan.CreditCheckHandler
 *     handlerVersion: 2.1.0
 *     mode: dirty
 *     consistency: eventual
 *     governance:
 *       allowedExternalCalls: [credit-bureau]
 *       maxExecutionTime: 5s
 *       retryPolicy: idempotent
 *     edges:
 *       incoming: [fork-to-credit]
 *       outgoing: [credit-to-join]
 *
 *   - id: join-checks
 *     type: join
 *     expectedBranches: 3
 *     mergeStrategy: attribute-merge
 *     edges:
 *       incoming: [credit-to-join, income-to-join, fraud-to-join]
 *       outgoing: [join-to-decision]
 *
 *   - id: route-decision
 *     type: choice
 *     runtime: loan-core
 *     handler: dev.acme.loan.LoanDecisionHandler
 *     handlerVersion: 1.0.0
 *     mode: pure
 *     consistency: deterministic
 *     conditionStrategy: handler
 *     edges:
 *       incoming: [join-to-decision]
 *       outgoing:
 *         - id: decision-to-approve
 *           condition: "approved"
 *         - id: decision-to-reject
 *           default: true
 *
 *   - id: end-approved
 *     type: terminate
 *     runtime: loan-core
 *     handler: dev.acme.loan.ApprovalNotificationHandler
 *     handlerVersion: 1.0.0
 *     mode: dirty
 *     consistency: eventual
 *     governance:
 *       allowedExternalCalls: [notification-service]
 *       retryPolicy: idempotent
 *     edges:
 *       incoming: [decision-to-approve]
 *
 *   - id: end-rejected
 *     type: terminate
 *     edges:
 *       incoming: [decision-to-reject]
 *
 * edges:
 *   - id: start-to-validate
 *   - id: validate-to-fork
 *   - id: fork-to-credit
 *   - id: fork-to-income
 *   - id: fork-to-fraud
 *   - id: credit-to-join
 *   - id: income-to-join
 *   - id: fraud-to-join
 *   - id: join-to-decision
 *   - id: decision-to-approve
 *   - id: decision-to-reject
 * }</pre>
 */
public record ProcessDefinition(

        /** Top-level process metadata. */
        ProcessHeader process,

        /** All nodes in the process graph. Order is not significant. */
        List<NodeDefinition> nodes,

        /** All edges in the process graph. Order is not significant. */
        List<EdgeDefinition> edges
) {

    // ── Serialisation ─────────────────────────────────────────────────────────

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    );

    /**
     * Deserialise a {@code ProcessDefinition} from a YAML file.
     *
     * @param path path to {@code process.yaml}
     * @return the deserialised process definition
     * @throws IOException if the file cannot be read or parsed
     */
    public static ProcessDefinition fromYaml(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return YAML_MAPPER.readValue(in, ProcessDefinition.class);
        }
    }

    // ── Convenience lookups ───────────────────────────────────────────────────

    /**
     * Index nodes by ID for O(1) lookup.
     * Called by the compiler — not materialised on the record itself
     * to keep the model pure data.
     */
    public Map<String, NodeDefinition> nodeIndex() {
        return nodes.stream()
                .collect(Collectors.toMap(NodeDefinition::id, Function.identity()));
    }

    /**
     * Index edges by ID for O(1) lookup.
     */
    public Map<String, EdgeDefinition> edgeIndex() {
        return edges.stream()
                .collect(Collectors.toMap(EdgeDefinition::id, Function.identity()));
    }
}

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
 * Root model for {@code deployment.yaml} — the environment-specific
 * deployment configuration.
 *
 * <p>The deployment definition is ops-authored and environment-specific.
 * The same {@code process.yaml} deploys unchanged to dev, staging, and
 * production — only {@code deployment.yaml} differs per environment.
 *
 * <p>The process compiler reads this file alongside {@code process.yaml}
 * and produces one {@code RuntimeManifest} per runtime instance.
 *
 * <p>Full example — single-runtime Java deployment:
 * <pre>{@code
 * runtimes:
 *   - name: loan-core
 *     language: java
 *     handlerArtifact: s3://acme-artifacts/loan-handlers-1.0.jar
 *     nodes:
 *       - start
 *       - validate
 *       - split-checks
 *       - credit-check
 *       - income-check
 *       - fraud-check
 *       - join-checks
 *       - route-decision
 *       - end-approved
 *       - end-rejected
 *
 * infrastructure:
 *   kafkaBrokers: kafka:9092
 *   valkeyUrl: redis://valkey:6379
 *   timescaleDb:
 *     url: jdbc:postgresql://timescaledb:5432/negotex
 *     user: negotex
 *     passwordRef: ${TIMESCALEDB_PASSWORD}
 *   victoriaMetricsUrl: http://victoriametrics:8428/api/v1/write
 * }</pre>
 *
 * <p>Polyglot deployment — Java + F# runtime instances:
 * <pre>{@code
 * runtimes:
 *   - name: loan-core
 *     language: java
 *     handlerArtifact: s3://acme/loan-handlers-1.0.jar
 *     nodes: [start, validate, split-checks, join-checks, route-decision,
 *             end-approved, end-rejected]
 *
 *   - name: risk-engine
 *     language: fsharp
 *     handlerArtifact: s3://acme/risk-engine-2.0.dll
 *     nodes: [credit-check, income-check, fraud-check]
 *
 * infrastructure:
 *   kafkaBrokers: kafka:9092
 *   ...
 * }</pre>
 */
public record DeploymentDefinition(

        /** Runtime instances participating in this deployment. */
        List<RuntimeInstanceDefinition> runtimes,

        /** Infrastructure endpoint configuration. */
        InfrastructureDefinition infrastructure
) {

    // ── Serialisation ─────────────────────────────────────────────────────────

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    );

    /**
     * Deserialise a {@code DeploymentDefinition} from a YAML file.
     *
     * @param path path to {@code deployment.yaml}
     * @return the deserialised deployment definition
     * @throws IOException if the file cannot be read or parsed
     */
    public static DeploymentDefinition fromYaml(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return YAML_MAPPER.readValue(in, DeploymentDefinition.class);
        }
    }

    // ── Convenience lookups ───────────────────────────────────────────────────

    /**
     * Index runtime instances by name for O(1) lookup.
     */
    public Map<String, RuntimeInstanceDefinition> runtimeIndex() {
        return runtimes.stream()
                .collect(Collectors.toMap(
                        RuntimeInstanceDefinition::name, Function.identity()));
    }

    /**
     * Find the runtime instance responsible for a given node ID.
     *
     * @throws IllegalArgumentException if the node is not assigned to any runtime
     */
    public RuntimeInstanceDefinition runtimeForNode(String nodeId) {
        return runtimes.stream()
                .filter(r -> r.nodes().contains(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Node '%s' is not assigned to any runtime instance".formatted(nodeId)));
    }
}

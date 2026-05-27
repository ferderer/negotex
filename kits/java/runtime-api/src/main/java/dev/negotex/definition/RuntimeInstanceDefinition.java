package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single runtime instance in the deployment definition.
 *
 * <p>One runtime instance = one deployed container running one language kit,
 * handling a named subset of nodes. A single-language process has one
 * runtime instance. A polyglot process has one per language.
 *
 * <pre>{@code
 * runtimes:
 *   - name: loan-core
 *     language: java
 *     handlerArtifact: s3://acme/loan-handlers-1.0.jar
 *     nodes: [start, validate, split-checks, credit-check, income-check,
 *             fraud-check, join-checks, route-decision, end-approved, end-rejected]
 *
 *   - name: risk-engine
 *     language: fsharp
 *     handlerArtifact: s3://acme/risk-engine-2.0.dll
 *     nodes: [calculate-risk]
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeInstanceDefinition(

        /**
         * Runtime instance name — must match the {@code runtime} field
         * on nodes in {@code process.yaml}.
         */
        String name,

        /**
         * Language kit to use.
         * e.g. {@code "java"}, {@code "fsharp"}, {@code "rust"}
         */
        String language,

        /**
         * Handler artifact location. Resolved at startup.
         * Supported schemes:
         *   S3:     {@code s3://bucket/path/handlers.jar}
         *   Volume: {@code /handlers/handlers.jar}
         *   Image:  {@code acme/risk-engine:2.0} (custom Docker image — Mode 2)
         */
        List<String> handlerArtifacts,

        /**
         * Node IDs assigned to this runtime instance.
         * Must be a subset of the node IDs declared in {@code process.yaml}.
         * The compiler validates completeness: every node with a {@code runtime}
         * field must appear in exactly one runtime instance's node list.
         */
        List<String> nodes
) {}

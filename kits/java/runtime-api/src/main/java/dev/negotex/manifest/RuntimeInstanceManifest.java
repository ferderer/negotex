package dev.negotex.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Describes the runtime instance this manifest was generated for.
 *
 * <p>One {@code RuntimeInstanceManifest} exists per deployed container.
 * It identifies which language kit is running, where the handler artifact
 * is located, and which node IDs this instance is responsible for.
 *
 * <p>A polyglot process produces one {@code RuntimeManifest} per runtime
 * instance — each instance only knows about its own nodes, not the full
 * process topology.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeInstanceManifest(

        /**
         * Runtime instance name — matches the name declared in {@code deployment.yaml}.
         * e.g. {@code "order-core"}, {@code "risk-engine"}
         */
        String name,

        /**
         * Programming language of this runtime instance.
         * e.g. {@code "java"}, {@code "fsharp"}, {@code "rust"}
         */
        String language,

        /**
         * Handler artifact location.
         * May be an S3 URL, a volume path, or a Docker image reference.
         * e.g. {@code "s3://acme/order-handlers-1.0.jar"}
         *      {@code "/handlers/loan-app.jar"}
         */
        List<String> handlerArtifacts,

        /**
         * Node IDs handled by this runtime instance.
         * The runtime only starts processors for nodes in this list.
         */
        List<String> nodeIds
) {}

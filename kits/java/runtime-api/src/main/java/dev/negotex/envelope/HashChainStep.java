package dev.negotex.envelope;

import java.util.List;

/**
 * Value object representing one step in the ADR-028 envelope hash chain.
 *
 * <p>Both fields are required — the chain contract is enforced at the type
 * level so processors cannot accidentally omit either. Lives in
 * {@code runtime-api} so all language kits implement the same contract.
 *
 * <p>Use the static factory methods for built-in node types to ensure
 * consistent, semantically meaningful execution results.
 *
 * @param executionResult canonical, deterministically serialised representation
 *                        of what this node decided or produced. Must be stable
 *                        across JVM restarts — use {@link ExecutionResultCanonicalizer}.
 * @param handlerVersion  version string identifying the exact handler logic.
 *                        For user handlers: from {@code HandlerManifest.handlerVersion()}.
 *                        For built-in nodes: {@code builtin:<type>@<runtimeVersion>}
 *                        as produced by {@code ProcessorStarter.resolveHandlerVersion()}.
 */
public record HashChainStep(String executionResult, String handlerVersion) {

    public HashChainStep {
        if (executionResult == null || executionResult.isBlank())
            throw new IllegalArgumentException("executionResult must not be blank");
        if (handlerVersion == null || handlerVersion.isBlank())
            throw new IllegalArgumentException("handlerVersion must not be blank");
    }

    // ── Built-in node factories ───────────────────────────────────────────────
    // All factories take handlerVersion directly from ProcessorContext.handlerVersion()
    // so callers do not need to re-parse or reconstruct the version string.

    /** Fork: sorted target topics — deterministic regardless of definition order. */
    public static HashChainStep fork(List<String> targetTopics, String handlerVersion) {
        var sorted = targetTopics.stream().sorted().toList();
        return new HashChainStep("fork:" + sorted, handlerVersion);
    }

    /** Merge: records the incoming edge ID. */
    public static HashChainStep merge(String incomingEdge, String handlerVersion) {
        return new HashChainStep("merge:" + incomingEdge, handlerVersion);
    }

    /**
     * Join: actual merge result hash + strategy + branch count.
     * Uses the SHA-256 of the merged payload rather than the payload itself
     * to keep the hash input bounded.
     */
    public static HashChainStep join(String mergeResultHash, String strategy,
                                     int branchCount, String handlerVersion) {
        return new HashChainStep(
                "join:" + strategy + ":" + branchCount + ":" + mergeResultHash,
                handlerVersion);
    }

    /** Wait — suspend phase. */
    public static HashChainStep waitSuspend(String taskId, String handlerVersion) {
        return new HashChainStep("suspend:" + taskId, handlerVersion);
    }

    /**
     * Wait — resume phase.
     *
     * @param resumeResultJson canonical JSON of the enriched delta returned by
     *                         {@code WaitHandler.onResume()} — produced via
     *                         {@link ExecutionResultCanonicalizer}
     */
    public static HashChainStep waitResume(String taskId, String resumeResultJson,
                                           String handlerVersion) {
        return new HashChainStep("resume:" + taskId + ":" + resumeResultJson, handlerVersion);
    }

    /** Terminate: canonical JSON of the final payload. */
    public static HashChainStep terminate(String finalPayloadJson, String handlerVersion) {
        return new HashChainStep("terminate:" + finalPayloadJson, handlerVersion);
    }

    /** Choice: the selected edge ID (or "default"). */
    public static HashChainStep choice(String selectedEdgeId, String handlerVersion) {
        return new HashChainStep("choice:" + selectedEdgeId, handlerVersion);
    }

    /** Filter: sorted selected edge IDs — deterministic regardless of handler return order. */
    public static HashChainStep filter(List<String> selectedEdgeIds, String handlerVersion) {
        var sorted = selectedEdgeIds.stream().sorted().toList();
        return new HashChainStep("filter:" + sorted, handlerVersion);
    }
}

package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.ExecutionResultCanonicalizer;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.error.HandlerError;
import dev.negotex.error.NegotexHandlerException;
import dev.negotex.manifest.MergeStrategy;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.state.CorrelationStore;
import dev.negotex.transport.DeliveryContext;
import dev.negotex.transport.StatefulProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processor for Join nodes — parallel gateway converging (ADR-009).
 *
 * <p>Protocol:
 * <ol>
 *   <li>store(correlationId, incomingEdgeId, envelope) — idempotent per edgeId</li>
 *   <li>If stored edgeIds == expectedEdgeIds: claim()</li>
 *   <li>If claimed: merge, publish, complete(), commit()</li>
 *   <li>If not complete or not claimed: commit() only</li>
 * </ol>
 *
 * <p>complete() is called after publish() — state survives a publish failure
 * and the join can be retried.
 */
public class JoinProcessor extends NodeProcessor implements StatefulProcessor {

    private final MergeStrategy mergeStrategy;
    private final CorrelationStore correlationStore;
    private final Set<String> expectedEdgeIds;

    public JoinProcessor(ProcessorContext ctx,
                         int expectedBranches,
                         MergeStrategy mergeStrategy,
                         CorrelationStore correlationStore,
                         Set<String> expectedEdgeIds) {
        super(ctx);
        this.mergeStrategy    = mergeStrategy != null ? mergeStrategy : MergeStrategy.ATTRIBUTE_MERGE;
        this.correlationStore = correlationStore;
        this.expectedEdgeIds  = Set.copyOf(expectedEdgeIds);
    }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        String edgeId = delivery.incomingEdge();
        if (edgeId == null) {
            throw new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE)
                    .with("nodeId", ctx.nodeId())
                    .with("envelopeId", incoming.envelopeId())
                    .with("reason", "DeliveryContext.incomingEdge is null — "
                            + "transport contract violation; cannot correlate branch");
        }

        String correlationId = incoming.processInstanceId() + "." + ctx.nodeId();
        Set<String> stored = correlationStore.store(correlationId, edgeId, incoming);

        if (!stored.containsAll(expectedEdgeIds)) {
            // Not all branches arrived yet — ack only
            commit();
            return;
        }

        // All branches present — attempt exclusive claim
        correlationStore.claim(correlationId).ifPresentOrElse(
                claim -> {
                    Envelope merged = merge(claim.entries(), incoming);
                    publish(new EnvelopeEvent.Completion(merged, ctx.nodeId(), ctx.handlerVersion(),
                            ctx.outgoingTopics(), System.currentTimeMillis() - startedAt));
                    correlationStore.complete(claim.claimToken());
                    commit();
                },
                () -> {
                    // Another instance already claimed — just ack
                    commit();
                }
        );
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    private Envelope merge(Map<String, Envelope> branches, Envelope last) {
        List<Envelope> envelopes = List.copyOf(branches.values());
        verifyHashes(envelopes, last);
        Map<String, Object> merged = switch (mergeStrategy) {
            case ATTRIBUTE_MERGE -> attributeMerge(envelopes);
            case FIRST_WINS      -> envelopes.get(0).payload();
            case COLLECT_ALL     -> Map.of("branches",
                    envelopes.stream().map(Envelope::payload).toList());
            case DEEP_MERGE      -> deepMerge(envelopes);
        };
        // Hash the actual merge result — not the strategy name
        String mergeResultHash = ExecutionResultCanonicalizer.canonicaliseAndHash(merged);
        HashChainStep step = HashChainStep.join(
                mergeResultHash, mergeStrategy.name(), branches.size(), ctx.handlerVersion());
        return last.withPayload(merged).enterNode(step);
    }

    private void verifyHashes(List<Envelope> envelopes, Envelope last) {
        String baseHash = (String) envelopes.get(0).payload().get("_hash");
        if (envelopes.stream().anyMatch(e -> !baseHash.equals(e.payload().get("_hash")))) {
            throw new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE)
                    .with("nodeId", ctx.nodeId())
                    .with("processInstanceId", last.processInstanceId())
                    .with("reason", "Envelopes have different base hashes");
        }
    }

    private Map<String, Object> attributeMerge(List<Envelope> envelopes) {
        var merged = new HashMap<String, Object>();
        for (Envelope e : envelopes) {
            for (var entry : e.payload().entrySet()) {
                if (merged.containsKey(entry.getKey())
                        && !merged.get(entry.getKey()).equals(entry.getValue())) {
                    throw new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE)
                            .with("nodeId", ctx.nodeId())
                            .with("conflictKey", entry.getKey())
                            .with("reason", "Attribute conflict during merge");
                }
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(merged);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(List<Envelope> envelopes) {
        var merged = new HashMap<String, Object>();
        for (Envelope e : envelopes) {
            for (var entry : e.payload().entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), (existing, incoming) -> {
                    if (existing instanceof Map && incoming instanceof Map) {
                        var m = new HashMap<>((Map<String, Object>) existing);
                        m.putAll((Map<String, Object>) incoming);
                        return Map.copyOf(m);
                    }
                    if (existing.equals(incoming)) return existing;
                    throw new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE)
                            .with("nodeId", ctx.nodeId())
                            .with("conflictKey", entry.getKey())
                            .with("reason", "Deep merge conflict at leaf level");
                });
            }
        }
        return Map.copyOf(merged);
    }
}

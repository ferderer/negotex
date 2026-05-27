package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.handler.FilterHandler;
import dev.negotex.manifest.EdgeManifest;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

import java.util.List;
import java.util.Set;

public class FilterProcessor extends NodeProcessor {
    private final FilterHandler handler;

    public FilterProcessor(ProcessorContext ctx, Object handler) {
        super(ctx);
        this.handler = (FilterHandler) handler;
    }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        Set<String> selectedEdgeIds = handler.selectEdges(incoming.payload());

        // Sorted list — deterministic regardless of Set iteration order
        HashChainStep step = HashChainStep.filter(
                selectedEdgeIds.stream().sorted().toList(), ctx.handlerVersion());
        Envelope outgoing = incoming.enterNode(step);

        List<String> selectedTopics = ctx.outgoingEdges().stream()
                .filter(e -> selectedEdgeIds.contains(e.id()))
                .map(EdgeManifest::topicName).toList();
        publish(new EnvelopeEvent.Completion(outgoing, ctx.nodeId(), ctx.handlerVersion(),
                selectedTopics, System.currentTimeMillis() - startedAt));
        commit();
    }
}

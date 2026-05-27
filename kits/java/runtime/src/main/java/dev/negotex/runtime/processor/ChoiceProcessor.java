package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.error.HandlerError;
import dev.negotex.error.NegotexHandlerException;
import dev.negotex.handler.ChoiceHandler;
import dev.negotex.manifest.EdgeManifest;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

import java.util.List;

public class ChoiceProcessor extends NodeProcessor {
    private final ChoiceHandler handler;

    public ChoiceProcessor(ProcessorContext ctx, Object handler) {
        super(ctx);
        this.handler = (ChoiceHandler) handler;
    }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        String selectedEdgeId = handler.selectEdge(incoming.payload());
        String resolved = selectedEdgeId != null ? selectedEdgeId : "default";

        HashChainStep step = HashChainStep.choice(resolved, ctx.handlerVersion());
        Envelope outgoing = incoming.enterNode(step);

        String topic = resolveTopicForEdge(selectedEdgeId, outgoing);
        publish(new EnvelopeEvent.Completion(outgoing, ctx.nodeId(), ctx.handlerVersion(),
                List.of(topic), System.currentTimeMillis() - startedAt));
        commit();
    }

    private String resolveTopicForEdge(String selectedEdgeId, Envelope envelope) {
        if (selectedEdgeId == null) {
            return ctx.outgoingEdges().stream()
                    .filter(EdgeManifest::defaultEdge).findFirst()
                    .map(EdgeManifest::topicName)
                    .orElseThrow(() -> new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE)
                            .with("nodeId", ctx.nodeId())
                            .with("reason", "No default edge configured on Choice node"));
        }
        return ctx.outgoingEdges().stream()
                .filter(e -> selectedEdgeId.equals(e.id())).findFirst()
                .map(EdgeManifest::topicName)
                .orElseThrow(() -> new NegotexHandlerException(HandlerError.UNEXPECTED_FAILURE)
                        .with("nodeId", ctx.nodeId())
                        .with("selectedEdgeId", selectedEdgeId)
                        .with("reason", "Handler returned unknown edge ID"));
    }
}

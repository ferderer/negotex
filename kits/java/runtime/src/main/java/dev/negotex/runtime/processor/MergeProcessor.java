package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

public class MergeProcessor extends NodeProcessor {
    public MergeProcessor(ProcessorContext ctx) { super(ctx); }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        HashChainStep step = HashChainStep.merge(
                delivery.incomingEdge() != null ? delivery.incomingEdge() : "unknown",
                ctx.handlerVersion());
        Envelope outgoing = incoming.enterNode(step);
        publish(new EnvelopeEvent.Completion(outgoing, ctx.nodeId(), ctx.handlerVersion(),
                ctx.outgoingTopics(), System.currentTimeMillis() - startedAt));
        commit();
    }
}

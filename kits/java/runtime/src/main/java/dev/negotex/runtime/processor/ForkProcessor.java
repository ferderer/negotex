package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

public class ForkProcessor extends NodeProcessor {
    public ForkProcessor(ProcessorContext ctx) { super(ctx); }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        HashChainStep step = HashChainStep.fork(ctx.outgoingTopics(), ctx.handlerVersion());
        Envelope outgoing = incoming.enterNode(step);
        publish(new EnvelopeEvent.Completion(outgoing, ctx.nodeId(), ctx.handlerVersion(),
                ctx.outgoingTopics(), System.currentTimeMillis() - startedAt));
        commit();
    }
}

package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.ExecutionResultCanonicalizer;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.handler.TerminateHandler;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

public class TerminateProcessor extends NodeProcessor {
    private final TerminateHandler handler;

    public TerminateProcessor(ProcessorContext ctx, Object handler) {
        super(ctx);
        this.handler = handler != null ? (TerminateHandler) handler : null;
    }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        if (handler != null) handler.onTerminate(incoming.payload());

        // Hash the final payload — this is the permanent compliance record (ADR-027)
        String finalPayloadJson = ExecutionResultCanonicalizer.canonicalise(incoming.payload());
        HashChainStep step = HashChainStep.terminate(finalPayloadJson, ctx.handlerVersion());
        Envelope terminal = incoming.enterNode(step);

        publish(new EnvelopeEvent.Termination(terminal, ctx.nodeId(), ctx.handlerVersion(),
                System.currentTimeMillis() - startedAt));
        commit();
    }
}

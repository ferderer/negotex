package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.ExecutionResultCanonicalizer;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.handler.TaskHandler;
import dev.negotex.payload.PayloadExtractor;
import dev.negotex.payload.PayloadInserter;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

public class MapProcessor<I, O> extends NodeProcessor {

    private final TaskHandler<I, O> handler;
    private final PayloadExtractor<I> extractor;
    private final PayloadInserter<O> inserter;

    private MapProcessor(ProcessorContext ctx, TaskHandler<I, O> handler,
                         PayloadExtractor<I> extractor, PayloadInserter<O> inserter) {
        super(ctx);
        this.handler = handler; this.extractor = extractor; this.inserter = inserter;
    }

    @SuppressWarnings("unchecked")
    public static MapProcessor<?, ?> create(ProcessorContext ctx, Object handler,
                                            PayloadExtractor<?> extractor,
                                            PayloadInserter<?> inserter) {
        return new MapProcessor<>(ctx,
                (TaskHandler<Object, Object>) handler,
                (PayloadExtractor<Object>) extractor,
                (PayloadInserter<Object>) inserter);
    }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        I input      = extractor.extract(incoming.payload());
        O output     = handler.handle(input);
        var enriched = inserter.insert(output, incoming.payload());

        // Canonical JSON of the handler output — deterministic via sorted map keys
        String executionResult = ExecutionResultCanonicalizer.canonicalise(output);
        HashChainStep step = new HashChainStep(executionResult, ctx.handlerVersion());

        Envelope outgoing = incoming.withPayload(enriched).enterNode(step);
        publish(new EnvelopeEvent.Completion(outgoing, ctx.nodeId(), ctx.handlerVersion(),
                ctx.outgoingTopics(), System.currentTimeMillis() - startedAt));
        commit();
    }
}

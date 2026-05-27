package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.ExecutionResultCanonicalizer;
import dev.negotex.envelope.HashChainStep;
import dev.negotex.handler.WaitHandler;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.state.CorrelationStore;
import dev.negotex.transport.DeliveryContext;
import dev.negotex.transport.StatefulProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processor for Wait nodes — suspends and resumes process instances (ADR-013).
 *
 * <p>Suspend protocol (store before notify, cleanup on notify failure):
 * <ol>
 *   <li>store(taskId, taskId, envelope)</li>
 *   <li>onSuspend() — if throws: claim+complete to clean up, re-throw</li>
 *   <li>publish(Suspension)</li>
 *   <li>commit()</li>
 * </ol>
 *
 * <p>Resume protocol (claim before onResume, complete after publish):
 * <ol>
 *   <li>claim(taskId)</li>
 *   <li>onResume() — if throws: re-throw, claim expires via TTL</li>
 *   <li>publish(Completion)</li>
 *   <li>complete(claimToken)</li>
 * </ol>
 */
public class WaitProcessor extends NodeProcessor implements StatefulProcessor {

    private static final Logger log = LoggerFactory.getLogger(WaitProcessor.class);

    private final WaitHandler handler;
    private final CorrelationStore correlationStore;

    public WaitProcessor(ProcessorContext ctx,
                         Object handler,
                         CorrelationStore correlationStore) {
        super(ctx);
        this.handler          = (WaitHandler) handler;
        this.correlationStore = correlationStore;
    }

    // ── Suspend path ──────────────────────────────────────────────────────────

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        String taskId = UUID.randomUUID().toString();

        correlationStore.store(taskId, taskId, incoming);

        try {
            handler.onSuspend(taskId, incoming.payload());
        } catch (Exception e) {
            // onSuspend failed — clean up parked state
            correlationStore.claim(taskId)
                    .ifPresent(claim -> correlationStore.complete(claim.claimToken()));
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }

        publish(new EnvelopeEvent.Suspension(
                incoming.enterNode(HashChainStep.waitSuspend(taskId, ctx.handlerVersion())),
                ctx.nodeId(), ctx.handlerVersion(), taskId,
                System.currentTimeMillis() - startedAt));
        commit();
    }

    // ── Resume path ───────────────────────────────────────────────────────────

    /**
     * Resume a suspended process instance.
     * Called by the task completion REST endpoint.
     */
    public void resume(String taskId, Map<String, Object> externalData) {
        correlationStore.claim(taskId).ifPresentOrElse(
                claim -> {
                    Envelope parked = claim.entries().get(taskId);
                    if (parked == null) {
                        log.warn("Claim for taskId {} had no envelope", taskId);
                        correlationStore.complete(claim.claimToken());
                        return;
                    }

                    Map<String, Object> enriched = handler.onResume(taskId, externalData);
                    var newPayload = new HashMap<>(parked.payload());
                    newPayload.putAll(enriched);

                    // Hash the actual resume delta — canonical and deterministic
                    String resumeResultJson = ExecutionResultCanonicalizer.canonicalise(enriched);
                    HashChainStep step = HashChainStep.waitResume(
                            taskId, resumeResultJson, ctx.handlerVersion());
                    Envelope resumed = parked.withPayload(Map.copyOf(newPayload)).enterNode(step);

                    ctx.publisher().publish(new EnvelopeEvent.Completion(
                            resumed, ctx.nodeId(), ctx.handlerVersion(), ctx.outgoingTopics(), 0L));

                    correlationStore.complete(claim.claimToken());
                },
                () -> log.warn("Resume attempted for unknown or already-claimed taskId: {}",
                        taskId)
        );
    }
}

package dev.negotex.runtime.processor;

import dev.negotex.envelope.Envelope;
import dev.negotex.envelope.ExecutionResultCanonicalizer;
import dev.negotex.handler.TriggerHandler;
import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.transport.DeliveryContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Processor for Trigger nodes — payload-creating strategy (ADR-014).
 * Activated externally via {@link #trigger} — does not receive transport messages.
 */
public class TriggerProcessor extends NodeProcessor {

    private final TriggerHandler handler;
    private final String processDefinitionId;
    private final String processVersion;

    public TriggerProcessor(ProcessorContext ctx, Object handler,
                            String processDefinitionId, String processVersion) {
        super(ctx);
        this.handler             = (TriggerHandler) handler;
        this.processDefinitionId = processDefinitionId;
        this.processVersion      = processVersion;
    }

    @Override
    protected void process(Envelope incoming, DeliveryContext delivery, long startedAt) {
        throw new UnsupportedOperationException(
                "TriggerProcessor receives no incoming envelopes. Use trigger() instead.");
    }

    public void trigger(Object externalEvent) {
        long startedAt = System.currentTimeMillis();

        Map<String, Object> initialPayload = handler.createPayload(externalEvent);

        // _hash: canonical SHA-256 of the initial payload — stable across JVM restarts.
        // Uses ExecutionResultCanonicalizer: sorted map keys at all levels, stable types.
        String payloadJson = ExecutionResultCanonicalizer.canonicalise(initialPayload);
        String payloadHash = sha256(payloadJson);

        var payload = new HashMap<>(initialPayload);
        payload.put("_hash", payloadHash);

        Instant now = Instant.now();

        // Genesis hash per ADR-028: SHA-256("GENESIS§" + payloadHash + "§" + timestamp + "§" + processVersion)
        // Uses § delimiter (same as Envelope.advanceHash) and explicit UTF-8.
        String genesisHash = sha256("GENESIS§" + payloadHash + "§" + now + "§" + processVersion);

        Envelope envelope = new Envelope(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                processDefinitionId, processVersion, genesisHash,
                now, now, Map.copyOf(payload));

        ctx.publisher().publish(new EnvelopeEvent.Completion(
                envelope, ctx.nodeId(), ctx.handlerVersion(), ctx.outgoingTopics(),
                System.currentTimeMillis() - startedAt));
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

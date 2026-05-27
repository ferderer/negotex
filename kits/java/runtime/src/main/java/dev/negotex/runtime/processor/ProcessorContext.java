package dev.negotex.runtime.processor;

import dev.negotex.manifest.EdgeManifest;
import dev.negotex.publisher.Publisher;
import dev.negotex.transport.EnvelopeTransport;

import java.util.List;

/**
 * Common context shared by all nine node processors.
 *
 * <p>Built once per node by {@link ProcessorStarter} from the
 * {@link dev.negotex.manifest.RuntimeManifest}.
 *
 * @param nodeId         the node identifier
 * @param handlerVersion version string for hash-chain steps (ADR-028).
 *                       For user handlers: {@code HandlerManifest.handlerVersion()}.
 *                       For built-in nodes (Fork, Merge, Join, Wait, Terminate):
 *                       a {@code builtin:<type>@<runtimeVersion>} string supplied
 *                       by {@link ProcessorStarter}.
 * @param outgoingEdges  all outgoing edges including topic names and optional
 *                       conditions (for Choice and Filter nodes)
 * @param publisher      the single outbound interface for all events
 * @param transport      the transport, used for retry re-queuing
 * @param maxRetries     maximum retry attempts before DLQ routing
 */
public record ProcessorContext(
        String nodeId,
        String handlerVersion,
        List<EdgeManifest> outgoingEdges,
        Publisher publisher,
        EnvelopeTransport transport,
        int maxRetries
) {
    public ProcessorContext {
        outgoingEdges = List.copyOf(outgoingEdges);
    }

    /** Outgoing topic names — convenience for unconditional-forward processors. */
    public List<String> outgoingTopics() {
        return outgoingEdges.stream()
            .map(EdgeManifest::topicName)
            .toList();
    }
}

package dev.negotex.runtime.processor;

import dev.negotex.manifest.EdgeManifest;
import dev.negotex.manifest.NodeManifest;
import dev.negotex.manifest.RuntimeManifest;
import dev.negotex.publisher.Publisher;
import dev.negotex.runtime.handler.ExtractorResolver;
import dev.negotex.runtime.handler.HandlerLoader;
import dev.negotex.state.CorrelationStore;
import dev.negotex.transport.EnvelopeTransport;
import dev.negotex.transport.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProcessorStarter implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ProcessorStarter.class);

    private final RuntimeManifest manifest;
    private final EnvelopeTransport transport;
    private final Publisher publisher;
    private final HandlerLoader handlerLoader;
    private final ExtractorResolver extractorResolver;
    private final CorrelationStore correlationStore;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private volatile boolean running = false;

    public ProcessorStarter(RuntimeManifest manifest,
                            EnvelopeTransport transport,
                            Publisher publisher,
                            HandlerLoader handlerLoader,
                            ExtractorResolver extractorResolver,
                            CorrelationStore correlationStore) {
        this.manifest          = manifest;
        this.transport         = transport;
        this.publisher         = publisher;
        this.handlerLoader     = handlerLoader;
        this.extractorResolver = extractorResolver;
        this.correlationStore  = correlationStore;
    }

    @Override
    public void start() {
        log.info("Starting node processors for runtime instance '{}'",
                manifest.runtimeInstance().name());

        for (String nodeId : manifest.assignedNodeIds()) {
            NodeManifest node = manifest.node(nodeId);
            ProcessorContext ctx = buildContext(node);
            NodeProcessor processor = buildProcessor(node, ctx);

            String consumerGroup = "%s-v%s.%s".formatted(
                    manifest.processId(), manifest.processVersion(), nodeId);

            for (String topic : resolveIncomingTopics(node)) {
                Subscription sub = transport.subscribe(topic, consumerGroup, processor);
                subscriptions.add(sub);
                log.info("Subscribed node '{}' to topic '{}'", nodeId, topic);
            }
        }

        running = true;
        log.info("All {} subscription(s) active", subscriptions.size());
    }

    @Override
    public void stop() {
        log.info("Cancelling {} subscription(s)", subscriptions.size());
        subscriptions.forEach(Subscription::cancel);
        running = false;
    }

    @Override
    public boolean isRunning() { return running; }

    private ProcessorContext buildContext(NodeManifest node) {
        int maxRetries = node.maxRetries() != null
                ? node.maxRetries()
                : manifest.defaultMaxRetries();
        return new ProcessorContext(node.id(), resolveHandlerVersion(node),
                resolveOutgoingEdges(node), publisher, transport, maxRetries);
    }

    /**
     * Resolve the handler version string for ADR-028 hash-chain steps.
     *
     * <p>For user handler nodes: uses {@code HandlerManifest.handlerVersion()}.
     * For built-in nodes (Fork, Merge, Join, Wait, Terminate): uses a stable
     * {@code builtin:<type>@<runtimeVersion>} string derived from this JAR's
     * Implementation-Version manifest attribute.
     */
    private String resolveHandlerVersion(NodeManifest node) {
        if (node.handler() != null && node.handler().handlerVersion() != null) {
            return node.handler().handlerVersion();
        }
        return "builtin:" + node.type().name().toLowerCase() + "@" + RUNTIME_VERSION;
    }

    /** Runtime kit version — read once from the JAR manifest, fallback to "dev". */
    private static final String RUNTIME_VERSION = resolveRuntimeVersion();

    private static String resolveRuntimeVersion() {
        String v = ProcessorStarter.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    private NodeProcessor buildProcessor(NodeManifest node, ProcessorContext ctx) {
        return switch (node.type()) {
            case MAP -> {
                var handler   = handlerLoader.load(node);
                var extractor = extractorResolver.resolveExtractor(handler.getClass());
                var inserter  = extractorResolver.resolveInserter(handler.getClass());
                yield MapProcessor.create(ctx, handler, extractor, inserter);
            }
            case FORK     -> new ForkProcessor(ctx);
            case MERGE    -> new MergeProcessor(ctx);
            case CHOICE   -> new ChoiceProcessor(ctx, handlerLoader.load(node));
            case FILTER   -> new FilterProcessor(ctx, handlerLoader.load(node));
            case JOIN     -> new JoinProcessor(ctx,
                    node.expectedBranches(),
                    node.mergeStrategy(),
                    correlationStore,
                    resolveIncomingEdgeIds(node));
            case WAIT     -> new WaitProcessor(ctx,
                    handlerLoader.load(node),
                    correlationStore);
            case TRIGGER  -> new TriggerProcessor(ctx,
                    handlerLoader.load(node),
                    manifest.processId(),
                    manifest.processVersion());
            case TERMINATE -> new TerminateProcessor(ctx,
                    node.handler() != null ? handlerLoader.load(node) : null);
        };
    }

    private List<String> resolveIncomingTopics(NodeManifest node) {
        return manifest.edges().values().stream()
                .filter(e -> node.id().equals(e.targetNodeId()))
                .map(EdgeManifest::topicName)
                .toList();
    }

    private List<EdgeManifest> resolveOutgoingEdges(NodeManifest node) {
        return manifest.edges().values().stream()
                .filter(e -> node.id().equals(e.sourceNodeId()))
                .toList();
    }

    /**
     * Incoming edge identifiers for Join nodes — the expected set of branches.
     *
     * <p>Must produce values matching what the transport injects as
     * {@code _incomingEdge}: the topic suffix after {@code .edge.}
     * ({@code sourceNodeId-to-targetNodeId}). Mirrors
     * {@code KafkaEnvelopeTransport#edgeIdFromTopic}.
     */
    private Set<String> resolveIncomingEdgeIds(NodeManifest node) {
        return manifest.edges().values().stream()
                .filter(e -> node.id().equals(e.targetNodeId()))
                .map(e -> edgeIdFromTopic(e.topicName()))
                .collect(Collectors.toSet());
    }

    private static String edgeIdFromTopic(String topicName) {
        int idx = topicName == null ? -1 : topicName.lastIndexOf(".edge.");
        return idx >= 0 ? topicName.substring(idx + 6) : topicName;
    }
}

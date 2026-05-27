package dev.negotex.runtime.handler;

import dev.negotex.error.NegotexException;
import dev.negotex.error.NegotexRuntimeError;
import dev.negotex.manifest.HandlerMode;
import dev.negotex.manifest.NodeManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Loads and instantiates handler classes from the manifest at startup.
 *
 * <p>Instantiation strategy per {@link HandlerMode}:
 * <ul>
 *   <li>{@link HandlerMode#PURE} — reflective no-arg constructor.
 *       No Spring involvement. The handler structurally cannot receive
 *       injected dependencies.</li>
 *   <li>{@link HandlerMode#DIRTY} — Spring's {@code AutowireCapableBeanFactory}.
 *       Constructor dependencies are injected by type from the application
 *       context. The handler JAR must provide a {@code @Configuration} class
 *       that defines its dependencies as Spring beans.</li>
 * </ul>
 *
 * @see ExtractorResolver for extractor/inserter loading
 */
@Component
public class HandlerLoader {

    private static final Logger log = LoggerFactory.getLogger(HandlerLoader.class);

    private final ApplicationContext applicationContext;

    public HandlerLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Load and instantiate the handler for the given node manifest.
     *
     * @param node the node manifest containing handler class name and mode
     * @return the instantiated handler object
     * @throws NegotexException with {@link NegotexRuntimeError#HANDLER_CLASS_NOT_FOUND}
     *                          if the handler class is not on the classpath
     * @throws NegotexException with {@link NegotexRuntimeError#DIRTY_HANDLER_NO_GOVERNANCE}
     *                          if a DIRTY handler has no governance policy
     * @throws NegotexException with {@link NegotexRuntimeError#HANDLER_INSTANTIATION_FAILED}
     *                          if the handler cannot be instantiated
     */
    public Object load(NodeManifest node) {
        validateGovernance(node);

        Class<?> handlerClass = loadClass(node.handler().handlerClass());

        log.info("Loading handler {} for node {} (mode: {})",
                handlerClass.getSimpleName(), node.id(), node.mode());

        return switch (node.mode()) {
            case PURE  -> instantiatePure(handlerClass, node.id());
            case DIRTY -> instantiateDirty(handlerClass, node.id());
        };
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateGovernance(NodeManifest node) {
        if (node.mode() == HandlerMode.DIRTY && node.governance() == null) {
            throw new NegotexException(NegotexRuntimeError.DIRTY_HANDLER_NO_GOVERNANCE)
                .with("nodeId", node.id())
                .with("handler", node.handler().handlerClass());
        }
    }

    // ── Class loading ─────────────────────────────────────────────────────────

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new NegotexException(NegotexRuntimeError.HANDLER_CLASS_NOT_FOUND, e)
                .with("className", className);
        }
    }

    // ── PURE instantiation ────────────────────────────────────────────────────

    private Object instantiatePure(Class<?> handlerClass, String nodeId) {
        try {
            return handlerClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new NegotexException(NegotexRuntimeError.HANDLER_INSTANTIATION_FAILED, e)
                .with("nodeId", nodeId)
                .with("handler", handlerClass.getName())
                .with("reason", "PURE handler requires a public no-arg constructor");
        } catch (ReflectiveOperationException e) {
            throw new NegotexException(NegotexRuntimeError.HANDLER_INSTANTIATION_FAILED, e)
                .with("nodeId", nodeId)
                .with("handler", handlerClass.getName())
                .with("cause", e.getMessage());
        }
    }

    // ── DIRTY instantiation ───────────────────────────────────────────────────

    private Object instantiateDirty(Class<?> handlerClass, String nodeId) {
        try {
            return applicationContext
                .getAutowireCapableBeanFactory()
                .createBean(handlerClass);
        }
        catch (IllegalStateException | BeansException e) {
            throw new NegotexException(NegotexRuntimeError.HANDLER_INSTANTIATION_FAILED, e)
                .with("nodeId", nodeId)
                .with("handler", handlerClass.getName())
                .with("reason", "DIRTY handler instantiation failed — check @Configuration beans in handler JAR")
                .with("cause", e.getMessage());
        }
    }
}

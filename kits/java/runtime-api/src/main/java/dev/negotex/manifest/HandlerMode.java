package dev.negotex.manifest;

/**
 * Handler execution mode (ADR-005).
 *
 * <p>{@link #PURE} — no-arg constructor, no injected dependencies.
 * The runtime instantiates via reflection. Structurally guarantees
 * no side effects. Implies {@link ConsistencyContract#DETERMINISTIC}.
 *
 * <p>{@link #DIRTY} — constructor dependencies injected by Spring
 * ({@code AutowireCapableBeanFactory.createBean()}). Requires a
 * {@link GovernancePolicy} in the process definition.
 */
public enum HandlerMode {
    PURE,
    DIRTY
}

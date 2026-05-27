package dev.negotex.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Handler reference and generated class names for a node.
 *
 * <p>The compiler resolves the handler class, verifies it exists on the
 * declared runtime's classpath, and records the generated extractor and
 * inserter class names (produced by the annotation processor).
 *
 * <p>The runtime uses {@code handlerClass} to instantiate the handler
 * (via reflection for {@link HandlerMode#PURE}, via Spring for
 * {@link HandlerMode#DIRTY}), and {@code extractorClass} /
 * {@code inserterClass} to wire up the {@code HandlerRegistry} entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HandlerManifest(

        /**
         * Fully qualified handler class name.
         * Java:  {@code dev.acme.loan.CreditCheckHandler}
         * F#:    {@code Acme.Risk.RiskCalculation.evaluate}
         * Rust:  {@code acme_risk::risk::evaluate}
         */
        String handlerClass,

        /**
         * Handler version — tracked in the hash chain (ADR-028).
         * Must be bumped on every logic change, even if the process
         * definition version does not change.
         */
        String handlerVersion
) {}

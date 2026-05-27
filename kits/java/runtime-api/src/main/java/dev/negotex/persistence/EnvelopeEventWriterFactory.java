package dev.negotex.persistence;

import dev.negotex.manifest.InfrastructureManifest;

/**
 * Factory for creating an {@link EnvelopeEventWriter} instance at startup.
 *
 * <p>Persistence modules (e.g. {@code negotex-persistence-timescale},
 * {@code negotex-persistence-noop}) each provide exactly one
 * implementation of this interface as a Spring {@code @Service}.
 *
 * <p>The runtime injects this factory and calls {@link #create} once
 * during startup — no persistence-specific code lives in the runtime itself.
 *
 * <p>Only one implementation may be present on the classpath at a time.
 * Spring will throw {@code NoUniqueBeanDefinitionException} if multiple
 * persistence modules are active simultaneously.
 *
 * @see EnvelopeEventWriter
 */
public interface EnvelopeEventWriterFactory {

    /**
     * Create and initialise the event writer.
     *
     * @param infrastructure infrastructure endpoints from the Runtime Manifest
     * @return a ready-to-use {@link EnvelopeEventWriter}
     */
    EnvelopeEventWriter create(InfrastructureManifest infrastructure);
}

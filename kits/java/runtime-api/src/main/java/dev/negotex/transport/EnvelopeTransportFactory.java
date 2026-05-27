package dev.negotex.transport;

import dev.negotex.manifest.InfrastructureManifest;

/**
 * Factory for creating an {@link EnvelopeTransport} instance at startup.
 *
 * <p>Transport modules (e.g. {@code negotex-transport-kafka},
 * {@code negotex-transport-inprocess}) each provide exactly one
 * implementation of this interface as a Spring {@code @Service}.
 *
 * <p>The runtime injects this factory and calls {@link #create} once
 * during startup — no transport-specific code lives in the runtime itself.
 *
 * <p>Only one implementation may be present on the classpath at a time.
 * Spring will throw {@code NoUniqueBeanDefinitionException} if multiple
 * transport modules are active simultaneously, forcing the operator
 * to choose exactly one.
 *
 * @see EnvelopeTransport
 */
public interface EnvelopeTransportFactory {

    /**
     * Create and initialise the transport.
     *
     * @param infrastructure infrastructure endpoints from the Runtime Manifest
     * @return a ready-to-use {@link EnvelopeTransport}
     */
    EnvelopeTransport create(InfrastructureManifest infrastructure);
}

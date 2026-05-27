package dev.negotex.state;

import dev.negotex.manifest.InfrastructureManifest;

/**
 * Factory for creating a {@link CorrelationStore} instance at startup.
 *
 * <p>Correlation store modules (e.g. {@code negotex-correlation-valkey},
 * {@code negotex-correlation-inmemory}) each provide exactly one
 * implementation as a Spring {@code @Service}.
 *
 * <p>Only one implementation may be present on the classpath at a time.
 */
public interface CorrelationStoreFactory {

    /**
     * Create and initialise the correlation store.
     *
     * @param infrastructure infrastructure endpoints from the Runtime Manifest
     * @return a ready-to-use {@link CorrelationStore}
     */
    CorrelationStore create(InfrastructureManifest infrastructure);
}

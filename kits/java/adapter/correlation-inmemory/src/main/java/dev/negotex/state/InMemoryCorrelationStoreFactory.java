package dev.negotex.state;

import dev.negotex.manifest.InfrastructureManifest;
import org.springframework.stereotype.Service;

/**
 * Factory for {@link InMemoryCorrelationStore}.
 */
@Service
public class InMemoryCorrelationStoreFactory implements CorrelationStoreFactory {

    private final InMemoryCorrelationStore store = new InMemoryCorrelationStore();

    @Override
    public CorrelationStore create(InfrastructureManifest infrastructure) {
        return store;
    }
}

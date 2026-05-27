package dev.negotex.persistence;

import dev.negotex.manifest.InfrastructureManifest;
import org.springframework.stereotype.Service;

/**
 * Factory for {@link NoOpEnvelopeEventWriter}.
 */
@Service
public class NoOpEnvelopeEventWriterFactory implements EnvelopeEventWriterFactory {

    @Override
    public EnvelopeEventWriter create(InfrastructureManifest infrastructure) {
        return new NoOpEnvelopeEventWriter();
    }
}

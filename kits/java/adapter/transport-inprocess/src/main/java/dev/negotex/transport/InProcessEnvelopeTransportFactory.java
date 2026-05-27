package dev.negotex.transport;

import dev.negotex.manifest.InfrastructureManifest;
import org.springframework.stereotype.Service;

/**
 * Factory for {@link InProcessEnvelopeTransport}.
 *
 * <p>No dependency on {@link dev.negotex.publisher.Publisher} — no bean cycle.
 */
@Service
public class InProcessEnvelopeTransportFactory implements EnvelopeTransportFactory {

    @Override
    public EnvelopeTransport create(InfrastructureManifest infrastructure) {
        return new InProcessEnvelopeTransport();
    }
}

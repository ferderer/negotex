package dev.negotex.transport;

import dev.negotex.manifest.InfrastructureManifest;
import org.springframework.stereotype.Service;

/**
 * Factory for {@link KafkaEnvelopeTransport}.
 *
 * <p>No dependency on {@link dev.negotex.publisher.Publisher} — the transport
 * owns its own ack lifecycle via a ThreadLocal. No bean cycle.
 */
@Service
public class KafkaEnvelopeTransportFactory implements EnvelopeTransportFactory {

    @Override
    public EnvelopeTransport create(InfrastructureManifest infrastructure) {
        return new KafkaEnvelopeTransport(
                infrastructure.kafkaBrokers(),
                new EnvelopeSerializer());
    }
}

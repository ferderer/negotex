package dev.negotex.runtime.publisher;

import dev.negotex.persistence.EnvelopeEvent;
import dev.negotex.persistence.EnvelopeEventWriter;
import dev.negotex.publisher.Publisher;
import dev.negotex.transport.EnvelopeTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link Publisher} implementation.
 *
 * <p>Routing and persistence only — ack lifecycle is owned by the transport.
 * {@link #commit()} delegates directly to {@link EnvelopeTransport#commit()}.
 */
@Component
public class DefaultPublisher implements Publisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultPublisher.class);

    private final EnvelopeTransport transport;
    private final EnvelopeEventWriter eventWriter;

    public DefaultPublisher(EnvelopeTransport transport,
                            EnvelopeEventWriter eventWriter) {
        this.transport   = transport;
        this.eventWriter = eventWriter;
    }

    @Override
    public void publish(EnvelopeEvent event) {
        switch (event) {
            case EnvelopeEvent.Completion c -> {
                for (String topic : c.targetTopics()) {
                    log.debug("Publishing envelope {} from node {} to topic {}",
                            c.envelope().envelopeId(), c.nodeId(), topic);
                    transport.send(c.envelope(), topic);
                }
                eventWriter.write(c);
            }
            case EnvelopeEvent.Failure f -> {
                log.debug("Recording failure for envelope {} at node {} — outcome: {}",
                        f.envelope().envelopeId(), f.nodeId(), f.outcome());
                eventWriter.write(f);
            }
            case EnvelopeEvent.Termination t -> {
                log.debug("Recording termination for envelope {} at node {}",
                        t.envelope().envelopeId(), t.terminalNodeId());
                eventWriter.write(t);
            }
            case EnvelopeEvent.Suspension s -> {
                log.debug("Recording suspension for envelope {} at node {} taskId={}",
                        s.envelope().envelopeId(), s.nodeId(), s.taskId());
                eventWriter.write(s);
            }
        }
    }

    @Override
    public void commit() {
        transport.commit();
    }
}

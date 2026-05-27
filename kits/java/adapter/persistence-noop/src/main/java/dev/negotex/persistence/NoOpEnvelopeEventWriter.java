package dev.negotex.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op {@link EnvelopeEventWriter} — logs at DEBUG, no database.
 *
 * <p>For PoC deployments and CI pipelines where audit persistence
 * is not required. Active when {@code negotex-persistence-noop}
 * is on the classpath.
 *
 * <p>Instantiated by {@link NoOpEnvelopeEventWriterFactory}. Not a
 * Spring bean itself — the factory is the bean.
 */
public class NoOpEnvelopeEventWriter implements EnvelopeEventWriter {

    private static final Logger log = LoggerFactory.getLogger(NoOpEnvelopeEventWriter.class);

    @Override
    public void write(EnvelopeEvent event) {
        switch (event) {
            case EnvelopeEvent.Completion c -> log.debug(
                    "[COMPLETION] envelope={} node={} duration={}ms targets={}",
                    c.envelope().envelopeId(), c.nodeId(),
                    c.durationMs(), c.targetTopics());

            case EnvelopeEvent.Failure f -> log.debug(
                    "[FAILURE] envelope={} node={} error={} retry={}/{} outcome={}",
                    f.envelope().envelopeId(), f.nodeId(),
                    f.error().getMessage(), f.retryCount(),
                    f.maxRetries(), f.outcome());

            case EnvelopeEvent.Termination t -> log.debug(
                    "[TERMINATION] envelope={} node={} duration={}ms",
                    t.envelope().envelopeId(), t.terminalNodeId(), t.durationMs());

            case EnvelopeEvent.Suspension s -> log.debug(
                    "[SUSPENSION] envelope={} node={} taskId={}",
                    s.envelope().envelopeId(), s.nodeId(), s.taskId());
        }
    }
}

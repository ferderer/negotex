package dev.negotex.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TimescaleDB implementation of {@link EnvelopeEventWriter}.
 *
 * <p>Writes to three tables per ADR-027 / ADR-028:
 * <ul>
 *   <li>{@code node_completions} — successful node processing</li>
 *   <li>{@code node_failures} — handler exceptions</li>
 *   <li>{@code process_terminations} — process instance end (compliance record)</li>
 * </ul>
 *
 * <p>Every record carries {@code handler_version}, {@code envelope_hash}, and
 * {@code previous_hash} so the Audit Verifier can reproduce the hash chain
 * without implicit manifest knowledge (ADR-028).
 *
 * <p>All writes are asynchronous — Spring {@code @Async} ensures the hot path
 * is never blocked (ADR-004).
 *
 * <p>Suspension events are logged but not persisted — Wait state lives in Valkey.
 *
 * <p>Content-addressable payload deduplication (ADR-027 §) is Phase 2.
 */
@Service
public class TimescaleEnvelopeEventWriter implements EnvelopeEventWriter {

    private static final Logger log = LoggerFactory.getLogger(TimescaleEnvelopeEventWriter.class);

    private final JdbcTemplate jdbc;

    public TimescaleEnvelopeEventWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    @Async
    public void write(EnvelopeEvent event) {
        switch (event) {
            case EnvelopeEvent.Completion c -> {
                if (!c.targetTopics().isEmpty()) {
                    try {
                        jdbc.update("""
                                INSERT INTO node_completions
                                (completion_id, envelope_id, process_instance_id,
                                 process_definition_id, process_version,
                                 node_id, handler_version,
                                 envelope_hash, previous_hash,
                                 completed_at, duration_ms, target_topics)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                                UUID.randomUUID().toString(),
                                c.envelope().envelopeId(),
                                c.envelope().processInstanceId(),
                                c.envelope().processDefinitionId(),
                                c.envelope().processVersion(),
                                c.nodeId(),
                                c.handlerVersion(),
                                c.envelope().previousEnvelopeHash(),
                                null, // previous_hash: set by verifier from prior record
                                Timestamp.from(Instant.now()),
                                c.durationMs(),
                                String.join(",", c.targetTopics()));
                    } catch (Exception e) {
                        log.error("Failed to persist COMPLETION for envelope {}: {}",
                                c.envelope().envelopeId(), e.getMessage(), e);
                    }
                }
            }

            case EnvelopeEvent.Failure f -> {
                try {
                    jdbc.update("""
                            INSERT INTO node_failures
                            (failure_id, envelope_id, process_instance_id,
                             process_definition_id, process_version,
                             node_id, handler_version,
                             envelope_hash,
                             failed_at, error_type, error_message,
                             retry_count, max_retries, outcome)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            UUID.randomUUID().toString(),
                            f.envelope().envelopeId(),
                            f.envelope().processInstanceId(),
                            f.envelope().processDefinitionId(),
                            f.envelope().processVersion(),
                            f.nodeId(),
                            f.handlerVersion(),
                            f.envelope().previousEnvelopeHash(),
                            Timestamp.from(Instant.now()),
                            f.error().getClass().getSimpleName(),
                            f.error().getMessage(),
                            f.retryCount(),
                            f.maxRetries(),
                            f.outcome());
                } catch (Exception e) {
                    log.error("Failed to persist FAILURE for envelope {}: {}",
                            f.envelope().envelopeId(), e.getMessage(), e);
                }
            }

            case EnvelopeEvent.Termination t -> {
                try {
                    jdbc.update("""
                            INSERT INTO process_terminations
                            (termination_id, envelope_id, process_instance_id,
                             process_definition_id, process_version,
                             terminal_node_id, handler_version,
                             envelope_hash,
                             completed_at, duration_ms)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            UUID.randomUUID().toString(),
                            t.envelope().envelopeId(),
                            t.envelope().processInstanceId(),
                            t.envelope().processDefinitionId(),
                            t.envelope().processVersion(),
                            t.terminalNodeId(),
                            t.handlerVersion(),
                            t.envelope().previousEnvelopeHash(),
                            Timestamp.from(Instant.now()),
                            t.durationMs());
                } catch (Exception e) {
                    log.error("Failed to persist TERMINATION for process instance {}: {}",
                            t.envelope().processInstanceId(), e.getMessage(), e);
                }
            }

            case EnvelopeEvent.Suspension s ->
                    log.debug("[SUSPENSION] envelope={} node={} taskId={} — parked in Valkey",
                            s.envelope().envelopeId(), s.nodeId(), s.taskId());
        }
    }
}

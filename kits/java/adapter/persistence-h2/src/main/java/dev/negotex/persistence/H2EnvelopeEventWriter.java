package dev.negotex.persistence;

import dev.negotex.manifest.InfrastructureManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * H2-backed {@link EnvelopeEventWriter} for testing and local development.
 *
 * <p>Schema mirrors the ADR-028-compliant Timescale schema:
 * {@code handler_version} and {@code envelope_hash} are present on all tables
 * so integration tests can validate the full audit chain without a live
 * TimescaleDB instance.
 *
 * <p>Schema is initialised automatically at startup. Data is lost on JVM
 * restart when using H2 in-memory mode.
 */
@Service
public class H2EnvelopeEventWriter implements EnvelopeEventWriter {

    private static final Logger log = LoggerFactory.getLogger(H2EnvelopeEventWriter.class);

    private final JdbcTemplate jdbc;

    public H2EnvelopeEventWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        initSchema();
    }

    @Override
    public void write(EnvelopeEvent event) {
        switch (event) {
            case EnvelopeEvent.Completion c -> {
                if (!c.targetTopics().isEmpty()) {
                    jdbc.update("""
                            INSERT INTO node_completions
                            (completion_id, envelope_id, process_instance_id,
                             process_definition_id, process_version,
                             node_id, handler_version,
                             envelope_hash,
                             completed_at, duration_ms, target_topics)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            UUID.randomUUID().toString(),
                            c.envelope().envelopeId(),
                            c.envelope().processInstanceId(),
                            c.envelope().processDefinitionId(),
                            c.envelope().processVersion(),
                            c.nodeId(),
                            c.handlerVersion(),
                            c.envelope().previousEnvelopeHash(),
                            Timestamp.from(Instant.now()),
                            c.durationMs(),
                            String.join(",", c.targetTopics()));
                }
            }

            case EnvelopeEvent.Failure f -> jdbc.update("""
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

            case EnvelopeEvent.Termination t -> jdbc.update("""
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

            case EnvelopeEvent.Suspension s ->
                    log.debug("[SUSPENSION] envelope={} node={} taskId={}",
                            s.envelope().envelopeId(), s.nodeId(), s.taskId());
        }
    }

    private void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS node_completions (
                    completion_id         VARCHAR(36)   PRIMARY KEY,
                    envelope_id           VARCHAR(36)   NOT NULL,
                    process_instance_id   VARCHAR(36)   NOT NULL,
                    process_definition_id VARCHAR(255)  NOT NULL,
                    process_version       VARCHAR(64),
                    node_id               VARCHAR(255)  NOT NULL,
                    handler_version       VARCHAR(255)  NOT NULL,
                    envelope_hash         VARCHAR(64)   NOT NULL,
                    completed_at          TIMESTAMP     NOT NULL,
                    duration_ms           BIGINT,
                    target_topics         VARCHAR(1024)
                )""");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS node_failures (
                    failure_id            VARCHAR(36)   PRIMARY KEY,
                    envelope_id           VARCHAR(36)   NOT NULL,
                    process_instance_id   VARCHAR(36)   NOT NULL,
                    process_definition_id VARCHAR(255)  NOT NULL,
                    process_version       VARCHAR(64),
                    node_id               VARCHAR(255)  NOT NULL,
                    handler_version       VARCHAR(255)  NOT NULL,
                    envelope_hash         VARCHAR(64)   NOT NULL,
                    failed_at             TIMESTAMP     NOT NULL,
                    error_type            VARCHAR(255),
                    error_message         VARCHAR(2048),
                    retry_count           INT,
                    max_retries           INT,
                    outcome               VARCHAR(16)
                )""");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS process_terminations (
                    termination_id        VARCHAR(36)   PRIMARY KEY,
                    envelope_id           VARCHAR(36)   NOT NULL,
                    process_instance_id   VARCHAR(36)   NOT NULL,
                    process_definition_id VARCHAR(255)  NOT NULL,
                    process_version       VARCHAR(64),
                    terminal_node_id      VARCHAR(255)  NOT NULL,
                    handler_version       VARCHAR(255)  NOT NULL,
                    envelope_hash         VARCHAR(64)   NOT NULL,
                    completed_at          TIMESTAMP     NOT NULL,
                    duration_ms           BIGINT
                )""");

        log.info("H2 audit schema initialised (ADR-028 compliant)");
    }
}

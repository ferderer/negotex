package dev.negotex.manifest;

/**
 * Infrastructure endpoint configuration for a runtime instance.
 *
 * <p>All values are either direct URLs/connection strings or references
 * to environment variables / secret store paths. The runtime resolves
 * secret references at startup — secret values never appear in the
 * manifest itself (ADR-022).
 */
public record InfrastructureManifest(

        /**
         * Kafka broker addresses.
         * e.g. {@code "kafka:9092"} or {@code "broker1:9092,broker2:9092"}
         */
        String kafkaBrokers,

        /**
         * Valkey (Redis-protocol) connection URL.
         * e.g. {@code "redis://valkey:6379"}
         */
        String valkeyUrl,

        /**
         * TimescaleDB JDBC URL.
         * e.g. {@code "jdbc:postgresql://timescaledb:5432/negotex"}
         */
        String timescaleDbUrl,

        /**
         * TimescaleDB username.
         * May be a secret reference: {@code "${TIMESCALEDB_USER}"}
         */
        String timescaleDbUser,

        /**
         * TimescaleDB password reference.
         * Always a secret reference — never a literal value.
         * e.g. {@code "${TIMESCALEDB_PASSWORD}"} or {@code "vault:secret/negotex/db#password"}
         */
        String timescaleDbPasswordRef,

        /**
         * VictoriaMetrics remote-write URL.
         * e.g. {@code "http://victoriametrics:8428/api/v1/write"}
         */
        String victoriaMetricsUrl
) {}

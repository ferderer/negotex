package dev.negotex.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Infrastructure endpoint configuration in the deployment definition.
 *
 * <p>Values may be literal connection strings or environment variable
 * references. Secret values (passwords, tokens) must always use references
 * — never literals.
 *
 * <pre>{@code
 * infrastructure:
 *   kafkaBrokers: kafka:9092
 *   valkeyUrl: redis://valkey:6379
 *   timescaleDb:
 *     url: jdbc:postgresql://timescaledb:5432/negotex
 *     user: negotex
 *     passwordRef: ${TIMESCALEDB_PASSWORD}
 *   victoriaMetricsUrl: http://victoriametrics:8428/api/v1/write
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InfrastructureDefinition(

        /** Kafka broker addresses. e.g. {@code "kafka:9092"} */
        String kafkaBrokers,

        /** Valkey connection URL. e.g. {@code "redis://valkey:6379"} */
        String valkeyUrl,

        /** TimescaleDB connection details. */
        TimescaleDbDefinition timescaleDb,

        /** VictoriaMetrics remote-write URL. */
        String victoriaMetricsUrl
) {

    /**
     * TimescaleDB connection details.
     *
     * <p>{@code passwordRef} must be a secret reference — never a literal password.
     * Supported reference formats:
     *   Environment variable: {@code ${TIMESCALEDB_PASSWORD}}
     *   Vault:                {@code vault:secret/negotex/db#password}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimescaleDbDefinition(
            String url,
            String user,
            String passwordRef
    ) {}
}

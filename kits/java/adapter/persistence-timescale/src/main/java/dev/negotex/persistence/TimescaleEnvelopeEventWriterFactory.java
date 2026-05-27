package dev.negotex.persistence;

import dev.negotex.manifest.InfrastructureManifest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/**
 * Factory for {@link TimescaleEnvelopeEventWriter}.
 */
@Service
public class TimescaleEnvelopeEventWriterFactory implements EnvelopeEventWriterFactory {

    @Override
    public EnvelopeEventWriter create(InfrastructureManifest infrastructure) {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(infrastructure.timescaleDbUrl());
        ds.setUsername(infrastructure.timescaleDbUser());
        ds.setPassword(resolveSecret(infrastructure.timescaleDbPasswordRef()));
        return new TimescaleEnvelopeEventWriter(ds);
    }

    private String resolveSecret(String ref) {
        if (ref == null) return "";
        if (ref.startsWith("${") && ref.endsWith("}")) {
            String varName = ref.substring(2, ref.length() - 1);
            String value = System.getenv(varName);
            if (value == null) {
                throw new IllegalStateException(
                        "Environment variable '%s' not set (referenced as '%s')"
                                .formatted(varName, ref));
            }
            return value;
        }
        return ref;
    }
}

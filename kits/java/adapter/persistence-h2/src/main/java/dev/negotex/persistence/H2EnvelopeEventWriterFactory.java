package dev.negotex.persistence;

import dev.negotex.manifest.InfrastructureManifest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/**
 * Factory for {@link H2EnvelopeEventWriter}.
 */
@Service
public class H2EnvelopeEventWriterFactory implements EnvelopeEventWriterFactory {

    @Override
    public EnvelopeEventWriter create(InfrastructureManifest infrastructure) {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:negotex;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return new H2EnvelopeEventWriter(ds);
    }
}

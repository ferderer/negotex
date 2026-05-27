package dev.negotex.runtime.config;

import dev.negotex.manifest.RuntimeManifest;
import dev.negotex.payload.PayloadConversions;
import dev.negotex.persistence.EnvelopeEventWriter;
import dev.negotex.persistence.EnvelopeEventWriterFactory;
import dev.negotex.runtime.manifest.ManifestLoader;
import dev.negotex.runtime.payload.JacksonPayloadConversions;
import dev.negotex.state.CorrelationStore;
import dev.negotex.state.CorrelationStoreFactory;
import dev.negotex.transport.EnvelopeTransport;
import dev.negotex.transport.EnvelopeTransportFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central Spring configuration for the Negotex runtime.
 *
 * <p>Wires four startup concerns via injected factories:
 * <ol>
 *   <li>{@link RuntimeManifest} — loaded from configured path</li>
 *   <li>{@link EnvelopeTransport} — via {@link EnvelopeTransportFactory}</li>
 *   <li>{@link EnvelopeEventWriter} — via {@link EnvelopeEventWriterFactory}</li>
 *   <li>{@link CorrelationStore} — via {@link CorrelationStoreFactory}</li>
 * </ol>
 *
 * <p>No transport, persistence, or state implementation details live here.
 */
@Configuration
@EnableConfigurationProperties(NegotexProperties.class)
public class NegotexAutoConfiguration {

    @Bean
    public RuntimeManifest runtimeManifest(NegotexProperties properties, ManifestLoader loader) {
        return loader.load(properties.manifestPath());
    }

    @Bean
    public PayloadConversions payloadConversions() {
        return new JacksonPayloadConversions();
    }

    @Bean
    public EnvelopeTransport envelopeTransport(RuntimeManifest manifest, EnvelopeTransportFactory factory) {
        return factory.create(manifest.infrastructure());
    }

    @Bean
    public EnvelopeEventWriter envelopeEventWriter(RuntimeManifest manifest, EnvelopeEventWriterFactory factory) {
        return factory.create(manifest.infrastructure());
    }

    @Bean
    public CorrelationStore correlationStore(RuntimeManifest manifest, CorrelationStoreFactory factory) {
        return factory.create(manifest.infrastructure());
    }
}

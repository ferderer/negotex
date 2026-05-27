package dev.negotex.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;

/**
 * External configuration for the Negotex runtime.
 *
 * <p>Bound from environment variables or Spring properties with the
 * {@code negotex} prefix. Only the minimal set of properties needed
 * to bootstrap the runtime — everything else comes from the
 * {@code RuntimeManifest}.
 *
 * <p>Example environment variables:
 * <pre>
 * NEGOTEX_MANIFEST_PATH=/config/runtime-manifest.yaml
 * </pre>
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * negotex:
 *   manifestPath: /config/runtime-manifest.yaml
 * }</pre>
 *
 * <p>Configuration priority:
 * <ol>
 *   <li>{@code manifestPath} — required, must always be provided externally</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "negotex")
public record NegotexProperties(

        /**
         * Path to the {@code runtime-manifest.yaml} file.
         * Required — startup fails with a clear error if absent or unreadable.
         */
        Path manifestPath
) {}

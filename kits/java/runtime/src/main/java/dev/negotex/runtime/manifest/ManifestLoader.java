package dev.negotex.runtime.manifest;

import dev.negotex.error.NegotexException;
import dev.negotex.error.NegotexRuntimeError;
import dev.negotex.manifest.RuntimeManifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the {@link RuntimeManifest} from a YAML file at startup.
 *
 * <p>Called once during application context initialisation.
 * Fails fast with a clear {@link NegotexException} if the manifest
 * is absent or malformed — the runtime cannot start without it.
 */
@Component
public class ManifestLoader {

    private static final Logger log = LoggerFactory.getLogger(ManifestLoader.class);

    /**
     * Load and deserialise the {@link RuntimeManifest} from the given path.
     *
     * @param manifestPath path to {@code runtime-manifest.yaml}
     * @return the deserialised manifest
     * @throws NegotexException with {@link NegotexRuntimeError#MANIFEST_NOT_FOUND}
     *                          if the file does not exist
     * @throws NegotexException with {@link NegotexRuntimeError#MANIFEST_INVALID}
     *                          if the file cannot be parsed
     */
    public RuntimeManifest load(Path manifestPath) {
        log.info("Loading runtime manifest from {}", manifestPath);

        if (!Files.exists(manifestPath)) {
            throw new NegotexException(NegotexRuntimeError.MANIFEST_NOT_FOUND)
                    .with("path", manifestPath.toAbsolutePath().toString());
        }

        if (!Files.isReadable(manifestPath)) {
            throw new NegotexException(NegotexRuntimeError.MANIFEST_NOT_FOUND)
                    .with("path", manifestPath.toAbsolutePath().toString())
                    .with("reason", "file exists but is not readable");
        }

        try {
            RuntimeManifest manifest = RuntimeManifest.fromYaml(manifestPath);
            log.info("Loaded manifest for process {}, version {}, runtime instance {}",
                    manifest.processId(),
                    manifest.processVersion(),
                    manifest.runtimeInstance().name());
            return manifest;
        } catch (IOException e) {
            throw new NegotexException(NegotexRuntimeError.MANIFEST_INVALID, e)
                    .with("path", manifestPath.toAbsolutePath().toString())
                    .with("cause", e.getMessage());
        }
    }
}

package dev.negotex.error;

import de.ferderer.guard4j.error.Category;
import de.ferderer.guard4j.error.Level;

public enum NegotexRuntimeError implements NegotexError {

    // Manifest
    MANIFEST_NOT_FOUND("Runtime manifest file not found", Category.SYSTEM, Level.ERROR),
    MANIFEST_INVALID("Runtime manifest cannot be parsed", Category.SYSTEM, Level.ERROR),

    // Handler artifacts
    ARTIFACT_NOT_FOUND("Handler artifact not found", Category.SYSTEM, Level.ERROR),
    ARTIFACT_LOAD_FAILED("Handler artifact could not be loaded", Category.SYSTEM, Level.ERROR),

    // Handler resolution
    HANDLER_CLASS_NOT_FOUND("Handler class not found in artifacts", Category.SYSTEM, Level.ERROR),
    HANDLER_INSTANTIATION_FAILED("Handler could not be instantiated", Category.SYSTEM, Level.ERROR),
    EXTRACTOR_NOT_FOUND("Generated extractor class not found", Category.SYSTEM, Level.ERROR),
    INSERTER_NOT_FOUND("Generated inserter class not found", Category.SYSTEM, Level.ERROR),

    // Infrastructure
    KAFKA_CONNECTION_FAILED("Cannot connect to Kafka broker", Category.EXTERNAL, Level.ERROR),
    VALKEY_CONNECTION_FAILED("Cannot connect to Valkey", Category.EXTERNAL, Level.ERROR),
    TIMESCALEDB_CONNECTION_FAILED("Cannot connect to TimescaleDB", Category.EXTERNAL, Level.ERROR),

    // Validation
    MANIFEST_NODE_MISMATCH("Node in manifest not assigned to this runtime instance", Category.SYSTEM, Level.ERROR),
    DIRTY_HANDLER_NO_GOVERNANCE("DIRTY handler has no governance policy in manifest", Category.SYSTEM, Level.ERROR),
    EXTRACTOR_INSTANTIATION_FAILED("Extractor or inserter could not be instantiated", Category.SYSTEM, Level.ERROR),
    ENVELOPE_SERIALISATION_FAILED("Envelope could not be serialised for Kafka transport", Category.SYSTEM, Level.ERROR),
    ENVELOPE_DESERIALISATION_FAILED("Envelope bytes could not be deserialised — possible protocol version mismatch", Category.SYSTEM, Level.ERROR);

    private final String message;
    private final Category category;
    private final Level level;

    NegotexRuntimeError(String message, Category category, Level level) {
        this.message = message;
        this.category = category;
        this.level = level;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Category category() {
        return category;
    }

    @Override
    public Level level() {
        return level;
    }
}

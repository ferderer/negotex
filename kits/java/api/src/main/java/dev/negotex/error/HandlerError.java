package dev.negotex.error;

import de.ferderer.guard4j.error.Category;
import de.ferderer.guard4j.error.Level;

/**
 * Built-in error codes used by the Negotex processor infrastructure.
 *
 * <p>These are <em>not</em> for handler authors — handler authors define their own
 * domain-specific error enums implementing {@link NegotexError}. {@code HandlerError}
 * covers the cases the processor itself generates: unexpected exceptions, misconfiguration,
 * payload extraction failures, and infrastructure-level problems.
 *
 * <p>The processor wraps any unchecked exception that is not a
 * {@link NegotexHandlerException} in {@link #UNEXPECTED_FAILURE}.
 */
public enum HandlerError implements NegotexError {

    /**
     * An unchecked exception not thrown via {@link NegotexHandlerException}.
     * Indicates a bug in the handler. Retryable — may be transient.
     */
    UNEXPECTED_FAILURE("Unexpected handler failure", Category.SYSTEM, Level.ERROR),

    /**
     * The processor could not extract the typed input from the envelope payload.
     * Indicates a schema mismatch between the process definition and the handler.
     * Not retryable — retrying with the same payload will produce the same result.
     */
    PAYLOAD_EXTRACTION_FAILED("Failed to extract typed input from envelope payload", Category.SYSTEM, Level.ERROR),

    /**
     * No handler is registered for this node in the current runtime.
     * Indicates a deployment configuration error.
     * Not retryable.
     */
    HANDLER_NOT_FOUND("No handler registered for node", Category.SYSTEM, Level.ERROR),

    /**
     * The handler exceeded its configured execution time limit.
     * Retryable — may be a transient slowdown.
     */
    EXECUTION_TIMEOUT("Handler execution exceeded time limit", Category.SYSTEM, Level.WARN),

    /**
     * An external service call timed out.
     * Use this in the processor's built-in {@code http} plugin handler.
     * Retryable.
     */
    EXTERNAL_TIMEOUT("External service timeout", Category.EXTERNAL, Level.WARN),

    /**
     * An external service explicitly rejected the request in a non-retryable way.
     * Use this in the processor's built-in {@code http} plugin handler.
     * Not retryable.
     */
    EXTERNAL_REJECTION("External service rejected the request", Category.EXTERNAL, Level.WARN);

    private final String message;
    private final Category category;
    private final Level level;

    HandlerError(String message, Category category, Level level) {
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

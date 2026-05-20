package dev.negotex.error;

import dev.guard4j.exception.Guard4jException;

/**
 * Exception thrown by Negotex handlers to signal a handled failure.
 *
 * <p>Extends {@link Guard4jException} typed to {@link NegotexError}, giving the
 * processor direct access to all three error dimensions without casting:
 * <ul>
 *   <li>{@code error().isRetryable()} — drives {@code outcome} in {@code node_failures}</li>
 *   <li>{@code error().code()}        — written to {@code node_failures.error_type}</li>
 *   <li>{@code error().message()}     — written to {@code node_failures.error_message}</li>
 *   <li>{@code error().severity()}    — sets log level and VictoriaMetrics label</li>
 *   <li>{@code data()}                — written to {@code node_failures.error_context}</li>
 * </ul>
 *
 * <p>Usage in a handler:
 * <pre>{@code
 * throw new NegotexHandlerException(LoanError.CREDIT_BUREAU_TIMEOUT)
 *     .with("bureauId", "experian")
 *     .with("timeoutMs", 5000);
 * }</pre>
 *
 * <p>To wrap an underlying cause:
 * <pre>{@code
 * } catch (IOException e) {
 *     throw new NegotexHandlerException(LoanError.CREDIT_BUREAU_TIMEOUT, e)
 *         .with("bureauId", "experian");
 * }
 * }</pre>
 *
 * <p>Any unchecked exception that is <em>not</em> a {@code NegotexHandlerException}
 * is caught by the processor and treated as {@link HandlerError#UNEXPECTED_FAILURE}
 * — retryable, logged at ERROR.
 *
 * @see NegotexError
 * @see HandlerError
 */
public class NegotexHandlerException extends Guard4jException {

    /**
     * Create exception with the specified Negotex error code.
     *
     * @param error the error code
     * @throws NullPointerException if error is null
     */
    public NegotexHandlerException(NegotexError error) {
        super(error);
    }

    /**
     * Create exception with the specified Negotex error code and underlying cause.
     *
     * @param error the error code
     * @param cause the underlying cause
     * @throws NullPointerException if error is null
     */
    public NegotexHandlerException(NegotexError error, Throwable cause) {
        super(error, cause);
    }

    /**
     * The Negotex error code. Typed override — no cast required.
     */
    @Override
    public NegotexError error() {
        return (NegotexError) super.error();
    }

    /**
     * Add contextual data. Fluent override for return type covariance.
     *
     * @param key   the data key
     * @param value the data value (null allowed)
     * @return this exception for chaining
     * @throws NullPointerException if key is null
     */
    @Override
    public NegotexHandlerException with(String key, Object value) {
        super.with(key, value);
        return this;
    }
}

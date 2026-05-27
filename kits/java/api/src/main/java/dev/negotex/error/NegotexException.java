package dev.negotex.error;

import de.ferderer.guard4j.error.Guard4jException;

public class NegotexException extends Guard4jException {
    /**
     * Create exception with the specified Negotex error code.
     *
     * @param error the error code
     * @throws NullPointerException if error is null
     */
    public NegotexException(NegotexError error) {
        super(error);
    }

    /**
     * Create exception with the specified Negotex error code and underlying cause.
     *
     * @param error the error code
     * @param cause the underlying cause
     * @throws NullPointerException if error is null
     */
    public NegotexException(NegotexError error, Throwable cause) {
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
    public NegotexException with(String key, Object value) {
        super.with(key, value);
        return this;
    }
}

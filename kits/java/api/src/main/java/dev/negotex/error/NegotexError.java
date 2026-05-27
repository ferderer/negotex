package dev.negotex.error;

import de.ferderer.guard4j.Guard4jError;
import de.ferderer.guard4j.error.Categorizable;
import de.ferderer.guard4j.error.Leveled;

/**
 * Marker interface for all Negotex error codes.
 *
 * <p>Combines the three dimensions the processor needs to handle a handler failure:
 * <ul>
 *   <li>{@link Error} — identity: {@code name()}, {@code message()}, {@code code()}</li>
 *   <li>{@link Categorizable} — retry decision: {@code category()}, {@code isRetryable()}</li>
 *   <li>{@link Leveled} — log level and metrics label: {@code severity()}</li>
 * </ul>
 *
 * <p>Handler authors implement this interface in their own error enums:
 * <pre>{@code
 * public enum LoanError implements NegotexError {
 *     CREDIT_BUREAU_TIMEOUT("Credit bureau timeout", Category.EXTERNAL, Severity.WARN),
 *     SANCTIONS_CHECK_FAILED("Sanctions check failed", Category.SECURITY, Severity.ERROR);
 *
 *     private final String message;
 *     private final Category category;
 *     private final Severity severity;
 *
 *     LoanError(String message, Category category, Severity severity) {
 *         this.message = message;
 *         this.category = category;
 *         this.severity = severity;
 *     }
 *
 *     public String message()   { return message; }
 *     public Category category() { return category; }
 *     public Severity severity() { return severity; }
 * }
 * }</pre>
 *
 * <p>Additional dimensions ({@code Visible}, etc.) are optional — implement them
 * on the application error enum if needed. The processor checks via {@code instanceof}.
 *
 * @see NegotexException
 * @see HandlerError
 */
public interface NegotexError extends Guard4jError, Categorizable, Leveled {
}

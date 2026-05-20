package dev.negotex.payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a handler parameter or record component to a named attribute in the envelope payload.
 *
 * <p>Used in two forms:
 *
 * <p><b>Form A — single-attribute handler</b> (annotate the {@code handle()} parameter directly):
 * <pre>{@code
 * class CreditCheckHandler implements TaskHandler<LoanApplication, CreditCheckResult> {
 *     public CreditCheckResult handle(@PayloadKey("application") LoanApplication application) {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p><b>Form B — multi-attribute handler</b> (annotate fields of a {@link PayloadInput} record):
 * <pre>{@code
 * @PayloadInput
 * record CreditCheckInput(
 *     @PayloadKey("application")     LoanApplication application,
 *     @PayloadKey("customerProfile") CustomerProfile profile
 * ) {}
 *
 * class CreditCheckHandler implements TaskHandler<CreditCheckInput, CreditCheckResult> {
 *     public CreditCheckResult handle(CreditCheckInput input) { ... }
 * }
 * }</pre>
 *
 * <p>The annotation processor ({@code negotex-annotation-processor}) generates a
 * {@code PayloadExtractor} implementation at compile time. No runtime reflection is used.
 *
 * <p><b>Reserved keys:</b> keys prefixed with {@code _} are reserved for Negotex internal
 * use (e.g. {@code _hash}). The annotation processor rejects reserved keys with a
 * compile-time error.
 *
 * @see PayloadInput
 * @see PayloadOutput
 * @see PayloadExtractor
 */
@Documented
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface PayloadKey {

    /**
     * The payload attribute key to extract.
     * Must be non-empty and must not start with {@code _}.
     */
    String value();
}

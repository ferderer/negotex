package dev.negotex.payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record as a multi-attribute handler output type, or names the payload key
 * for a single-value return type.
 *
 * <p><b>Form A — single-value output</b> (annotate the {@code handle()} method):
 * <pre>{@code
 * class CreditCheckHandler implements TaskHandler<LoanApplication, CreditScore> {
 *
 *     @PayloadOutput("creditScore")
 *     public CreditScore handle(@PayloadKey("application") LoanApplication application) {
 *         ...
 *     }
 * }
 * }</pre>
 * The return value is inserted into the payload under the key {@code "creditScore"}.
 *
 * <p><b>Form B — multi-attribute output</b> (annotate a record, name each component):
 * <pre>{@code
 * @PayloadOutput
 * record CreditCheckResult(
 *     @PayloadKey("creditScore") CreditScore score,
 *     @PayloadKey("riskBand")   RiskBand riskBand,
 *     @PayloadKey("creditFlags") List<String> flags
 * ) {}
 *
 * class CreditCheckHandler implements TaskHandler<LoanApplication, CreditCheckResult> {
 *     public CreditCheckResult handle(...) { ... }
 * }
 * }</pre>
 * Each record component is inserted as a separate payload attribute. This is the
 * recommended form when a handler produces multiple outputs — it makes the additive
 * enrichment model explicit and enables compile-time key conflict detection.
 *
 * <p>Compile-time validations enforced by the annotation processor:
 * <ul>
 *   <li>Form A: {@code value()} must be non-empty and non-reserved</li>
 *   <li>Form B: must be applied to a {@code record}; all components must carry
 *       {@link PayloadKey}; no duplicate or reserved keys</li>
 *   <li>Both forms: keys must not start with {@code _} (reserved namespace)</li>
 * </ul>
 *
 * @see PayloadKey
 * @see PayloadInserter
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface PayloadOutput {

    /**
     * The payload attribute key for single-value outputs (Form A).
     * Ignored when applied to a record type (Form B).
     */
    String value() default "";
}

package dev.negotex.payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record as a multi-attribute handler input type.
 *
 * <p>Every component of the annotated record must be annotated with {@link PayloadKey}.
 * The annotation processor generates a {@link PayloadExtractor} implementation that
 * constructs the record from the envelope payload map at runtime.
 *
 * <pre>{@code
 * @PayloadInput
 * record CreditCheckInput(
 *     @PayloadKey("application")     LoanApplication application,
 *     @PayloadKey("customerProfile") CustomerProfile profile
 * ) {}
 * }</pre>
 *
 * <p>Compile-time validations enforced by the annotation processor:
 * <ul>
 *   <li>Must be applied to a {@code record}, not a class or interface</li>
 *   <li>All record components must carry {@link PayloadKey}</li>
 *   <li>No two components may share the same {@link PayloadKey} value</li>
 *   <li>No {@link PayloadKey} value may start with {@code _} (reserved namespace)</li>
 * </ul>
 *
 * @see PayloadKey
 * @see PayloadExtractor
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface PayloadInput {
}

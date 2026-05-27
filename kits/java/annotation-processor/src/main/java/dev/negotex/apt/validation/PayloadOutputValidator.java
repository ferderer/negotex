package dev.negotex.apt.validation;

import dev.negotex.payload.PayloadKey;
import dev.negotex.payload.PayloadOutput;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates elements annotated with {@link PayloadOutput}.
 *
 * <p>Validation rules:
 *
 * <p><b>@PayloadOutput record (Form B):</b>
 * <ul>
 *   <li>Must be applied to a {@code record}, not a class or interface</li>
 *   <li>Every record component must carry {@code @PayloadKey}</li>
 *   <li>No two components may share the same {@code @PayloadKey} value</li>
 *   <li>No key may be empty or start with {@code _}</li>
 * </ul>
 *
 * <p><b>@PayloadOutput("key") on handler method (Form A):</b>
 * <ul>
 *   <li>{@code value()} must be non-empty and non-reserved</li>
 *   <li>Return type must not be {@code void}</li>
 * </ul>
 */
public final class PayloadOutputValidator {

    private static final String RESERVED_PREFIX = "_";

    /**
     * Validate a {@code @PayloadOutput}-annotated record type.
     * @param element
     * @param result
     */
    public void validatePayloadOutputRecord(TypeElement element, ValidationResult result) {
        if (element.getKind() != ElementKind.RECORD) {
            result.error(element,
                    "@PayloadOutput may only be applied to records, not to " +
                    element.getKind().name().toLowerCase() + " '" +
                    element.getSimpleName() + "'");
            return;
        }

        List<? extends RecordComponentElement> components = element.getRecordComponents();

        if (components.isEmpty()) {
            result.error(element,
                    "@PayloadOutput record '" + element.getSimpleName() +
                    "' has no components — at least one @PayloadKey component is required");
            return;
        }

        Set<String> seenKeys = new HashSet<>();

        for (RecordComponentElement component : components) {
            PayloadKey payloadKey = component.getAnnotation(PayloadKey.class);

            if (payloadKey == null) {
                result.error(component,
                        "Record component '" + component.getSimpleName() +
                        "' in @PayloadOutput record '" + element.getSimpleName() +
                        "' must be annotated with @PayloadKey");
                continue;
            }

            validateKey(payloadKey.value(), component, result);

            if (!seenKeys.add(payloadKey.value())) {
                result.error(component,
                        "Duplicate @PayloadKey value '" + payloadKey.value() +
                        "' in @PayloadOutput record '" + element.getSimpleName() + "'");
            }
        }
    }

    /**
     * Validate a {@code @PayloadOutput("key")}-annotated handler method (Form A).
     * @param method
     * @param result
     */
    public void validateSingleValueMethod(ExecutableElement method, ValidationResult result) {
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            result.error(method,
                    "@PayloadOutput method '" + method.getSimpleName() +
                    "' must not return void");
        }

        PayloadOutput annotation = method.getAnnotation(PayloadOutput.class);
        String key = annotation.value();

        if (key == null || key.isBlank()) {
            result.error(method,
                    "@PayloadOutput on method '" + method.getSimpleName() +
                    "' requires a non-empty key value — use @PayloadOutput(\"myKey\")");
            return;
        }

        validateKey(key, method, result);
    }

    private void validateKey(String key,
                              javax.lang.model.element.Element element,
                              ValidationResult result) {
        if (key.startsWith(RESERVED_PREFIX)) {
            result.error(element,
                    "@PayloadKey value '" + key +
                    "' uses the reserved '_' prefix — reserved keys are for Negotex internal use");
        }
    }
}

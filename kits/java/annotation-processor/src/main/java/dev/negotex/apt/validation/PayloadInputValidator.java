package dev.negotex.apt.validation;

import dev.negotex.payload.PayloadInput;
import dev.negotex.payload.PayloadKey;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates elements annotated with {@link PayloadInput} and
 * {@link PayloadKey} on handler method parameters.
 *
 * <p>Validation rules:
 *
 * <p><b>@PayloadInput record (Form B):</b>
 * <ul>
 *   <li>Must be applied to a {@code record}, not a class or interface</li>
 *   <li>Every record component must carry {@code @PayloadKey}</li>
 *   <li>No two components may share the same {@code @PayloadKey} value</li>
 *   <li>No {@code @PayloadKey} value may be empty</li>
 *   <li>No {@code @PayloadKey} value may start with {@code _} (reserved namespace)</li>
 * </ul>
 *
 * <p><b>@PayloadKey on handler parameter (Form A):</b>
 * <ul>
 *   <li>The annotated method must have exactly one parameter</li>
 *   <li>The key must be non-empty and non-reserved</li>
 * </ul>
 */
public final class PayloadInputValidator {

    private static final String RESERVED_PREFIX = "_";

    /**
     * Validate a {@code @PayloadInput}-annotated type element.
     * @param element
     * @param result
     */
    public void validatePayloadInput(TypeElement element, ValidationResult result) {
        if (element.getKind() != ElementKind.RECORD) {
            result.error(element,
                    "@PayloadInput may only be applied to records, not to " +
                    element.getKind().name().toLowerCase() + " '" +
                    element.getSimpleName() + "'");
            return;
        }

        List<? extends RecordComponentElement> components = element.getRecordComponents();

        if (components.isEmpty()) {
            result.error(element,
                    "@PayloadInput record '" + element.getSimpleName() +
                    "' has no components — at least one @PayloadKey component is required");
            return;
        }

        Set<String> seenKeys = new HashSet<>();

        for (RecordComponentElement component : components) {
            PayloadKey payloadKey = component.getAnnotation(PayloadKey.class);

            if (payloadKey == null) {
                result.error(component,
                        "Record component '" + component.getSimpleName() +
                        "' in @PayloadInput record '" + element.getSimpleName() +
                        "' must be annotated with @PayloadKey");
                continue;
            }

            validateKey(payloadKey.value(), component, result);

            if (!seenKeys.add(payloadKey.value())) {
                result.error(component,
                        "Duplicate @PayloadKey value '" + payloadKey.value() +
                        "' in @PayloadInput record '" + element.getSimpleName() + "'");
            }
        }
    }

    /**
     * Validate a handler method whose parameter carries {@code @PayloadKey} (Form A).
     * @param method
     * @param result
     */
    public void validateSingleParameter(ExecutableElement method, ValidationResult result) {
        List<? extends VariableElement> params = method.getParameters();

        if (params.size() != 1) {
            result.error(method,
                    "Handler method '" + method.getSimpleName() +
                    "' has multiple parameters — use a @PayloadInput record for multi-attribute input");
            return;
        }

        VariableElement param = params.get(0);
        PayloadKey payloadKey = param.getAnnotation(PayloadKey.class);

        if (payloadKey == null) {
            result.error(param,
                    "Handler method parameter '" + param.getSimpleName() +
                    "' must be annotated with @PayloadKey");
            return;
        }

        if (param.asType().getKind().isPrimitive()) {
            result.error(param,
                    "@PayloadKey parameter '" + param.getSimpleName() +
                    "' is a primitive type — use the boxed type for payload extraction");
            return;
        }

        validateKey(payloadKey.value(), param, result);
    }

    private void validateKey(String key,
                              javax.lang.model.element.Element element,
                              ValidationResult result) {
        if (key == null || key.isBlank()) {
            result.error(element, "@PayloadKey value must not be empty");
            return;
        }
        if (key.startsWith(RESERVED_PREFIX)) {
            result.error(element,
                    "@PayloadKey value '" + key +
                    "' uses the reserved '_' prefix — reserved keys are for Negotex internal use");
        }
    }
}

package dev.negotex.apt.model;

import javax.lang.model.type.TypeMirror;

/**
 * Represents a single mapping between a record component or handler parameter
 * and a named envelope payload attribute.
 *
 * <p>Carries compile-time type information ({@link TypeMirror}) rather than
 * {@code Class<?>} — annotation processors operate in the {@code javax.lang.model}
 * world, not the reflection world.
 *
 * @param fieldName   the record component name or parameter name in the handler method
 * @param payloadKey  the payload attribute key from {@code @PayloadKey}
 * @param typeMirror  the compile-time type of the field or parameter
 */
public record FieldMapping(
        String fieldName,
        String payloadKey,
        TypeMirror typeMirror
) {}

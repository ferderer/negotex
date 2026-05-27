package dev.negotex.apt.model;

import java.util.List;

/**
 * Compile-time model for a generated {@code PayloadInserter} implementation.
 *
 * <p>Built by {@link ModelBuilder} from a {@code @PayloadOutput} record or a
 * {@code @PayloadOutput}-annotated handler method. Consumed by
 * {@link dev.negotex.apt.codegen.InserterGenerator} to produce the source file.
 *
 * @param sourcePackage      package of the annotated output type or handler class
 * @param outputTypeName     simple name of the output type
 * @param outputTypeQualified fully qualified name of the output type
 * @param generatedName      simple name of the class to generate, e.g. {@code CreditCheckResultInserter}
 * @param fields             ordered list of payload key → field mappings
 * @param singleValue        true when Form A (method-level @PayloadOutput with a single key) —
 *                           the inserter writes one key rather than iterating record components
 * @param singleValueKey     the payload key when {@code singleValue} is true; null otherwise
 */
public record InserterModel(
        String sourcePackage,
        String outputTypeName,
        String outputTypeQualified,
        String generatedName,
        List<FieldMapping> fields,
        boolean singleValue,
        String singleValueKey
) {
    public InserterModel {
        fields = List.copyOf(fields);
    }
}

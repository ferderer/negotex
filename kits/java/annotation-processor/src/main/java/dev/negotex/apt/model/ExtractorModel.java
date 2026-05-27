package dev.negotex.apt.model;

import java.util.List;

/**
 * Compile-time model for a generated {@code PayloadExtractor} implementation.
 *
 * <p>Built by {@link ModelBuilder} from a {@code @PayloadInput} record or a
 * {@code @PayloadKey}-annotated handler method parameter. Consumed by
 * {@link dev.negotex.apt.codegen.ExtractorGenerator} to produce the source file.
 *
 * @param sourcePackage    package of the annotated input type or handler class
 * @param inputTypeName    simple name of the input type (record or parameter type)
 * @param inputTypeQualified fully qualified name of the input type
 * @param generatedName    simple name of the class to generate, e.g. {@code CreditCheckInputExtractor}
 * @param fields           ordered list of payload key → field mappings
 * @param singleParam      true when Form B (direct parameter annotation) — affects
 *                         how the extractor constructs the result (direct value vs record)
 */
public record ExtractorModel(
        String sourcePackage,
        String inputTypeName,
        String inputTypeQualified,
        String generatedName,
        List<FieldMapping> fields,
        boolean singleParam
) {
    public ExtractorModel {
        fields = List.copyOf(fields);
    }
}

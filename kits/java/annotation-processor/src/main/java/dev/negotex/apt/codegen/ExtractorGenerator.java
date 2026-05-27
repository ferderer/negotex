package dev.negotex.apt.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dev.negotex.apt.model.ExtractorModel;
import dev.negotex.apt.model.FieldMapping;
import dev.negotex.payload.PayloadConversions;
import dev.negotex.payload.PayloadExtractor;

import javax.lang.model.element.Modifier;
import java.util.Map;

/**
 * Generates a {@code PayloadExtractor} implementation from an {@link ExtractorModel}.
 *
 * <p>Form B (multi-attribute {@code @PayloadInput} record) generates:
 * <pre>{@code
 * public final class CreditCheckInputExtractor implements PayloadExtractor<CreditCheckInput> {
 *
 *     private final PayloadConversions conversions;
 *
 *     public CreditCheckInputExtractor(PayloadConversions conversions) {
 *         this.conversions = conversions;
 *     }
 *
 *     @Override
 *     public CreditCheckInput extract(Map<String, Object> payload) {
 *         return new CreditCheckInput(
 *             conversions.convert(payload.get("application"), LoanApplication.class, "application"),
 *             conversions.convert(payload.get("customerProfile"), CustomerProfile.class, "customerProfile")
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p>Form A (single {@code @PayloadKey} parameter) generates an extractor that
 * returns the converted value directly rather than constructing a record.
 */
public final class ExtractorGenerator {

    private static final ClassName PAYLOAD_CONVERSIONS =
            ClassName.get(PayloadConversions.class);
    private static final ClassName PAYLOAD_EXTRACTOR =
            ClassName.get(PayloadExtractor.class);
    private static final ClassName MAP =
            ClassName.get(Map.class);

    private final CodeWriter writer;

    public ExtractorGenerator(CodeWriter writer) {
        this.writer = writer;
    }

    /**
     * Generate and write the extractor source file for the given model.
     *
     * @param model
     * @return true if generation succeeded
     */
    public boolean generate(ExtractorModel model) {
        ClassName inputType = ClassName.bestGuess(model.inputTypeQualified());
        ParameterizedTypeName extractorInterface =
                ParameterizedTypeName.get(PAYLOAD_EXTRACTOR, inputType);
        ParameterizedTypeName mapType =
                ParameterizedTypeName.get(MAP, ClassName.get(String.class),
                        ClassName.get(Object.class));

        // extract() method body
        MethodSpec extractMethod = MethodSpec.methodBuilder("extract")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(inputType)
                .addParameter(mapType, "payload")
                .addCode(buildExtractBody(model, inputType))
                .build();

        // Constructor
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(PAYLOAD_CONVERSIONS, "conversions")
                .addStatement("this.conversions = conversions")
                .build();

        TypeSpec extractor = TypeSpec.classBuilder(model.generatedName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(extractorInterface)
                .addField(PAYLOAD_CONVERSIONS, "conversions", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(constructor)
                .addMethod(extractMethod)
                .build();

        return writer.write(model.sourcePackage(), extractor);
    }

    private CodeBlock buildExtractBody(ExtractorModel model, ClassName inputType) {
        if (model.singleParam()) {
            // Form A: return converted value directly
            FieldMapping field = model.fields().get(0);
            TypeName targetType = TypeName.get(field.typeMirror());
            return CodeBlock.builder()
                    .addStatement("return conversions.convert(payload.get($S), $T.class, $S)",
                            field.payloadKey(), targetType, field.payloadKey())
                    .build();
        }

        // Form B: construct record from all components
        CodeBlock.Builder body = CodeBlock.builder();
        body.add("return new $T(\n", inputType);
        body.indent();

        var fields = model.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldMapping field = fields.get(i);
            TypeName targetType = TypeName.get(field.typeMirror());
            boolean last = i == fields.size() - 1;
            body.add("conversions.convert(payload.get($S), $T.class, $S)$L\n",
                    field.payloadKey(), targetType, field.payloadKey(),
                    last ? "" : ",");
        }

        body.unindent();
        body.addStatement(")");
        return body.build();
    }
}

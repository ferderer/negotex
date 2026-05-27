package dev.negotex.apt.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dev.negotex.apt.model.FieldMapping;
import dev.negotex.apt.model.InserterModel;
import dev.negotex.payload.PayloadConversions;
import dev.negotex.payload.PayloadInserter;

import javax.lang.model.element.Modifier;
import java.util.Map;

/**
 * Generates a {@code PayloadInserter} implementation from an {@link InserterModel}.
 *
 * <p>Form B (multi-attribute {@code @PayloadOutput} record) generates:
 * <pre>{@code
 * public final class CreditCheckResultInserter implements PayloadInserter<CreditCheckResult> {
 *
 *     private final PayloadConversions conversions;
 *
 *     public CreditCheckResultInserter(PayloadConversions conversions) {
 *         this.conversions = conversions;
 *     }
 *
 *     @Override
 *     public Map<String, Object> insert(CreditCheckResult output, Map<String, Object> payload) {
 *         Map<String, Object> enriched = conversions.insert("creditScore", output.score(), payload);
 *         enriched = conversions.insert("riskBand", output.riskBand(), enriched);
 *         enriched = conversions.insert("creditFlags", output.flags(), enriched);
 *         return enriched;
 *     }
 * }
 * }</pre>
 *
 * <p>Form A (single-value {@code @PayloadOutput("key")} method) generates an inserter
 * that writes the single return value under the declared key.
 */
public final class InserterGenerator {

    private static final ClassName PAYLOAD_CONVERSIONS =
            ClassName.get(PayloadConversions.class);
    private static final ClassName PAYLOAD_INSERTER =
            ClassName.get(PayloadInserter.class);
    private static final ClassName MAP =
            ClassName.get(Map.class);

    private final CodeWriter writer;

    public InserterGenerator(CodeWriter writer) {
        this.writer = writer;
    }

    /**
     * Generate and write the inserter source file for the given model.
     *
     * @param model
     * @return true if generation succeeded
     */
    public boolean generate(InserterModel model) {
        ClassName outputType = ClassName.bestGuess(model.outputTypeQualified());
        ParameterizedTypeName inserterInterface =
                ParameterizedTypeName.get(PAYLOAD_INSERTER, outputType);
        ParameterizedTypeName mapType =
                ParameterizedTypeName.get(MAP, ClassName.get(String.class),
                        ClassName.get(Object.class));

        MethodSpec insertMethod = MethodSpec.methodBuilder("insert")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(mapType)
                .addParameter(outputType, "output")
                .addParameter(mapType, "payload")
                .addCode(buildInsertBody(model))
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(PAYLOAD_CONVERSIONS, "conversions")
                .addStatement("this.conversions = conversions")
                .build();

        TypeSpec inserter = TypeSpec.classBuilder(model.generatedName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(inserterInterface)
                .addField(PAYLOAD_CONVERSIONS, "conversions", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(constructor)
                .addMethod(insertMethod)
                .build();

        return writer.write(model.sourcePackage(), inserter);
    }

    private CodeBlock buildInsertBody(InserterModel model) {
        CodeBlock.Builder body = CodeBlock.builder();

        if (model.singleValue()) {
            // Form A: insert the return value under the declared key
            body.addStatement(
                    "return conversions.insert($S, output, payload)",
                    model.singleValueKey());
            return body.build();
        }

        // Form B: chain inserts for each record component
        var fields = model.fields();
        body.addStatement(
                "$T<$T, $T> enriched = conversions.insert($S, output.$L(), payload)",
                Map.class, String.class, Object.class,
                fields.get(0).payloadKey(), fields.get(0).fieldName());

        for (int i = 1; i < fields.size(); i++) {
            FieldMapping field = fields.get(i);
            body.addStatement("enriched = conversions.insert($S, output.$L(), enriched)",
                    field.payloadKey(), field.fieldName());
        }

        body.addStatement("return enriched");
        return body.build();
    }
}

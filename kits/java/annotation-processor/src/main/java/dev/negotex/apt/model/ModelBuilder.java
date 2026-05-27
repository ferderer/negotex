package dev.negotex.apt.model;

import dev.negotex.payload.PayloadKey;
import dev.negotex.payload.PayloadOutput;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;

/**
 * Builds {@link ExtractorModel} and {@link InserterModel} instances from annotated
 * elements in the {@code javax.lang.model} element tree.
 *
 * <p>Called after validation succeeds — assumes all elements have already been
 * validated by {@link dev.negotex.apt.validation.PayloadInputValidator} and
 * {@link dev.negotex.apt.validation.PayloadOutputValidator}.
 */
public final class ModelBuilder {

    private final ProcessingEnvironment env;

    public ModelBuilder(ProcessingEnvironment env) {
        this.env = env;
    }

    // -------------------------------------------------------------------------
    // Extractor models
    // -------------------------------------------------------------------------

    /**
     * Build an extractor model for a {@code @PayloadInput} record.
     * Form B: multi-attribute input.
     * @param recordElement
     * @return 
     */
    public ExtractorModel forPayloadInputRecord(TypeElement recordElement) {
        String pkg = packageOf(recordElement);
        String simpleName = recordElement.getSimpleName().toString();
        String qualified = recordElement.getQualifiedName().toString();
        String generatedName = simpleName + "Extractor";

        List<FieldMapping> fields = recordElement.getRecordComponents().stream()
                .map(this::toFieldMapping)
                .toList();

        return new ExtractorModel(pkg, simpleName, qualified, generatedName, fields, false);
    }

    /**
     * Build an extractor model for a {@code @PayloadKey}-annotated handler method parameter.
     * Form A: single-attribute input.
     * @param method
     * @param param
     * @return 
     */
    public ExtractorModel forSingleParameter(ExecutableElement method, VariableElement param) {
        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        String pkg = packageOf(enclosingClass);

        // Use the parameter type as the extractor target type
        String paramTypeName = env.getTypeUtils()
                .asElement(param.asType())
                .getSimpleName().toString();
        String paramTypeQualified = param.asType().toString();
        String generatedName = paramTypeName + "Extractor";

        PayloadKey annotation = param.getAnnotation(PayloadKey.class);
        FieldMapping field = new FieldMapping(
                param.getSimpleName().toString(),
                annotation.value(),
                param.asType()
        );

        return new ExtractorModel(pkg, paramTypeName, paramTypeQualified,
                generatedName, List.of(field), true);
    }

    // -------------------------------------------------------------------------
    // Inserter models
    // -------------------------------------------------------------------------

    /**
     * Build an inserter model for a {@code @PayloadOutput} record.
     * Form B: multi-attribute output.
     * @param recordElement
     * @return 
     */
    public InserterModel forPayloadOutputRecord(TypeElement recordElement) {
        String pkg = packageOf(recordElement);
        String simpleName = recordElement.getSimpleName().toString();
        String qualified = recordElement.getQualifiedName().toString();
        String generatedName = simpleName + "Inserter";

        List<FieldMapping> fields = recordElement.getRecordComponents().stream()
                .map(this::toFieldMapping)
                .toList();

        return new InserterModel(pkg, simpleName, qualified, generatedName, fields, false, null);
    }

    /**
     * Build an inserter model for a {@code @PayloadOutput("key")}-annotated handler method.
     * Form A: single-value output.
     * @param method
     * @return 
     */
    public InserterModel forSingleValueMethod(ExecutableElement method) {
        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        String pkg = packageOf(enclosingClass);

        String returnTypeName = env.getTypeUtils()
                .asElement(method.getReturnType())
                .getSimpleName().toString();
        String returnTypeQualified = method.getReturnType().toString();
        String generatedName = returnTypeName + "Inserter";

        PayloadOutput annotation = method.getAnnotation(PayloadOutput.class);

        return new InserterModel(pkg, returnTypeName, returnTypeQualified,
                generatedName, List.of(), true, annotation.value());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FieldMapping toFieldMapping(RecordComponentElement component) {
        PayloadKey annotation = component.getAnnotation(PayloadKey.class);
        return new FieldMapping(
                component.getSimpleName().toString(),
                annotation.value(),
                component.asType()
        );
    }

    private String packageOf(TypeElement element) {
        return env.getElementUtils()
                .getPackageOf(element)
                .getQualifiedName()
                .toString();
    }
}

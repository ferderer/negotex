package dev.negotex.apt;

import dev.negotex.apt.codegen.CodeWriter;
import dev.negotex.apt.codegen.ExtractorGenerator;
import dev.negotex.apt.codegen.InserterGenerator;
import dev.negotex.apt.model.ModelBuilder;
import dev.negotex.apt.validation.PayloadInputValidator;
import dev.negotex.apt.validation.PayloadOutputValidator;
import dev.negotex.apt.validation.ValidationResult;
import dev.negotex.payload.PayloadInput;
import dev.negotex.payload.PayloadKey;
import dev.negotex.payload.PayloadOutput;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Set;

/**
 * Annotation processor for Negotex payload mapping annotations.
 *
 * <p>Processes {@link PayloadInput}, {@link PayloadOutput}, and {@link PayloadKey}
 * annotations, generating {@code PayloadExtractor} and {@code PayloadInserter}
 * implementations at compile time.
 *
 * <p>Processing order per round:
 * <ol>
 *   <li>Validate all annotated elements — collect all errors before emitting</li>
 *   <li>If validation passes, build models from annotated elements</li>
 *   <li>Generate extractor and inserter source files</li>
 * </ol>
 *
 * <p>Registered via {@code META-INF/services/javax.annotation.processing.Processor}.
 */
@SupportedAnnotationTypes({
        "dev.negotex.payload.PayloadInput",
        "dev.negotex.payload.PayloadOutput",
        "dev.negotex.payload.PayloadKey"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class NegotexAnnotationProcessor extends AbstractProcessor {

    private PayloadInputValidator inputValidator;
    private PayloadOutputValidator outputValidator;
    private ModelBuilder modelBuilder;
    private ExtractorGenerator extractorGenerator;
    private InserterGenerator inserterGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        inputValidator = new PayloadInputValidator();
        outputValidator = new PayloadOutputValidator();
        modelBuilder = new ModelBuilder(env);
        CodeWriter codeWriter = new CodeWriter(env.getFiler(), env.getMessager());
        extractorGenerator = new ExtractorGenerator(codeWriter);
        inserterGenerator = new InserterGenerator(codeWriter);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) {
            return false;
        }

        ValidationResult validation = new ValidationResult();

        // --- Validate @PayloadInput records ---
        for (Element element : round.getElementsAnnotatedWith(PayloadInput.class)) {
            if (element instanceof TypeElement typeElement) {
                inputValidator.validatePayloadInput(typeElement, validation);
            }
        }

        // --- Validate @PayloadOutput: records and methods ---
        for (Element element : round.getElementsAnnotatedWith(PayloadOutput.class)) {
            switch (element) {
                case TypeElement typeElement -> outputValidator.validatePayloadOutputRecord(typeElement, validation);
                case ExecutableElement method -> outputValidator.validateSingleValueMethod(method, validation);
                default -> {}
            }
        }

        // --- Validate @PayloadKey on handler method parameters (Form A) ---
        for (Element element : round.getElementsAnnotatedWith(PayloadKey.class)) {
            if (element instanceof VariableElement param
                    && param.getEnclosingElement() instanceof ExecutableElement method
                    && method.getEnclosingElement().getKind() != ElementKind.RECORD) {
                // Only validate method parameters — record components are covered by @PayloadInput
                inputValidator.validateSingleParameter(method, validation);
            }
        }

        // Emit all collected validation errors/warnings before proceeding
        validation.emit(processingEnv.getMessager());
        if (validation.hasErrors()) {
            return true;
        }

        // --- Generate extractors for @PayloadInput records ---
        for (Element element : round.getElementsAnnotatedWith(PayloadInput.class)) {
            if (element instanceof TypeElement typeElement) {
                extractorGenerator.generate(modelBuilder.forPayloadInputRecord(typeElement));
            }
        }

        // --- Generate extractors for @PayloadKey method parameters (Form A) ---
        Set<? extends Element> keyElements = round.getElementsAnnotatedWith(PayloadKey.class);
        for (Element element : keyElements) {
            if (element instanceof VariableElement param
                    && param.getEnclosingElement() instanceof ExecutableElement method
                    && method.getEnclosingElement().getKind() != ElementKind.RECORD) {
                extractorGenerator.generate(modelBuilder.forSingleParameter(method, param));
            }
        }

        // --- Generate inserters for @PayloadOutput records and methods ---
        for (Element element : round.getElementsAnnotatedWith(PayloadOutput.class)) {
            switch (element) {
                case TypeElement typeElement -> inserterGenerator.generate(modelBuilder.forPayloadOutputRecord(typeElement));
                case ExecutableElement method -> inserterGenerator.generate(modelBuilder.forSingleValueMethod(method));
                default -> {}
            }
        }

        return true;
    }
}

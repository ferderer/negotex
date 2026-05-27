package dev.negotex.runtime.handler;

import dev.negotex.error.NegotexException;
import dev.negotex.error.NegotexRuntimeError;
import dev.negotex.handler.TaskHandler;
import dev.negotex.payload.PayloadExtractor;
import dev.negotex.payload.PayloadInserter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Derives and loads {@link PayloadExtractor} and {@link PayloadInserter}
 * implementations for a handler class by naming convention.
 *
 * <p>The annotation processor generates extractor and inserter classes
 * in the same package as the annotated input/output type, named
 * {@code {TypeName}Extractor} and {@code {TypeName}Inserter}.
 *
 * <p>This resolver reads the handler's generic type parameters at startup
 * (once per handler, not per envelope) to determine the input and output
 * type names, then loads the generated classes by their conventional names.
 *
 * <p>Example: given
 * <pre>{@code
 * class CreditCheckHandler implements TaskHandler<CreditCheckInput, CreditCheckResult>
 * }</pre>
 * the resolver loads:
 * <ul>
 *   <li>{@code dev.acme.loan.CreditCheckInputExtractor}</li>
 *   <li>{@code dev.acme.loan.CreditCheckResultInserter}</li>
 * </ul>
 */
@Component
public class ExtractorResolver {

    private static final Logger log = LoggerFactory.getLogger(ExtractorResolver.class);

    private final dev.negotex.payload.PayloadConversions conversions;

    public ExtractorResolver(dev.negotex.payload.PayloadConversions conversions) {
        this.conversions = conversions;
    }

    /**
     * Resolve and instantiate the {@link PayloadExtractor} for the given handler class.
     *
     * @param handlerClass the handler class implementing {@link TaskHandler}
     * @return instantiated extractor
     * @throws NegotexException with {@link NegotexRuntimeError#EXTRACTOR_NOT_FOUND}
     *                          if the extractor class is not on the classpath
     * @throws NegotexException with {@link NegotexRuntimeError#EXTRACTOR_INSTANTIATION_FAILED}
     *                          if the extractor cannot be instantiated
     */
    public PayloadExtractor<?> resolveExtractor(Class<?> handlerClass) {
        Class<?> inputType = resolveInputType(handlerClass);
        String extractorName = inputType.getPackageName() + "."
            + inputType.getSimpleName() + "Extractor";

        log.debug("Resolving extractor {} for handler {}", extractorName,
            handlerClass.getSimpleName());

        Class<?> extractorClass = loadClass(extractorName, handlerClass,
            NegotexRuntimeError.EXTRACTOR_NOT_FOUND);

        return instantiate(extractorClass, handlerClass,
            NegotexRuntimeError.EXTRACTOR_INSTANTIATION_FAILED);
    }

    /**
     * Resolve and instantiate the {@link PayloadInserter} for the given handler class.
     *
     * @param handlerClass the handler class implementing {@link TaskHandler}
     * @return instantiated inserter
     * @throws NegotexException with {@link NegotexRuntimeError#INSERTER_NOT_FOUND}
     *                          if the inserter class is not on the classpath
     * @throws NegotexException with {@link NegotexRuntimeError#EXTRACTOR_INSTANTIATION_FAILED}
     *                          if the inserter cannot be instantiated
     */
    public PayloadInserter<?> resolveInserter(Class<?> handlerClass) {
        Class<?> outputType = resolveOutputType(handlerClass);
        String inserterName = outputType.getPackageName() + "."
            + outputType.getSimpleName() + "Inserter";

        log.debug("Resolving inserter {} for handler {}", inserterName,
            handlerClass.getSimpleName());

        Class<?> inserterClass = loadClass(inserterName, handlerClass,
            NegotexRuntimeError.INSERTER_NOT_FOUND);

        return instantiate(inserterClass, handlerClass,
            NegotexRuntimeError.EXTRACTOR_INSTANTIATION_FAILED);
    }

    // ── Generic type resolution ───────────────────────────────────────────────

    /**
     * Resolve the input type {@code I} from {@code TaskHandler<I, O>}.
     */
    private Class<?> resolveInputType(Class<?> handlerClass) {
        return resolveTypeArgument(handlerClass, 0);
    }

    /**
     * Resolve the output type {@code O} from {@code TaskHandler<I, O>}.
     */
    private Class<?> resolveOutputType(Class<?> handlerClass) {
        return resolveTypeArgument(handlerClass, 1);
    }

    private Class<?> resolveTypeArgument(Class<?> handlerClass, int index) {
        for (Type iface : handlerClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt
                    && pt.getRawType() == TaskHandler.class) {
                Type arg = pt.getActualTypeArguments()[index];
                if (arg instanceof Class<?> cls) {
                    return cls;
                }
                throw new NegotexException(NegotexRuntimeError.HANDLER_CLASS_NOT_FOUND)
                    .with("handler", handlerClass.getName())
                    .with("reason", "Type argument " + index + " is not a concrete class: " + arg);
            }
        }
        throw new NegotexException(NegotexRuntimeError.HANDLER_CLASS_NOT_FOUND)
            .with("handler", handlerClass.getName())
            .with("reason", "Does not directly implement TaskHandler<I, O>");
    }

    // ── Class loading and instantiation ───────────────────────────────────────

    private Class<?> loadClass(String className, Class<?> handlerClass, NegotexRuntimeError errorOnMissing) {
        try {
            return Class.forName(className, true, handlerClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NegotexException(errorOnMissing, e)
                .with("className", className)
                .with("handler", handlerClass.getName())
                .with("reason", "Ensure the handler JAR was compiled with "
                    + "negotex-annotation-processor on the annotation processor path");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<?> cls, Class<?> handlerClass, NegotexRuntimeError errorOnFailure) {
        try {
            // Generated extractors and inserters have a single-arg constructor
            // accepting PayloadConversions
            var constructor = cls.getDeclaredConstructor( dev.negotex.payload.PayloadConversions.class);
            return (T) constructor.newInstance(conversions);
        } catch (NoSuchMethodException e) {
            throw new NegotexException(errorOnFailure, e)
                .with("class", cls.getName())
                .with("handler", handlerClass.getName())
                .with("reason", "Expected constructor (PayloadConversions) not found — "
                    + "version mismatch between annotation processor and runtime API?");
        } catch (ReflectiveOperationException e) {
            throw new NegotexException(errorOnFailure, e)
                .with("class", cls.getName())
                .with("handler", handlerClass.getName())
                .with("cause", e.getMessage());
        }
    }
}

package dev.negotex.apt.validation;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects validation errors and warnings before emitting them via {@link Messager}.
 *
 * <p>Rather than emitting errors immediately and failing on the first problem,
 * all errors for a compilation round are collected first. This gives handler
 * authors a complete picture of what is wrong in one compilation pass.
 */
public final class ValidationResult {

    private record Entry(Diagnostic.Kind kind, Element element, String message) {}

    private final List<Entry> entries = new ArrayList<>();

    /** Record a compile error on the given element.
     * @param element
     * @param message */
    public void error(Element element, String message) {
        entries.add(new Entry(Diagnostic.Kind.ERROR, element, message));
    }

    /** Record a compile warning on the given element.
     * @param element
     * @param message */
    public void warning(Element element, String message) {
        entries.add(new Entry(Diagnostic.Kind.WARNING, element, message));
    }

    /** True if any errors were recorded (warnings do not count).
     * @return  */
    public boolean hasErrors() {
        return entries.stream().anyMatch(e -> e.kind() == Diagnostic.Kind.ERROR);
    }

    /** Emit all collected entries via the provided {@link Messager}.
     * @param messager */
    public void emit(Messager messager) {
        for (Entry entry : entries) {
            messager.printMessage(entry.kind(), entry.message(), entry.element());
        }
    }
}

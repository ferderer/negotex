package dev.negotex.handler;

/**
 * Handler for Map nodes — pure function transformation.
 *
 * <p>Implementations must be stateless and free of infrastructure dependencies.
 * The same instance may be called concurrently by multiple virtual threads.
 *
 * @param <I> Input type extracted from the envelope payload by the node processor
 * @param <O> Output type added to the payload as a new attribute by the node processor
 * @see <a href="https://github.com/negotex/negotex/blob/main/docs/adr/ADR-005-Handlers-As-Pure-Functions.md">ADR-005</a>
 */
@FunctionalInterface
public interface TaskHandler<I, O> {
    O handle(I input);
}

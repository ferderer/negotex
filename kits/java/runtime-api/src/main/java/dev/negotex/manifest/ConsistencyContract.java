package dev.negotex.manifest;

/**
 * Node-level consistency contract (ADR-030).
 *
 * <p>{@link #DETERMINISTIC} — same input always produces same output.
 * No network calls, no time-dependent behaviour, no randomness.
 * Required for Audit Certification Mode.
 *
 * <p>{@link #EVENTUAL} — side effects permitted. Idempotency under
 * retry is the handler author's responsibility.
 */
public enum ConsistencyContract {
    DETERMINISTIC,
    EVENTUAL
}

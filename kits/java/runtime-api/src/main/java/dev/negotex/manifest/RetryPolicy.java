package dev.negotex.manifest;

/**
 * Retry semantics for a node's governance policy (ADR-029).
 */
public enum RetryPolicy {
    /** Handler is idempotent — safe to retry on failure. */
    IDEMPOTENT,
    /** Handler must not be retried — at-most-once execution. */
    AT_MOST_ONCE
}

package dev.negotex.manifest;

/**
 * Payload merge strategy for Join nodes (ADR-009).
 */
public enum MergeStrategy {
    /** Merge all top-level keys. Exception on duplicate keys with differing values. */
    ATTRIBUTE_MERGE,
    /** First-arriving envelope passes through; others discarded. */
    FIRST_WINS,
    /** Payload becomes a list of all branch payloads. No conflicts possible. */
    COLLECT_ALL,
    /** Recursive map merge. Exception at leaf-level conflict. */
    DEEP_MERGE
}

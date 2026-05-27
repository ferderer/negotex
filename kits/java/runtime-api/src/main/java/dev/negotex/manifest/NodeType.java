package dev.negotex.manifest;

/**
 * The nine Negotex node primitives (ADR-006).
 */
public enum NodeType {
    MAP,
    FORK,
    JOIN,
    CHOICE,
    MERGE,
    FILTER,
    WAIT,
    TRIGGER,
    TERMINATE
}

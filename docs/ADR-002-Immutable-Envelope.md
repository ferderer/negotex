# ADR-002 — Immutable envelope with map-based payload

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 1 — Fundamental architecture |
| **Relates to** | ADR-009 (Join), ADR-027 (Audit storage) |

## Context

A workflow engine needs a unit of execution that flows through the process graph carrying process data. BPMN calls this a *token*. The implementation choices for this structure directly determine type safety, parallel branch handling, and audit trail fidelity.

**Problems with existing approaches:**
- Camunda 7 process variables are a mutable key-value store returning `Object`. No type safety, race conditions possible in parallel branches, no merge guarantees.
- Temporal workflow arguments are strongly typed but mutable workflow state requires synchronisation.
- None of the reference engines provide a formal merge semantics for parallel gateway convergence.

**Requirements specific to Negotex:**
1. Payloads must survive a Fork and be safely merged at a Join — each branch adds attributes without overwriting the base.
2. Business logic handlers must receive typed inputs, not raw maps.
3. The payload model must support content-addressable storage to avoid redundant writes on the audit trail (see ADR-027).
4. The base payload must be tamper-evident across branches (see ADR-028).

## Decision

### Envelope structure

The execution unit is `Envelope<T>` — an immutable record with a typed payload carried as `Map<String, Object>`:

```java
record Envelope<T>(
    String envelopeId,
    String processInstanceId,
    String processDefinitionId,
    String processVersion,
    Instant createdAt,
    Instant nodeEnteredAt,
    Map<String, Object> payload  // defensive copy via Map.copyOf()
) {}
```

`createdAt` records process-instance start time. `nodeEnteredAt` records entry into the current node, enabling per-node latency metrics without additional instrumentation.

Operations on an envelope (`moveTo()`, `withPayload()`, `transform()`) return new instances. Mutation is not possible.

### Payload model: additive enrichment

The initial payload is set by the Trigger node and contains a `_hash` key computed over all base attributes:

```java
String hash = sha256(canonicalJson(initialPayload));
payload.put("_hash", hash);
```

Subsequent nodes *add* new attributes to the payload; they never modify existing ones. The `_hash` remains unchanged throughout the process instance. This gives every node a tamper-detectable baseline: any branch that received the same initial payload will carry the same `_hash`.

### Fork/Join merge verification

When a Join node receives all expected envelopes from parallel branches, it verifies that all share the same `_hash` before merging:

```java
boolean mergeable = envelopes.stream()
    .allMatch(e -> baseHash.equals(e.payload().get("_hash")));
```

If hashes differ, the envelopes did not originate from the same process instance and cannot be safely merged. This is a hard failure.

**Built-in merge strategies (configurable per Join node):**

| Strategy | Behaviour | Conflict handling |
|---|---|---|
| `attribute-merge` (default) | Merges all top-level keys | Exception on duplicate keys with differing values |
| `first-wins` | First-arriving envelope passes through | Others discarded |
| `collect-all` | Payload becomes a list of all branch payloads | No conflicts possible |
| `deep-merge` | Recursive map merge | Exception at leaf level |

### Type-safe handler extraction

Handlers receive typed inputs extracted from the map by the processor:

```java
Application app = PayloadExtractor.extract(payload, "application", Application.class);
```

Jackson is used as a fallback converter for complex objects. The handler itself has no dependency on the map structure.

## Consequences

**Positive:**
- Thread-safe by design — no synchronisation needed for parallel branches.
- `_hash` provides fork-origin verification at Join without external coordination.
- Additive enrichment model makes payload evolution explicit: each node's contribution is a named, isolated attribute.
- Payloads are plain maps — easy to serialise, inspect, and test.

**Negative:**
- Fork duplicates the full payload across N branches. For large payloads (>256 bytes per attribute), ADR-027's content-addressable storage mitigates the storage cost; in-memory cost remains.
- All complex objects must be Jackson-serialisable.
- Merge conflicts must be avoided at design time via naming conventions (e.g. `approval_manager`, `approval_legal` rather than both `approval`).

## Alternatives considered

**Mutable process variables (Camunda style):** Rejected — race conditions in parallel branches, weak typing, no merge guarantees.

**Separate typed context object (Temporal style):** Rejected — requires synchronisation for parallel branches, extra abstraction layer without solving the merge problem.

**Event sourcing (all changes as events):** Rejected — excessive overhead for the common case; the audit trail in TimescaleDB already provides event sourcing at the infrastructure level.

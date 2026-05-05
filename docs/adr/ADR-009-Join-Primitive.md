# ADR-009 — Join primitive

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **BPMN equivalent** | Parallel Gateway (converging) |
| **Relates to** | ADR-002 (hash verification), ADR-003 (Valkey) |

## Decision

**Structure:** N incoming edges (N ≥ 2), 1 outgoing edge, Valkey state.

**Semantics:** Synchronisation point. Waits for envelopes from all N incoming edges sharing the same `processInstanceId`. When all have arrived, verifies that all carry the same `_hash` (confirming origin from the same Fork), merges their payloads, and publishes the merged envelope downstream. Intermediate envelopes are stored in Valkey keyed by `processInstanceId + nodeId`.

**Merge verification:**
```java
boolean mergeable = envelopes.stream()
    .allMatch(e -> baseHash.equals(e.payload().get("_hash")));
if (!mergeable) throw new MergeException("Envelopes have different base hashes");
```

**Built-in merge strategies:**

| Strategy | Behaviour | On conflict |
|---|---|---|
| `attribute-merge` (default) | Merges top-level keys | Exception |
| `first-wins` | First-arriving envelope passes through; others discarded | N/A |
| `collect-all` | Payload becomes a list of all branch payloads | N/A |
| `deep-merge` | Recursive map merge | Exception at leaf level |

**Process definition:**
```yaml
- id: join-checks
  type: join
  expectedBranches: 3
  mergeStrategy: attribute-merge
  edges:
    incoming:
      - credit-checked
      - income-checked
      - fraud-checked
    outgoing: checks-complete
```

## Consequences

Valkey TTL on pending envelopes should cover the maximum expected branch duration plus a safety margin. If one branch fails permanently (DLQ), the Join will never fire — process-level timeout configuration is required to handle this case.

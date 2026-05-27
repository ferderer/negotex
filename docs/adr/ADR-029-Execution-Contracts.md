# ADR-029 — Execution contracts

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 5 — Compliance features |
| **Priority** | PoC phase |
| **Relates to** | ADR-005 (handler model), ADR-017 (handler plugin architecture) |

## Context

Business logic handlers run inside the Negotex runtime. Without a formal contract, a `DIRTY` handler (ADR-005) can make arbitrary network calls, allocate unbounded memory, or invoke external services not sanctioned by the process owner. In regulated environments, these capabilities must be declared and enforced — not trusted by convention.

The governance policy in the process definition YAML is the single source of truth for what a node is permitted to do. Handler code does not carry capability annotations — the annotation approach would create two sources of truth and require the compiler to reconcile them. Instead, the handler's `Mode.DIRTY` declaration (ADR-005) signals that external access is possible, and the YAML governance policy declares what that access is allowed to be.

## Decision

### Governance policies (per node / per process)

Governance policies are first-class citizens in the process definition YAML — versioned with the process, auditable, and validated at deployment time:

```yaml
- id: charge-payment
  type: map
  handler: PaymentHandler
  mode: dirty
  consistency: eventual
  governance:
    maxExecutionTime: 2s
    maxMemoryUsage: 64MB
    allowedExternalCalls:
      - payment-gateway
    retryPolicy: idempotent
```

A governance policy is required for all `DIRTY` handlers. The compiler rejects a `DIRTY` handler without a governance policy.

`PURE` handlers do not carry governance policies — their mode structurally guarantees no external access.

### Governance policy fields

| Field | Description |
|---|---|
| `maxExecutionTime` | Maximum wall-clock time for handler execution |
| `maxMemoryUsage` | Maximum heap allocation (enforcement is JVM / language dependent) |
| `allowedExternalCalls` | Named external services the handler is permitted to call |
| `retryPolicy` | `idempotent` (safe to retry) or `at-most-once` (no retry on failure) |
| `auditRetention` | Retention period for audit events from this node, overrides process default |

### Contract validation at deployment

The process compiler validates each node's configuration at deployment:

1. `DIRTY` handler without governance policy → **error**
2. `DIRTY` handler with `allowedExternalCalls` not in the cluster's allowed service registry → **error**
3. `PURE` handler with a governance policy → **warning** (policy is ignored but signals a misclassification)
4. `DIRTY` + `deterministic` consistency → **warning** (see ADR-030)

Runtime enforcement of `allowedExternalCalls` (actually blocking calls to non-declared services) requires classloader isolation or a security agent — this is declaration-only in the PoC phase, with runtime enforcement planned for the Enterprise Control Plane.

## Consequences

**Positive:**
- The YAML process definition is the complete, auditable record of what each node is permitted to do.
- No handler code annotations required for capability declaration — one source of truth.
- Compliance officers can inspect the process definition and understand every node's security boundary without reading handler code.
- Violations are caught at deployment, not in production.

**Negative:**
- Governance policy correctness depends on the handler author being honest about what their handler does. Incorrect declarations are caught only if runtime enforcement is active.
- Runtime enforcement is declaration-only in the PoC — full sandboxing is deferred to the Enterprise Control Plane.

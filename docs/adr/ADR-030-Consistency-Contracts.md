# ADR-030 — Node-level consistency contracts

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 5 — Compliance features |
| **Priority** | PoC phase |
| **Relates to** | ADR-005 (handler model), ADR-028 (hash chains), ADR-029 (execution contracts) |

## Context

Not all nodes are equal from a determinism standpoint. A credit score calculation is deterministic — same input, same output, every time. A call to an external payment gateway is not — the result may differ based on the gateway's state, network conditions, or timing.

Process engineers need to declare which nodes are deterministic and which are not, for two reasons:

1. **Auditors** need to verify that compliance-critical calculations are reproducible from the audit trail.
2. **The runtime** needs to know whether a node can participate in Audit Certification Mode.

The handler's `mode` (ADR-005) and the node's `consistency` are related but orthogonal. Mode describes the handler's *infrastructure contract* (what it has access to). Consistency describes the handler's *behavioural contract* (what its outputs guarantee). `PURE` mode implies `deterministic` consistency structurally. `DIRTY` mode requires an explicit consistency declaration because external access does not necessarily mean non-determinism — but it makes determinism harder to guarantee.

## Decision

### Consistency declarations

Every node definition declares its consistency contract in the process YAML:

**`deterministic`:** The handler produces an identical output for identical inputs. No network calls, no time-dependent behaviour, no randomness. Suitable for: calculations, validations, transformations, rule evaluations.

**`eventual`:** The handler may have side effects or produce non-reproducible results. Idempotency under retry is the handler author's responsibility. Suitable for: external API calls, email sending, database writes.

```yaml
- id: calculate-risk-score
  type: map
  handler: RiskScoreHandler
  mode: pure
  consistency: deterministic    # implied by mode: pure, but explicit is clearer

- id: charge-payment
  type: map
  handler: PaymentHandler
  mode: dirty
  consistency: eventual
  governance:
    allowedExternalCalls: [payment-gateway]
    retryPolicy: idempotent
```

### Relationship between mode and consistency

| Mode | Consistency | Validity | Notes |
|---|---|---|---|
| `pure` | `deterministic` | ✅ valid | Structural guarantee — no side effects possible |
| `pure` | `eventual` | ❌ error | A pure handler cannot have side effects by definition |
| `dirty` | `eventual` | ✅ valid | Natural combination — external access with side effects |
| `dirty` | `deterministic` | ⚠️ warning | Permitted, but the handler author must guarantee it; injected dependencies make this hard to verify |
| `dirty` | not declared | ⚠️ warning | Assumed `eventual`; explicit declaration is preferred |

`PURE` mode makes the `consistency: deterministic` declaration redundant but not wrong — the compiler accepts it and may omit it in generated process definitions to reduce noise.

### Audit Certification Mode

A deployment-level flag that enforces full process reproducibility:

```yaml
process:
  id: loan-application
  version: 1.3.0
  auditCertification: true
```

When enabled:
- All nodes must declare `consistency: deterministic` (or be `mode: pure`).
- Any `eventual` node blocks deployment.
- Any `dirty` node without a `deterministic` declaration blocks deployment.
- The runtime verifies the hash chain (ADR-028) after each process instance completes, confirming that replay would produce identical results.

In Audit Certification Mode, a regulator can replay any historical process instance from the TimescaleDB audit trail and verify that the output matches the original.

## Consequences

**Positive:**
- Consistency guarantees are explicit in the process definition — readable by process engineers, auditors, and automated tools.
- The relationship between `mode` and `consistency` is formally defined — the compiler can catch contradictions at deployment.
- Audit Certification Mode enables regulatory replay certification without additional tooling.
- `PURE` + `deterministic` is the strongest compliance guarantee Negotex can offer — structurally enforced, not just declared.

**Negative:**
- `DIRTY` + `deterministic` is hard to verify without running the handler under controlled conditions. Enforcement is declaration-only in the PoC.
- Audit Certification Mode excludes all `eventual` handlers — any process that calls an external service cannot be certified. This is intentional: certification and external calls are incompatible.

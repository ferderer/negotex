# ADR-030 — Node-level consistency contracts

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 5 — Compliance features |
| **Priority** | PoC phase |
| **Relates to** | ADR-005 (handlers as pure functions), ADR-028 (hash chains) |

## Context

Not all nodes are equal from a determinism standpoint. A credit score calculation is deterministic — same input, same output, every time. A call to an external payment gateway is not — the result may differ based on the gateway's state, network conditions, or timing. Process engineers need a way to declare which nodes are deterministic and which are not, and auditors need to verify this declaration.

For certain regulatory certifications, an entire process must be reproducible — every step must produce the same result given the same input. This requires that all nodes in the process are deterministic.

## Decision

Every node definition declares its consistency contract:

**`deterministic`:** The handler produces an identical output for identical inputs. No network calls, no time-dependent behaviour, no randomness. The processor enforces this by running the handler in an environment where such capabilities are unavailable (or at minimum, violations are logged as contract breaches). Suitable for: calculations, validations, transformations, rule evaluations.

**`eventual`:** The handler may have side effects or produce non-reproducible results. Idempotency is the handler author's responsibility. Suitable for: external API calls, database lookups, email sending.

**Node definition:**
```yaml
- id: calculate-risk-score
  type: map
  handler: RiskScoreHandler
  consistency: deterministic

- id: charge-payment
  type: map
  handler: PaymentGatewayHandler
  consistency: eventual
```

**Audit Certification Mode (deployment-level flag):** When enabled, all nodes in the process must declare `deterministic`. Deployment fails if any node declares `eventual`. In this mode, a regulator can replay any historical process instance from its TimescaleDB audit trail and verify that the output matches the original — the hash chain (ADR-028) provides the verification anchor.

## Consequences

**Positive:**
- Consistency guarantees are explicit in the process definition — readable by process engineers, auditors, and automated tools.
- Audit Certification Mode enables regulatory replay certification without additional tooling.
- The `deterministic` declaration is the design-time companion to the runtime hash chain: the chain proves the execution happened as recorded; the `deterministic` contract proves the execution could be reproduced.

**Negative:**
- Enforcing `deterministic` at the JVM level (blocking network access, etc.) is language and runtime dependent. Initial implementation may be declaration-only, with enforcement as a future enhancement.
- Handler authors must correctly classify their handlers; incorrect `deterministic` declarations are not detectable without running the handler under controlled conditions.

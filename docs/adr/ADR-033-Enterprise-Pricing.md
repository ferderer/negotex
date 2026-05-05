# ADR-033 — Enterprise pricing model

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 6 — Business & product |

## Decision

Feature-tiered pricing on a per-cluster basis. No per-execution, per-node, or per-core metering.

| Tier | Price (indicative) | Target |
|---|---|---|
| Team | $15k/year | Small teams, single cluster |
| Business | $50k/year | Mid-size organisations, multi-cluster |
| Enterprise | $150k+/year | Large organisations, compliance-heavy, SLA |

## Consequences

Predictable cost for buyers — no metering surprises as process volume grows. No instrumentation overhead to count executions. Straightforward to quote and contract.

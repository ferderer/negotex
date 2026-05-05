# ADR-029 — Execution contracts

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 5 — Compliance features |
| **Priority** | PoC phase |
| **Relates to** | ADR-005 (handlers as pure functions), ADR-017 (handler plugin architecture) |

## Context

Business logic plugins (handlers) run inside the Negotex runtime. Without a formal contract, a plugin can make arbitrary network calls, allocate unbounded memory, or invoke external services not sanctioned by the process owner. In regulated environments, these capabilities must be declared and enforced — not trusted by convention.

Two concerns are separate but related:
- **Governance policies** (process/node level): what is the node *allowed* to do?
- **Plugin capability declarations** (handler level): what does the plugin *claim to need*?

At deployment, Negotex verifies that every plugin's declared capabilities are permitted by the governance policy of the node it is deployed into. A plugin that claims network access to `payment-gateway` will only deploy into a node whose policy allows that call. Violations are caught at deployment time, not at runtime.

## Decision

### Governance policies (per node / per process)

Governance policies are first-class citizens in the process definition — versioned, auditable, and runtime-enforceable:

```json
{
  "maxExecutionTime": "2s",
  "maxMemoryUsage": "64MB",
  "allowedExternalCalls": ["payment-gateway", "credit-bureau"],
  "auditRetention": "7y",
  "retryPolicy": "idempotent"
}
```

### Plugin capability registry (per handler)

Each handler declares its required runtime capabilities:

| Capability | Example value |
|---|---|
| `networkAccess` | `true` / `false` |
| `allowedOutboundServices` | `["payment-gateway", "credit-bureau"]` |
| `cpuLimit` | `500m` |
| `memoryLimit` | `64MB` |
| `filesystemAccess` | `none` / `read-only` / `read-write` |
| `cryptography` | `["AES-256", "SHA-256"]` |

### Contract validation

At topology compilation (deployment time), the console validates that every handler's declared capabilities are a subset of the permissions granted by the node's governance policy. Deployment fails with a validation error if any handler exceeds its node's policy.

## Consequences

**Positive:**
- Security posture is explicit and auditable — process definitions contain the full security contract.
- Violations are caught before deployment, not during a production incident.
- Compliance officers can inspect process definitions and understand the security boundary of every node without reading handler code.

**Negative:**
- Runtime enforcement of capability constraints (e.g. actually blocking network calls not in the allowed list) requires either a security sandbox (JVM security manager equivalent, classloader isolation) or an agent — this is not trivially implementable in all handler languages.
- Capability declarations require discipline from handler authors — incorrect declarations defeat the purpose.

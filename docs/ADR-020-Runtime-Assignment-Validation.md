# ADR-020 — Runtime assignment and handler validation

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 3 — Multilanguage & runtime |

## Decision

Process definitions include a `runtimes` section that names the runtime instances involved in the process. Each node references a runtime by name. The handler reference format is language-specific:

| Language | Handler reference format |
|---|---|
| Java | Fully qualified class name: `com.example.CreditCheckHandler` |
| F# | `Namespace.Module.functionName` |
| Rust | `crate::module::function_name` |

The OSS console validates handler reference consistency at deployment time: every node's handler must exist in the runtime it references, and the runtime's language must match the handler reference format.

**Process definition excerpt:**
```yaml
runtimes:
  order-core:
    language: java
    base: negotex/java-runtime:1.0
    handlers: s3://acme/order-handlers-1.0.jar
  risk-engine:
    language: fsharp
    image: acme/risk-engine-fsharp:1.0

nodes:
  - id: validate-order
    type: map
    runtime: order-core
    handler: com.acme.order.ValidateOrderHandler
  - id: calculate-risk
    type: map
    runtime: risk-engine
    handler: Acme.Risk.RiskCalculation.evaluate
```

## Consequences

Teams can deploy different nodes in different languages within the same process. Handler validation at deployment prevents runtime failures caused by missing or misspelled handler references. The console is the single point of validation — runtimes themselves trust that the manifest they receive is correct.

# ADR-021 — Handler packaging and deployment modes

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 3 — Multilanguage & runtime |

## Decision

Two deployment modes:

**Mode 1 — Standard image + handler artifact:**
The Negotex runtime image is used as-is. Handler artifacts (JAR, DLL, binary) are supplied at runtime via a mounted volume, S3 URL, or classpath injection. No Docker rebuild is required for handler changes.

```yaml
runtimes:
  order-core:
    language: java
    base: negotex/java-runtime:1.0
    handlers: s3://acme/order-handlers-1.0.jar
    replicas: 2
```

**Mode 2 — Custom image:**
A fully built Docker image that includes the runtime and handlers. Used when handler packaging requirements are complex or when immutable image semantics are required for compliance.

```yaml
runtimes:
  risk-engine:
    language: rust
    image: acme/risk-engine:1.0
    replicas: 4
```

## Consequences

Mode 1 is the default for iterative development — handler changes deploy in seconds without a Docker build pipeline. Mode 2 is preferred for production compliance environments where image provenance and immutability are audited.


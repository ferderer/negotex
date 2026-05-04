# Architecture Decision Records

Architecture decisions for Negotex, organised by concern level. Each ADR captures context, the decision made, and its consequences. Decisions marked **Accepted** are stable. Decisions marked **Proposed** are under active review.

---

## Level 1 — Fundamental architecture

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](ADR-001-No-Central-Orchestrator.md) | No central orchestrator | Accepted |
| [ADR-002](ADR-002-Immutable-Envelope.md) | Immutable envelope with map-based payload | Accepted |
| [ADR-003](ADR-003-Unified-Infrastructure-Stack.md) | Unified infrastructure stack | Accepted |
| [ADR-004](ADR-004-Async-State-Publishing.md) | Async state publishing | Accepted |

## Level 2 — Node processing

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-005](ADR-005-Handlers-As-Pure-Functions.md) | Handlers as pure functions | Accepted |
| [ADR-006](ADR-006-Node-Primitives.md) | Nine node primitives | Accepted |
| [ADR-007](ADR-007-Map-Primitive.md) | Map primitive | Accepted |
| [ADR-008](ADR-008-Fork-Primitive.md) | Fork primitive | Accepted |
| [ADR-009](ADR-009-Join-Primitive.md) | Join primitive | Accepted |
| [ADR-010](ADR-010-Choice-Primitive.md) | Choice primitive | Accepted |
| [ADR-011](ADR-011-Merge-Primitive.md) | Merge primitive | Accepted |
| [ADR-012](ADR-012-Filter-Primitive.md) | Filter primitive | Accepted |
| [ADR-013](ADR-013-Wait-Primitive.md) | Wait primitive | Accepted |
| [ADR-014](ADR-014-Trigger-Primitive.md) | Trigger primitive | Accepted |
| [ADR-015](ADR-015-Terminate-Primitive.md) | Terminate primitive | Accepted |
| [ADR-016](ADR-016-Conditions-As-Plugins.md) | Conditions as plugins | Accepted |
| [ADR-017](ADR-017-Handler-Plugin-Architecture.md) | Handler plugin architecture | Accepted |

## Level 3 — Multilanguage & runtime

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-018](ADR-018-Multilanguage-Node-Processors.md) | Multilanguage node processors | Accepted |
| [ADR-019](ADR-019-FSharp-Prioritisation.md) | F#/.NET prioritisation | Accepted |
| [ADR-020](ADR-020-Runtime-Assignment-Validation.md) | Runtime assignment and handler validation | Accepted |
| [ADR-021](ADR-021-Handler-Packaging.md) | Handler packaging and deployment modes | Accepted |
| [ADR-022](ADR-022-Runtime-Manifest.md) | Runtime manifest | Accepted |
| [ADR-023](ADR-023-Zero-Downtime-Versioning.md) | Zero-downtime versioning | Accepted |

## Level 4 — Infrastructure

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-024](ADR-024-Java-Kafka-Baseline.md) | Java 21+ and Kafka 4 (KRaft) baseline | Accepted |
| [ADR-025](ADR-025-Publisher-Interface.md) | Publisher interface | Accepted |
| [ADR-026](ADR-026-Kafka-Topic-Configuration.md) | Kafka topic naming and configuration | Accepted |
| [ADR-027](ADR-027-Audit-Storage.md) | Audit storage — content-addressable payloads with differentiated retention | Accepted |

## Level 5 — Compliance features

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-028](ADR-028-Envelope-Hash-Chains.md) | Envelope hash chains | Proposed |
| [ADR-029](ADR-029-Execution-Contracts.md) | Execution contracts | Proposed |
| [ADR-030](ADR-030-Consistency-Contracts.md) | Node-level consistency contracts | Proposed |

## Level 6 — Business & product

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-031](ADR-031-Open-Core-Model.md) | Open-core model and enterprise separation | Accepted |
| [ADR-032](ADR-032-OSS-Console-vs-Enterprise-CP.md) | OSS console vs Enterprise Control Plane | Accepted |
| [ADR-033](ADR-033-Enterprise-Pricing.md) | Enterprise pricing model | Accepted |

## Level 7 — Tooling

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-034](ADR-034-Project-Generator.md) | Project generator from BPMN | Accepted |
| [ADR-035](ADR-035-SDKMAN-Distribution.md) | SDKMAN distribution | Accepted |
| [ADR-036](ADR-036-Console-UI-Architecture.md) | Console UI architecture | Accepted |

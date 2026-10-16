# ADR-041 — `ops`: process compiler and deployment engine

| | |
|---|---|
| **Status** | Proposed |
| **Level** | 4 — Infrastructure |
| **Relates to** | ADR-020, ADR-022, ADR-026, ADR-029, ADR-030, ADR-034, ADR-040, ADR-042 |

## Context

ADR-020, -022, -026 and -029 assign validation, manifest generation, topic creation and contract checking to "the console". With the console becoming a pure REST client (ADR-040), these responsibilities need a home that CLI, console and the Enterprise Control Plane can share without duplicating logic.

## Decision

`ops` is a library with CLI and REST facade. It is the single implementation of everything that turns a process definition into a deployable topology. Console, CLI and ECP are clients of `ops`; **wherever ADR-020, -022, -026 and -029 say "console", read "ops".**

### Source of truth

The YAML process definition is the single source of truth. BPMN is an entry point only: the generator (ADR-034) converts BPMN into YAML once. An optional drift check compares structure (nodes, types, edges) between a YAML file and a BPMN file and reports differences; there is no bidirectional sync.

### Pipeline

```
parse → resolve → compile → apply
```

| Stage | Responsibility | Side effects |
|---|---|---|
| **parse** | YAML to `ProcessDefinition`; normalisation of input sugar | none |
| **resolve** | Fetch handler artifacts, read descriptors, compute digests | read-only I/O |
| **compile** | `(definition, resolved artifacts, environment) → diagnostics + topology + manifests`; pure function | none |
| **apply** | Create topics (idempotent), deliver manifests | yes |

`compile` is deterministic and runs without Docker or Kafka, so it is covered by golden-file tests.

### Canonical model

Edges are listed centrally; nodes carry none:

```yaml
nodes:
  - { id: start,    type: trigger,   trigger: api, handler: com.acme.StartHandler }
  - { id: validate, type: map,       handler: com.acme.ValidateHandler, consistency: deterministic }
  - { id: end,      type: terminate }
edges:
  - { from: start,    to: validate }
  - { from: validate, to: end }
```

The nested form of ADR-007/-008/-010 (`edges.incoming/outgoing`) remains accepted as input and is normalised away. The edge ID is always `{from}-to-{to}`, matching ADR-026. Consequently:

- Node IDs match `[a-z0-9]+(-[a-z0-9]+)*` and must not contain the segment `-to-` (otherwise edge IDs become ambiguous).
- The segments `edge`, `dlq`, `retry` are reserved (ADR-026, ADR-042).
- At most one edge per `(from, to)` pair.

### Handler descriptor

Every handler artifact carries a descriptor file listing its handlers: reference, interface type, and capability declaration (ADR-029). The compiler validates Java, F# and Rust handlers uniformly without loading code. The artifact digest becomes the `handlerVersion` used by the hash chain (ADR-028). The project generator (ADR-034) emits the descriptor.

### Validation

1. **Structure:** edge arity per primitive (ADR-006); at least one Trigger and one Terminate; reachability; exactly one default on Choice.
2. **Split/join pairing:** every Join is paired with its Split to verify `expectedBranches`; Filter→Join uses a dynamic count (ADR-012). v1 requires block-structured processes; unstructured graphs are rejected with a diagnostic naming the offending nodes.
3. **Handlers (ADR-020):** existence, interface type, reference format per language.
4. **Contracts (ADR-029/030):** capabilities ⊆ governance policy; Audit Certification Mode.
5. **Topics (ADR-026/042):** name conformance, length (Kafka limit 249), DLQ retention versus Valkey TTL for Joins.

### Output

The topology contains edge topics, per-node DLQ topics (ADR-042), consumer groups, partitions, replication and retention. Manifests follow ADR-022; secrets are references only. Infrastructure endpoints come from an environment configuration, never from the process definition.

### Modules

| Module | Content |
|---|---|
| `ops-model` | Definition, diagnostics, topology |
| `ops-compiler` | Parser, normalisation, validators, planner, emitter |
| `ops-apply` | Kafka admin, manifest delivery, DLQ redrive |
| CLI | Thin layer, shared with the generator |

### Phases

1. Model, YAML parser, structural validation, topic plan, manifest emitter; `negotex validate`, `negotex compile`.
2. Handler resolve, contract validation, `apply` for new deployments.
3. drain-and-switch (needs a management endpoint to pause the Trigger), DLQ redrive, BPMN drift check.

## Consequences

**Positive:**
- One implementation for CLI, console and ECP; the IP boundary of ADR-031 is unaffected, since `ops` belongs to the OSS side.
- Deterministic compile stage makes the process compiler testable in isolation and reusable in CI.
- Process definitions are plain files — versioned, diffable and reviewable in Git.

**Negative:**
- BPMN is no longer the artefact of record. Regulators that expect BPMN as the submitted artefact need the drift check or an export.
- Block-structured processes only in v1 excludes some valid BPMN graphs.
- Descriptor files add one more artefact per handler package.

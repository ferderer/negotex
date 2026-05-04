# ADR-034 — Project generator from BPMN

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 7 — Tooling |

## Decision

A CLI generator reads a BPMN file and produces a Maven project containing:
- Handler stub classes for every Service Task / node with a handler
- Test templates per handler (JUnit 5, input/output fixtures)
- Process definition YAML derived from the BPMN
- Deployment artefacts (Dockerfile, Docker Compose, Kubernetes manifests)

The generator is the primary on-ramp for new Negotex projects. It eliminates the boilerplate of creating handler stubs and wiring up the process definition manually.

## Consequences

Fast project start from an existing BPMN diagram. Consistent project structure across teams. The generator must track process definition schema evolution.

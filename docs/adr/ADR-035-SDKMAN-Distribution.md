# ADR-035 — SDKMAN distribution

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 7 — Tooling |

## Decision

SDKMAN is the primary distribution channel for the Negotex CLI (project generator, process validator, deployment tool). A Docker image is provided as an alternative for CI/CD environments and non-SDKMAN users.

## Consequences

Java developers already use SDKMAN for JDK management — the installation path is familiar. Version management (install, use, list) is handled by SDKMAN. Limited reach outside SDKMAN users; the Docker image covers the remainder.

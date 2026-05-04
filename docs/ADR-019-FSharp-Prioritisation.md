# ADR-019 — F#/.NET prioritisation

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 3 — Multilanguage & runtime |

## Decision

The F#/.NET processor kit is prioritised for Phase 2, before Go and Rust.

**Rationale:** The primary target market for Negotex is compliance-heavy industries — banking, insurance, healthcare. The financial services sector uses F# extensively for quantitative modelling, risk calculations, and compliance logic. Algebraic types in F# (discriminated unions, option types) are a natural fit for domain modelling in regulated domains where incomplete or invalid states must be made unrepresentable.

**Processor kit roadmap:**

| Kit | Phase | Primary target |
|---|---|---|
| `negotex-java` | 1 (current) | Enterprise Java shops |
| `negotex-fsharp` | 2 | Financial services — quant, risk, compliance |
| `negotex-rust` | 3 | Systems-level and security-critical nodes |

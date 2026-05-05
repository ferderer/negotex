# ADR-017 — Handler plugin architecture

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |

## Decision

Handler is a plugin system. Built-in handler types cover the common cases without custom code:

| Plugin type | Use case |
|---|---|
| `custom` | Java/F#/Rust class implementing the handler interface |
| `expression` | Simple field transformation expressions |
| `script` | JSR-223 scripting (Groovy, etc.) |
| `http` | HTTP call to external service |
| `sql` | SQL query against configured datasource |
| `mapper` | Declarative field mapping (no code) |
| `condition` | Boolean evaluation (used in Choice/Filter) |

Users can register additional plugin types. A plugin type is identified by its name in the process definition; the runtime resolves it to an implementation via the plugin registry.

## Consequences

Low-code teams can build processes using `expression`, `mapper`, and `http` plugins without writing Java. Pro-code teams use `custom` for full flexibility. The plugin system is extensible without modifying the Negotex core.

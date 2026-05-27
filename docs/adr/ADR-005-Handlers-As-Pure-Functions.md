# ADR-005 — Handler model and execution modes

| | |
|---|---|
| **Status** | Accepted |
| **Level** | 2 — Node processing |
| **Relates to** | ADR-002 (Envelope), ADR-006 (Node primitives), ADR-029 (Execution contracts), ADR-030 (Consistency contracts) |

## Context

Business logic in a workflow engine needs to be testable in isolation and deployable without knowledge of the surrounding topology. If handlers import Kafka clients, database connections, or envelope routing logic, they become coupled to the runtime and untestable without a full infrastructure stack.

At the same time, real-world processes require handlers that call external services — payment gateways, credit bureaus, notification systems. Prohibiting all external communication would make Negotex unusable for the compliance-heavy workflows it targets.

The model must therefore distinguish between handlers that are genuinely pure and handlers that require controlled external access, while keeping the infrastructure contract explicit in both cases.

## Decision

### Handler interfaces

Every handler implements a typed interface per primitive. The handler receives a typed input extracted from the envelope payload by the node processor. It returns a typed output. It has no access to the `Envelope`, the `Publisher`, or any infrastructure component.

| Primitive | Handler interface | Signature |
|---|---|---|
| Map | `TaskHandler<I, O>` | `I → O` |
| Choice | `ChoiceHandler` | `payload → edgeId` |
| Filter | `FilterHandler` | `payload → Set<edgeId>` |
| Wait | `WaitHandler` | `onSuspend(taskId, ctx)` / `onResume(taskId, data) → payload` |
| Trigger | `TriggerHandler` | `externalEvent → payload` |
| Terminate | `TerminateHandler` | `onTerminate(payload)` (optional cleanup hook) |

Fork, Join, and Merge have no handler — they are pure infrastructure primitives.

### Handler modes

Every handler declares its execution mode via `@NegotexHandler`:

**`Mode.PURE`** — the handler has no dependencies. The runtime instantiates it via its no-arg constructor. No Spring context is involved. The handler structurally cannot access infrastructure — there is no injection mechanism available to it. `PURE` implies `deterministic` (ADR-030).

```java
@NegotexHandler(Mode.PURE)
public class CreditCheckHandler implements TaskHandler<String, Integer> {
    public Integer handle(String application) {
        return application.length() > 10 ? 750 : 500;
    }
}
```

**`Mode.DIRTY`** — the handler has constructor dependencies that the runtime injects via Spring (`AutowireCapableBeanFactory.createBean()`). Handler authors configure their dependencies in a `@Configuration` class shipped in the handler JAR. The handler does not receive the Spring context — only its declared constructor parameters are injected. `DIRTY` handlers must declare a consistency contract in the process definition YAML (ADR-030).

```java
@NegotexHandler(Mode.DIRTY)
public class PaymentHandler implements TaskHandler<PaymentRequest, PaymentResult> {

    private final PaymentGatewayClient client;

    public PaymentHandler(PaymentGatewayClient client) {
        this.client = client;
    }

    public PaymentResult handle(PaymentRequest request) {
        return client.charge(request.amount(), request.cardToken());
    }
}
```

### Runtime initialisation per mode

```java
HandlerEntry entry = switch (manifest.mode(nodeId)) {
    case PURE  -> new HandlerEntry(
                      handlerClass.getDeclaredConstructor().newInstance(),
                      extractorFor(handlerClass),
                      inserterFor(handlerClass));
    case DIRTY -> new HandlerEntry(
                      applicationContext.getAutowireCapableBeanFactory()
                                        .createBean(handlerClass),
                      extractorFor(handlerClass),
                      inserterFor(handlerClass));
};
```

### Compiler cross-validation

At deployment, the process compiler validates mode against the node's governance policy (ADR-029) and consistency contract (ADR-030):

| Mode | Consistency | Compiler outcome |
|---|---|---|
| `PURE` | `deterministic` | ✅ valid |
| `PURE` | `eventual` | ❌ error — a pure handler cannot have side effects |
| `DIRTY` | `deterministic` | ⚠️ warning — determinism is hard to guarantee with injected dependencies |
| `DIRTY` | `eventual` | ✅ valid |
| `DIRTY` | not declared | ⚠️ warning — undeclared side effects |
| `DIRTY` | any | governance policy required in YAML — error if absent |

## Consequences

**Positive:**
- `PURE` handlers are unit-testable without any mocking of infrastructure.
- `DIRTY` handlers can call external services without requiring a factory pattern or custom wiring abstraction.
- Mode is declared explicitly — the runtime enforces it structurally, not by convention.
- `PURE` + `deterministic` is the foundation for Audit Certification Mode (ADR-030).

**Negative:**
- `DIRTY` handlers break the pure function guarantee. Handler authors are responsible for idempotency.
- Spring context is required for `DIRTY` handler instantiation — the runtime has a Spring dependency that pure deployments don't strictly need.
- Handlers cannot access envelope metadata (correlation ID, timestamps) in either mode — this is intentional.
- Cross-cutting concerns (logging, metrics) are injected by the processor wrapper, not written into the handler.

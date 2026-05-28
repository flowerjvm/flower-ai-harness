# ARCHITECTURE.md

This document defines the technical shape of `flower-ai-harness` for its first
implementable version. It is the architectural counterpart to `README.md`,
`STRATEGY.md`, and `OPEN_SOURCE_STRATEGY.md`.

The goal of v0 is not to ship a public framework. It is to build a small but
real harness framework, validate it against ArchDox document QA, and let real
usage refine the abstractions. The architecture below is sized accordingly:
small feature set, strong boundaries, production-shape contracts.

---

## 1. Overall Goal

Build a Java framework that:

- runs an AI step as a controlled, observable, retryable unit of work inside a
  Flower flow,
- never blocks a Flower worker tick on network or model latency,
- returns structured, schema-validated results,
- supports retry/refine as a first-class lifecycle,
- exposes a provider-neutral model gateway that can later wrap any vendor,
- can be exercised end-to-end against a deterministic fake provider,
- has zero ArchDox-specific concepts in its core.

"Minimal but real" is the operative phrase. The first version implements a
small surface area. Behind that surface, the contracts are shaped as if real
production harnesses will run through them — because they will, starting with
ArchDox.

---

## 2. Project Boundary

Three independent codebases, three responsibilities:

```text
flower                  Java orchestration toolkit. Flow, Step, Worker, Engine.
                        Tick-driven, single-JVM workflow execution. No AI
                        concepts. No knowledge of providers, prompts, findings.

flower-ai-harness       Generic AI run lifecycle framework on top of Flower.
                        Provider gateway abstraction, model request/response,
                        validation, retry/refine, structured findings, run
                        context, prompt versioning model, deterministic test
                        provider. No domain concepts.

archdox                 Document workflow product. Depends on
                        flower-ai-harness. Supplies document-specific prompt
                        builders, finding mappers, persistence adapters, and
                        UI integration. Does not modify the harness core.
```

The contract is one-directional:

```text
archdox  ─depends on→  flower-ai-harness  ─depends on→  flower
```

Nothing in `flower-ai-harness` knows about ArchDox. Nothing in `flower` knows
about AI.

This separation is enforced through:

- separate repositories,
- separate Maven group/artifact coordinates,
- no compile-time dependency from core onto vendor SDKs,
- no compile-time dependency from core onto Spring,
- no compile-time dependency from core onto any persistence engine.

---

## 3. Role Separation (Concrete)

### What Flower owns

- Flow definition and step transitions
- Worker / Engine lifecycle
- Tick scheduling
- Step return contract (`stay()`, `done()`, `fail()`)
- In-flow state plumbing

`flower-ai-harness` does not add steps, scheduling primitives, or context
mechanics to Flower. It composes Flower's existing primitives.

### What `flower-ai-harness` owns

- `AiHarnessRunContext` — per-run state slot inside a Flower flow
- `AiHarnessSpec` — declarative description of a single harness type
- `AiHarnessFlowFactory` — builds an `AiHarnessFlow` from a `Spec` and an input
- Internal step set (`PreparePromptStep`, `AwaitResponseStep`,
  `ValidateResponseStep`, `RefineDecisionStep`, `EmitFindingsStep`)
- `AiModelGateway` and `AiModelCall` — non-blocking provider port
- `AiModelRequest` / `AiModelResponse` — provider-neutral DTOs
- `AiSchemaValidator` / `ValidationResult` — structured output contract
- `AiRefinePolicy` / `RefineDecision` — retry/refine lifecycle
- `AiFinding` / `AiFindingSeverity` — domain-neutral structured finding
- `PromptTemplate` / `PromptVersion` / `PromptBuilder`
- `FindingExtractor` / `FindingSink` — host integration seams
- `FakeAiModelGateway` — first-class deterministic provider (test artifact)
- `TraceListener` (SPI) — passive observation hook
- `ModelId` / `ProviderOptions` — multi-provider routing primitives
- `AiHarnessRunStore` / `AiHarnessRunSnapshot` — AI-run operational state,
  separate from Flower checkpoints
- `AiRecoveryPolicy` / `AiRecoveryDecision` — explicit snapshot recovery
  decisions
- `AiCancellationToken` / `AiBudgetPolicy` / `AiResourceGovernor` —
  cancellation, cost, and resource governance controls

### What ArchDox owns (must not leak into core)

- `DocumentSnapshot`, `DocumentTemplate`, `LegalRuleSet`
- `DocumentFinding`, `DocumentFindingLocation`
- Document-specific prompt text and prompt builders
- Mapping from `AiFinding` to ArchDox domain finding types
- DB schema, JPA entities, repositories
- REST endpoints and UI integration
- Multi-tenant / org-scoping concerns

These are implemented inside ArchDox using the seams exposed by
`flower-ai-harness`. The harness never imports an ArchDox type.

---

## 4. Core Abstractions (Summary)

The v0 type set, grouped by concern. Full signatures and rationale live in
`API_DESIGN.md`.

| Concern             | Types                                                              |
| ------------------- | ------------------------------------------------------------------ |
| Model I/O           | `AiModelRequest`, `AiModelResponse`, `ModelId`, `ProviderOptions`  |
| Provider port       | `AiModelGateway`, `AiModelCall`, `AiModelCallStatus`               |
| Prompting           | `PromptTemplate`, `PromptVersion`, `PromptBuilder`, `RenderedPrompt` |
| Validation          | `AiSchemaValidator<T>`, `ValidationResult<T>`, `ValidationError`   |
| Refine              | `AiRefinePolicy`, `RefineDecision`, `ModelFallbackPlan`            |
| Findings            | `AiFinding`, `AiFindingSeverity`, `FindingExtractor<T>`, `FindingSink` |
| Run state           | `AiHarnessRunContext`, `AiHarnessRunId`, `AiHarnessRunStatus`, `AiHarnessRunSnapshot`, `AiHarnessRunStore` |
| Recovery            | `AiRecoveryContext`, `AiRecoveryDecision`, `AiRecoveryPolicy`           |
| Operational control | `AiCancellationToken`, `AiBudgetPolicy`, `AiResourceGovernor`      |
| Spec & assembly     | `AiHarnessSpec<I,T>`, `AiHarnessFlowFactory<I,T>`, `AiHarnessFlow` |
| Test provider       | `FakeAiModelGateway`                                               |
| Observability hook  | `TraceListener` (SPI)                                              |

Roughly twenty public types. Each has a single, narrow responsibility. The
combined public API is intentionally small enough that one developer can read
the entire surface in one sitting.

---

## 5. Module Structure

### v0 modules (shipped together)

```text
flower-ai-harness-core      Public API + reference implementation.
                            No vendor SDK deps. No Spring deps.
                            Java 21+. Depends on Flower core.

flower-ai-harness-validator-jackson
                            Reference JSON-to-POJO validator.
                            Depends on core + Jackson. Kept out of core so
                            the framework API remains dependency-light.

flower-ai-harness-test      FakeAiModelGateway, deterministic clock,
                            assertion helpers, JUnit 5 extensions.
                            Designed as a first-class provider, not as a
                            disposable test stub.

flower-ai-harness-spring-ai SpringAiModelGateway adapter.
                            Depends on core + Spring AI Chat Client.
                            Keeps production model integration outside core.

flower-ai-harness-spring-boot-starter
                            Auto-configures SpringAiModelGateway for Spring
                            Boot applications. Depends on spring-ai adapter +
                            Spring Boot autoconfigure.

flower-ai-harness-samples   One domain-neutral runnable sample
                            (text-review-sample). No Spring. No external
                            provider. Uses FakeAiModelGateway.
```

### Deferred modules (created only after v0 + ArchDox feedback)

```text
flower-ai-harness-provider-openai         (only if direct SDK support is needed)
flower-ai-harness-provider-anthropic      (only if direct SDK support is needed)
flower-ai-harness-provider-local          (e.g., Ollama / llama.cpp wrapper)
flower-ai-harness-observability          (TraceListener exporters)
flower-ai-harness-prompt-registry        (only if a real backend is needed)
```

These are listed so v0 contracts stay compatible with them, not because v0
implements them. Each deferred module has a one-paragraph note in
`IMPLEMENTATION_PLAN.md` describing what trigger justifies creating it.

---

## 6. Package Structure (core module)

```text
io.github.parkkevinsb.flower.ai.harness
├── model        AiModelRequest, AiModelResponse, AiModelCall,
│                AiModelCallStatus, ModelId, ProviderOptions
├── gateway      AiModelGateway, RoutingAiModelGateway, GatewayException
├── prompt       PromptTemplate, PromptVersion, PromptBuilder, RenderedPrompt
├── validate     AiSchemaValidator, ValidationResult, ValidationError
├── refine       AiRefinePolicy, RefineDecision, RefineContext
├── finding      AiFinding, AiFindingSeverity, FindingExtractor, FindingSink
├── run          AiHarnessRunContext, AiHarnessRunId, AiHarnessAttempt
├── spec         AiHarnessSpec, AiHarnessSpecBuilder
├── flow         AiHarnessFlowFactory, AiHarnessFlow
│   └── step     PreparePromptStep, AwaitResponseStep,
│                ValidateResponseStep, RefineDecisionStep, EmitFindingsStep
│                (package-private — not part of the public API)
└── spi          TraceListener, AiHarnessClock
```

Test module:

```text
io.github.parkkevinsb.flower.ai.harness.test
├── fake         FakeAiModelGateway, FakeAiModelCall, FakeResponseProgram
├── time         FixedAiHarnessClock
└── assertion   AiFindingAssertions, AiHarnessRunAssertions
```

Public vs. internal:

- Internal step classes are package-private implementation details of
  `AiHarnessFlowFactory`. Hosts wire harnesses through the factory, never by
  instantiating steps directly. This is a key boundary: it lets us reshape
  the step set later without breaking the public API.
- Everything in `spi` is a stable extension point. SPI changes are treated as
  breaking even pre-1.0.

---

## 7. Execution Lifecycle

A single harness run proceeds through five internal Flower steps. The
ten-line lifecycle in `README.md` maps onto them like this:

```text
README step                                Internal step
─────────────────────────────────────────  ──────────────────────────
1. Build input context                     (host caller — outside flow)
2. Render prompt from versioned template   PreparePromptStep
3. Submit model request                    AwaitResponseStep.onEnter
4. Wait for model response                 AwaitResponseStep.onTick
5. Parse structured output                 ValidateResponseStep.onEnter
6. Validate schema                         ValidateResponseStep.onEnter
7. Critique or refine if needed            RefineDecisionStep
8. Produce findings/results                EmitFindingsStep.onEnter
9. Persist / publish                       FindingSink (host adapter)
10. Finish or fail                         (Flower terminal transition)
```

State transitions:

```text
                        ┌──────────────────────────┐
                        │   PreparePromptStep      │
                        │   onEnter: render prompt │
                        │   → done() always         │
                        └──────────┬───────────────┘
                                   │
                                   ▼
              ┌────────────────────────────────────────┐
              │   AwaitResponseStep                    │
              │   onEnter: gateway.submit(req) → call  │
              │            store call handle           │
              │   onTick:  switch(call.poll())         │
              │     PENDING → stay()                   │
              │     READY   → store response; done()   │
              │     FAILED  → done() with error flag   │
              └──────────┬─────────────────────────────┘
                         │
                         ▼
              ┌────────────────────────────────────────┐
              │   ValidateResponseStep                 │
              │   onEnter: validator.validate(resp)    │
              │     store ValidationResult             │
              │     → done() always                    │
              └──────────┬─────────────────────────────┘
                         │
                         ▼
              ┌────────────────────────────────────────┐
              │   RefineDecisionStep                   │
              │   onEnter:                             │
              │     if response failed or invalid:     │
              │       decision = policy.decide(...)    │
              │       switch(decision)                 │
              │         Retry(req') → set next request │
              │                       transition back  │
              │                       to AwaitResponse │
              │         Continue   → done()            │
              │         Fail(...)  → fail(...)         │
              │     else: done()                       │
              └──────────┬─────────────────────────────┘
                         │
                         ▼
              ┌────────────────────────────────────────┐
              │   EmitFindingsStep                     │
              │   onEnter:                             │
              │     T value = ctx.validatedValue()     │
              │     findings = extractor.extract(value)│
              │     sink.accept(findings, ctx)         │
              │     → done()                           │
              └────────────────────────────────────────┘
```

The transition from `RefineDecisionStep` back to `AwaitResponseStep` is the
only non-linear edge. It is implemented via Flower's normal step transition
mechanism, not by mutating the flow graph at runtime. The flow graph is
fixed at construction; refine cycles are loops in the graph, not dynamic
re-wiring.

Flower does not provide a generic flow-level context slot.
`AiHarnessFlowFactory` creates one `AiHarnessRunContext` per harness run and
injects the same context instance into every internal Step constructor. Every
step reads from and writes to that shared context. The factory returns an
`AiHarnessFlow` wrapper containing both the Flower `Flow` to submit and the run
context for inspection/correlation. The context survives across the refine loop
and carries the attempt counter, the current request, the latest call handle,
the latest response, the latest validation result, and the prompt version in
effect.

---

## 8. Connecting Flower Steps and AI Calls

The hard constraint: a Flower worker tick is short and must not block on a
remote model call. The harness honors this with one rule:

> All long-running work happens behind `AiModelGateway`. Steps only submit,
> poll, and react. Steps never block.

Concretely:

- `AwaitResponseStep.onEnter` calls `gateway.submit(request)`. This returns
  an `AiModelCall` handle synchronously. The gateway implementation owns the
  actual asynchrony — it may use a `CompletableFuture`, an HTTP async client,
  a queue worker, virtual threads, or any other mechanism. The step does not
  care.
- `AwaitResponseStep.onTick` calls `call.poll()` which returns one of
  `PENDING`, `READY`, `FAILED`. This call must be cheap and non-blocking.
- If `PENDING`, the step returns `stay()` and Flower moves on to other work.
- If `READY` or `FAILED`, the step transitions out.
- Before submitting a provider call, `AwaitResponseStep` checks
  `AiBudgetPolicy` and tries to acquire an `AiResourceGovernor` permit.
  Budget rejection fails the run before spending another call. Resource denial
  returns `stay()` without blocking or submitting.
- If a run is cancelled, `AwaitResponseStep` calls `AiModelCall.cancel()`,
  marks the harness run `CANCELLED`, persists a snapshot, emits
  `TraceListener.onRunCancelled`, and terminates the Flower flow.

Reasons this is the public contract instead of, say, exposing
`CompletableFuture<AiModelResponse>` directly:

- Polling is the natural fit for tick-driven workflow engines.
- The handle exposes a `callId()` for tracing without forcing futures into
  the trace model.
- Cancellation (`call.cancel()`) is a first-class concept.
- It does not force every provider implementation onto a specific async
  primitive. Providers wrap whatever async client they have.

Provider implementations are responsible for not exhausting their executor
pools. Backpressure, rate limiting, and provider-client timeout enforcement
live inside the provider, not inside the step. The harness timeout bounds how
long the harness waits for a call result; it is not a universal guarantee that
the underlying HTTP/model request has been interrupted. For example, the Spring
AI adapter applies the request timeout to the `CompletableFuture`, while
production applications must still configure Spring AI / HTTP-client timeouts.

---

## 9. Async / Non-Blocking Principles

Rules that v0 enforces and that every later contributor must respect:

1. **No blocking I/O inside any Step.** `onEnter` and `onTick` must return
   in microseconds, not seconds. Anything slower belongs behind
   `AiModelGateway`.
2. **No sleeping.** Steps never call `Thread.sleep`. Delays are expressed as
   "next tick" via `stay()`.
3. **No synchronous network calls in core.** Core has zero network code.
   All network lives in provider modules.
4. **Providers own their threading.** Whether a provider uses platform
   threads, virtual threads, or an HTTP reactor client is a provider concern.
   The contract is: `submit` returns immediately; `poll` returns immediately.
5. **Refine loops do not mutate the flow graph.** Refine is a backward edge
   in a static graph. The graph is constructed once per run.
6. **Steps are not re-entrant across attempts.** Each refine iteration
   increments the attempt counter in `AiHarnessRunContext` and re-enters
   `AwaitResponseStep` with a fresh `AiModelCall`. The previous call handle
   is discarded (and `cancel()`-ed defensively).
7. **All step state lives in `AiHarnessRunContext`.** Steps themselves are
   stateless beyond their configuration.

These rules collectively ensure that one Flower worker thread can multiplex
many concurrent harness runs without head-of-line blocking from any single
model call.

---

## 9.1 Operational State, Recovery, and Governance

Flower durable mode and harness run persistence are intentionally different:

```text
Flower checkpoint
  flowType, flowKey, currentStepId, stepNo, recovery policy

AiHarnessRunSnapshot
  runId, harnessId, promptVersion, status, attempt, currentRequest,
  provider call id, latest response, terminal reason

Host persistence
  business request, document snapshot, user-visible status, findings, audit
```

Flower can restore the flow position. It cannot decide what a half-finished AI
provider call means after restart. That decision belongs to the harness and the
host application because it depends on provider semantics, cost tolerance,
idempotency, and whether late responses can be correlated.

The first operational contracts are:

- `AiHarnessRunStatus`: domain-level status such as `QUEUED`,
  `WAITING_PROVIDER`, `VALIDATING`, `REFINING`, `SUCCEEDED`, `FAILED`, and
  `CANCELLED`.
- `AiHarnessRunStore`: host-supplied snapshot storage. Core ships no-op and
  in-memory implementations only.
- `AiCancellationToken`: per-run cancellation signal. The token belongs in
  `RunOverrides`, not in the shared spec, so cancelling one run does not cancel
  every run of the same harness type.
- `AiBudgetPolicy`: pre-submit guardrail for retries and model fallback.
- `AiResourceGovernor`: non-blocking concurrency/rate gate. `tryAcquire`
  returns empty when no slot is available; the Flower step stays and tries
  again later.
- `AiRecoveryPolicy`: explicit decision point for rebuilding a harness flow
  from an `AiHarnessRunSnapshot`. Core ships a conservative policy only.

Recovery in the current core is intentionally narrow. A host loads a persisted
snapshot and calls `AiHarnessFlowFactory.createRecoveredFlow(...)`. The factory
validates that the snapshot belongs to the same harness id and prompt version,
restores durable harness state into `AiHarnessRunContext`, asks the recovery
policy for a decision, then builds a new Flower flow.

Supported recovery decisions are:

- `RetryCurrentRequest`: start from `AwaitResponseStep` and submit the current
  model request again.
- `ContinueFromFlow`: currently equivalent to retrying the current request
  when one exists. Full checkpoint-position continuation belongs to host /
  Flower durable integration later.
- `FailRecoverable`: mark the run failed with a clear terminal reason.
- `MarkCancelled`: mark the run cancelled without calling the provider.

The current implementation does not restore an active provider call handle.
Call handles are process-local and provider-specific; after a restart the safe
generic behavior is either retry, cancel, or fail. The built-in conservative
policy is therefore at-least-once: it may submit the same `currentRequest`
again and duplicate provider cost. Expensive or non-idempotent harnesses should
use a custom `AiRecoveryPolicy` that fails recoverably or routes the run to
manual review instead of retrying. Database-backed stores, startup scanners,
provider call reconciliation, and replay are outside core.

---

## 10. ArchDox Integration Boundary

ArchDox integrates by supplying three implementations and one configuration:

```text
ArchDox-provided                            Core-defined seam
──────────────────────────────────────────  ────────────────────────────────
DocumentQaPromptBuilder                     PromptBuilder<DocumentSnapshot>
DocumentFindingExtractor                    FindingExtractor<DocumentReview>
ArchDoxFindingEventSink                     FindingSink
ArchDox-configured AiHarnessSpec            AiHarnessSpec<DocumentSnapshot,
                                                          DocumentReview>
```

ArchDox wires these inside a Spring `@Configuration` class that produces:

- a singleton `AiModelGateway` (initially `FakeAiModelGateway`, later a
  routing gateway with provider modules)
- a singleton `AiHarnessFlowFactory<DocumentSnapshot, DocumentReview>` for
  the document QA harness
- a `DocumentQaFlowListener` (using ArchDox's existing Bloom pattern, as in
  `abyss-runner`) that subscribes to document events and submits the flow to
  the Flower worker

The integration boundary tests both directions:

- ArchDox must be implementable using *only* the public API of
  `flower-ai-harness-core` (no internal classes, no reflection hacks).
- `flower-ai-harness-core` must compile and run its test suite with *no*
  ArchDox dependency on the classpath.

If either direction breaks during ArchDox integration, that is a signal that
the boundary needs adjustment — see `IMPLEMENTATION_PLAN.md` for the
adjustment protocol.

---

## 11. Extension Points

These are the points designed to remain stable while internals change.

### 11.1 Adding a provider

Implement `AiModelGateway`. Decide on a `ModelId` provider prefix (e.g.,
`"openai"`, `"anthropic"`, `"local"`). Accept `ProviderOptions` as an
opaque map and interpret your own keys. Map your provider's async primitive
to `AiModelCall`. Publish as a separate module (`flower-ai-harness-provider-*`).

For Java/Spring applications, the preferred production provider module is
`flower-ai-harness-spring-ai`:

```text
SpringAiModelGateway implements AiModelGateway
  -> delegates to Spring AI ChatClient / ChatModel
```

This keeps `flower-ai-harness` out of the provider-SDK business early and lets
Spring AI handle model integration. Direct SDK adapters remain possible later
when they offer clear value over Spring AI.

The host application wires either a single provider gateway or a
`RoutingAiModelGateway` that dispatches by `ModelId.provider()`.

Spring Boot applications can use `flower-ai-harness-spring-boot-starter`.
The starter creates:

- a dedicated `flowerAiHarnessModelExecutor`,
- a `SpringAiModelResolver` when there is a single `ChatClient`,
- a `SpringAiModelGateway` when a resolver is available and no
  `AiModelGateway` has already been defined.

If multiple `ChatClient` beans exist, the starter does not guess routing.
The host supplies a `SpringAiModelResolver`.

### 11.2 Adding a validator strategy

Implement `AiSchemaValidator<T>`. v0 ships a Jackson-POJO reference
implementation in `flower-ai-harness-validator-jackson`, not in core. Later
candidates: JSON Schema validator, Avro, Bean Validation, or a domain-specific
structural validator. All plug in through the same interface; the spec selects
one per harness.

### 11.3 Adding a refine strategy

Implement `AiRefinePolicy`. v0 ships with `MaxAttemptsRefinePolicy` (retry
the same model) and `ModelFallbackRefinePolicy` (switch model IDs according
to `ModelFallbackPlan`) so hosts can separate cheap first-pass review from
strong fallback review without changing the flow graph.
candidates: critique-then-revise, expert-router, escalation-to-human. None
require changes to flow steps.

### 11.4 Adding observability

Implement `TraceListener`. The flow factory accepts an optional list of
listeners. Events: `runStarted`, `requestSubmitted`, `responseReceived`,
`validationCompleted`, `refineTriggered`, `runCompleted`, `runFailed`.
Listeners are passive — they cannot alter flow control.

### 11.5 Adding prompt versioning backend

Implement `PromptTemplate.Loader` (a small SPI over the `PromptTemplate`
record). v0 supports code-defined templates only. A resource-loader
implementation can ship in v0.1 without breaking the spec API. A
database-backed registry is a later module.

### 11.6 Adding persistence

The harness exposes `AiHarnessRunStore` for framework-level run snapshots.
Core ships no-op and in-memory stores only; JDBC/JPA/Redis/file stores belong
in optional modules or the host application. Business persistence remains the
host's job, achieved by supplying a `FindingSink` and domain repositories.
This keeps core decoupled from any storage engine while still giving
operational users a stable run-state boundary.

---

## 12. Multi-Model / Multi-Provider Strategy

Provider neutrality is a non-negotiable design property of v0, even though
v0 only ships one real provider implementation (the fake one).

### 12.1 `ModelId` is structured

```text
ModelId(provider, name)
  e.g., ModelId("openai", "gpt-4o")
        ModelId("anthropic", "claude-opus-4-7")
        ModelId("local", "llama3-8b-q4")
        ModelId("fake", "echo")

ModelId.parse("anthropic:claude-opus-4-7")
```

Provider routing happens on `ModelId.provider()`. The model name is opaque
to core.

### 12.2 `ProviderOptions` is an opaque bag

Provider-specific knobs (temperature, top-p, system-prompt-position, JSON
mode, tool definitions) live in `ProviderOptions`, an immutable typed map.
Core does not validate or interpret keys. Each provider documents its own
keys. This is the only way provider-specific config enters the request
without polluting core.

### 12.3 Selection levers

A `ModelId` can be set at three levels, with the rightmost winning:

```text
spec default model  →  run-level override  →  per-request override
```

- **Spec default**: set when building `AiHarnessSpec`.
- **Run-level**: passed via `AiHarnessFlowFactory.createFlow(input, overrides)`.
- **Per-request**: a refine iteration can switch models (e.g., escalate
  from a small validator model to a stronger reviewer model).

### 12.4 Routing

`RoutingAiModelGateway` is a core implementation of `AiModelGateway` that
delegates by `ModelId.provider()`. It is the natural composition point for
multi-provider deployments. v0 ships it because it is small and proves the
abstraction works without needing any real provider.

In Spring applications, the recommended production route is usually:

```text
ModelId provider prefix -> SpringAiModelGateway -> Spring AI model selection
```

Direct `provider-openai`, `provider-anthropic`, and `provider-local` modules
are lower priority than the Spring AI adapter because Spring AI already covers
the common Java provider-integration surface.

### 12.5 Core has zero vendor SDKs on the classpath

Verified by:

- Maven `dependency:tree` check
- a sanity test in `flower-ai-harness-core` that asserts no
  `com.openai.*`, `com.anthropic.*`, or `dev.langchain4j.*` class is
  reachable.

---

## 13. Non-Goals (v0)

The following are explicitly out of scope for the first version, even though
the architecture is shaped to remain compatible with most of them later.

- **Full durable execution.** Core does not ship a database-backed run store,
  startup scanner, provider-call reconciliation loop, or replay engine.
  Snapshot-based recovery exists, but the host application still owns durable
  storage and Flower checkpoint/resume wiring.
- **Distributed execution.** Single JVM only.
- **Streaming model output.** v0 awaits a complete response. Token streaming
  is a future provider-level capability and would require a new
  `AiModelCallStatus` value (`STREAMING`) which can be added compatibly.
- **Tool calling / function calling.** v0 does not model tools. The
  `ProviderOptions` bag can carry vendor-specific tool definitions if a
  provider supports them, but the harness lifecycle does not orchestrate
  multi-turn tool loops. This is the single largest deliberate omission.
- **Multi-agent handoff.** No agent-to-agent routing primitives.
- **RAG.** No vector store, no embedding gateway, no retriever interface.
  Document loading is the host's job.
- **Hosted dashboard / registry.** None of the commercial layers from
  `STRATEGY.md` exist yet.
- **HTTP server, REST endpoints, async event bus.** Hosts bring their own.
- **Prompt marketplace / discovery.** Prompts are first-class types but
  there is no registry beyond what the host provides.

---

## 14. Open Questions (with current direction)

These are questions where the brief left options open. The current
direction is stated; revisit after ArchDox integration.

| # | Question                                                          | Current direction                                                                                                                  |
|---|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `AiModelGateway` async style: fully async vs sync-with-executor?  | Polling handle (`AiModelCall`). Providers internally use whatever async client they want. Core never blocks.                       |
| 2 | Minimal structured format for `AiFinding`?                        | `severity`, `code`, `message`, `evidence` (free-text), `location` (opaque `String`), `attributes` (open map). No domain location.  |
| 3 | Schema validation: JSON Schema, records, Jackson, or pluggable?   | Pluggable `AiSchemaValidator<T>`. v0 ships a Jackson-POJO reference impl in a separate module. JSON Schema is a later module.      |
| 4 | Prompt versioning representation?                                 | v0: code-defined `PromptTemplate` records with explicit `PromptVersion`. Loader SPI exists so resource/DB backends can plug in.    |
| 5 | Trace metadata in core vs. observability module?                  | Minimal trace events emitted from core via `TraceListener` SPI. Exporters (file, OTEL, custom) live in an external module.         |
| 6 | Refine loops: steps, internal state, or mini-policy object?       | Static flow graph with a backward edge from `RefineDecisionStep` to `AwaitResponseStep`. Policy is a separate object on the spec.  |
| 7 | Spring Boot auto-config value?                                    | Thin starter only: single `ChatClient` auto-wiring, host-provided `SpringAiModelResolver` for multi-client routing, dedicated executor properties. |
| 8 | First sample: document QA, generic text review, or domain-neutral document inspection? | Generic text-review (`text-review-sample`). Domain-neutral. ArchDox lives in its own repo as the real validation, not as the public sample. |

Each open question is also tracked in `IMPLEMENTATION_PLAN.md` against the
phase where it must be re-evaluated.

---

## 15. Architectural Invariants

These are the rules a reviewer should be able to check against any future
PR.

1. `flower-ai-harness-core` has no compile dependency on any vendor SDK,
   on Spring, or on ArchDox.
2. No public type in core references an ArchDox class, package, or concept.
3. No `Step` blocks. No `onTick` returns later than the next tick boundary
   under normal conditions.
4. `AiHarnessRunContext` is the only shared state between steps.
5. Internal flow step classes are package-private.
6. Every public type has a clear single responsibility documented in
   `API_DESIGN.md`.
7. The fake provider is treated as a peer of any future real provider, not
   as a downgrade.
8. Adding a new provider requires zero changes to core.
9. Adding a new validation strategy requires zero changes to core.
10. Adding a new refine strategy requires zero changes to core.

Violating any of these invariants is treated as breaking the architecture,
not as a feature.

# API_DESIGN.md

This document defines the first-version public API of `flower-ai-harness-core`,
plus the test-module public API where it materially affects core contracts.

The signatures below are **sketches**, not implementations. They are intended
to be precise enough that a skeleton can be generated from them, but they are
expected to be refined during implementation. Every type listed includes:

- **Why** — the problem it solves
- **First-version responsibilities** — what it does in v0
- **Deliberate omissions** — what it intentionally does not do in v0
- **Future extensions** — directions for v0.x and beyond
- **ArchDox separation** — what the ArchDox-side counterpart looks like,
  and why core stays neutral

Conventions:

- Java 21+ language level.
- Records for immutable data.
- Sealed interfaces for closed alternatives.
- Generic input/output type parameters where appropriate.
- No external dependencies in core except `flower` itself and JDK.
- Jackson lives in `flower-ai-harness-validator-jackson`; core contracts do not
  require it.

---

## 0. Cross-cutting types

These small value types appear across the model, prompt, and gateway APIs.

### `ModelId`

```java
package io.github.flowerjvm.flower.ai.harness.model;

public record ModelId(String provider, String name) {
    public static ModelId parse(String spec);   // "openai:gpt-4o"
    public String asString();                    // "openai:gpt-4o"
}
```

- **Why**: routing model selection across providers without leaking vendor
  types into the request shape.
- **First-version**: provider/name parsing. Equality. `asString()`.
- **Omissions**: no version semantics (`@1.0.0`), no model registry, no
  capability metadata (max tokens, supports-tools, etc.).
- **Future**: optional `version` field; a `ModelCatalog` SPI to query
  capabilities.
- **ArchDox separation**: ArchDox passes `ModelId` values as strings from
  configuration. Document concepts never reach this type.

### `ProviderOptions`

```java
public final class ProviderOptions {
    public static ProviderOptions empty();
    public static ProviderOptions of(Map<String, Object> options);
    public ProviderOptions with(String key, Object value);
    public Optional<Object> get(String key);
    public Map<String, Object> asMap();   // unmodifiable
}
```

- **Why**: a single, immutable, opaque place for provider-specific knobs
  (temperature, top-p, JSON mode flag, vendor tool defs). Keeps
  `AiModelRequest` clean.
- **First-version**: immutable typed map with safe accessors.
- **Omissions**: no key validation. Core does not know which keys are valid
  for any provider.
- **Future**: provider-defined typed accessors via static helpers in each
  provider module (`OpenAiOptions.temperature(...)`).
- **ArchDox separation**: ArchDox does not set provider options. The host
  application's configuration layer does. ArchDox passes already-built
  `ProviderOptions` values through.

---

## 1. `AiModelRequest`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.model;

public record AiModelRequest(
    ModelId modelId,
    RenderedPrompt prompt,
    ProviderOptions options,
    Duration timeout
) {
    public AiModelRequest {
        Objects.requireNonNull(modelId);
        Objects.requireNonNull(prompt);
        if (options == null) options = ProviderOptions.empty();
        if (timeout == null) timeout = Duration.ofSeconds(60);
    }

    public AiModelRequest withModelId(ModelId next);
    public AiModelRequest withPrompt(RenderedPrompt next);
    public AiModelRequest withOptions(ProviderOptions next);
}
```

- **Why**: the unit of work submitted to a provider. Provider-neutral.
- **First-version responsibilities**: carry the rendered prompt, model
  selection, opaque provider options, and a timeout hint. Be immutable.
  Provide builder-style `with*` for refine cycles that adjust one field.
- **Deliberate omissions**:
  - no streaming flag (no streaming in v0),
  - no tool definitions field (tools are out of v0 scope; if a provider
    supports them they go through `ProviderOptions`),
  - no system/user role split — the `RenderedPrompt` already encapsulates
    structure (see §3),
  - no token budgets — those are provider-specific options.
- **Future**: optional `streaming`, `Tools`, or `ResponseFormat` concepts.
  Adding record components is not binary/source compatible, so post-v0 API
  growth should use a builder, a sibling request type, or an options object
  rather than assuming records can expand freely.
- **Timeout note**: `timeout` is a harness/provider-adapter contract. It
  bounds the call handle as observed by the harness, but each production
  provider must still configure its own HTTP/client timeout where supported.
- **ArchDox separation**: ArchDox builds an `AiModelRequest` indirectly,
  through `PromptBuilder` (which yields a `RenderedPrompt`) and the spec's
  default `ModelId`. ArchDox does not subclass or wrap `AiModelRequest`.

---

## 2. `AiModelResponse`

### Sketch

```java
public record AiModelResponse(
    String rawText,
    ModelId modelId,
    ResponseMetadata metadata
) {
    public record ResponseMetadata(
        Optional<Integer> inputTokens,
        Optional<Integer> outputTokens,
        Optional<Duration> latency,
        Optional<String> finishReason,
        Map<String, String> providerTrace   // unmodifiable
    ) {
        public static ResponseMetadata empty();
    }
}
```

- **Why**: a provider-neutral capture of "the model said something." The raw
  text is preserved verbatim — schema parsing is a separate step.
- **First-version responsibilities**: carry the raw text and minimal
  metadata.
- **Deliberate omissions**:
  - no parsed structure — that is the validator's job; mixing them couples
    parsing with transport,
  - no per-token data, no logprobs,
  - no streaming events.
- **Future**: optional `List<Chunk>` for streaming; optional structured-output
  parsed payload if a provider returns one natively (still wrapped, not
  exposed).
- **ArchDox separation**: ArchDox never reads `rawText` directly. It reads
  the validated typed value produced by `AiSchemaValidator<T>`.

---

## 3. `RenderedPrompt`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.prompt;

public record RenderedPrompt(
    List<Message> messages,
    PromptVersion version
) {
    public record Message(Role role, String content) {}
    public enum Role { SYSTEM, USER, ASSISTANT }
}
```

- **Why**: a structured prompt that fits the conversational model used by
  most providers without locking into any of them.
- **First-version responsibilities**: hold an ordered list of role-tagged
  messages plus the prompt version that produced it.
- **Deliberate omissions**:
  - no template substitution at this layer — that's `PromptBuilder`'s job,
  - no tool/function messages (out of scope),
  - no attachment/multimodal content.
- **Future**: a `Content` sealed type per message to carry text, images, or
  document references; multimodal support.
- **ArchDox separation**: ArchDox renders documents into `RenderedPrompt`
  via its `DocumentQaPromptBuilder`. The document type itself never appears
  in core.

### `PromptTemplate`, `PromptVersion`, `PromptBuilder`

```java
public record PromptVersion(String id, String version) {
    public String asString();   // "document-qa@1.3.0"
}

public record PromptTemplate(PromptVersion version, String template) {
    // v0 keeps it a single string; the host's PromptBuilder decides how to
    // turn it into messages.
}

public interface PromptBuilder<I> {
    RenderedPrompt build(I input, AiHarnessRunContext ctx);
}
```

- **Why**: provide first-class identity (`PromptVersion`) for prompts so
  runs are traceable, while letting hosts decide how prompts are assembled.
- **First-version responsibilities**: identity, host-supplied builder.
- **Deliberate omissions**: no template engine, no resource loading, no
  registry. v0 templates are declared in code.
- **Future**: `PromptTemplateLoader` SPI (classpath, filesystem, DB).
- **ArchDox separation**: `DocumentQaPromptBuilder implements
  PromptBuilder<DocumentSnapshot>` lives in ArchDox.

---

## 4. `AiModelCall` + `AiModelCallStatus`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.model;

public enum AiModelCallStatus { PENDING, READY, FAILED, CANCELLED }

public interface AiModelCall {
    String callId();
    AiModelCallStatus poll();             // non-blocking; cheap; idempotent
    AiModelResponse result();             // throws IllegalStateException unless READY
    Throwable error();                    // throws IllegalStateException unless FAILED
    void cancel();                        // best-effort
}
```

- **Why**: the non-blocking handle that lets Flower steps drive AI calls
  without ever blocking a tick.
- **First-version responsibilities**: status polling, terminal-state
  accessors, identity, cancellation.
- **Deliberate omissions**:
  - no streaming events (no token-by-token callback),
  - no progress percentage,
  - no listener-style subscribers — polling only.
- **Future**: a sibling `AiModelStreamingCall` extending the contract with
  an event stream; same gateway, separate handle type. Adding it does not
  break the polling API.
- **ArchDox separation**: ArchDox never sees `AiModelCall`. It is purely an
  internal interaction between steps and the gateway.

---

## 5. `AiModelGateway`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.gateway;

public interface AiModelGateway {
    AiModelCall submit(AiModelRequest request);
}

public class RoutingAiModelGateway implements AiModelGateway {
    public RoutingAiModelGateway(Map<String, AiModelGateway> byProvider);
    public RoutingAiModelGateway withFallback(AiModelGateway fallback);
    @Override
    public AiModelCall submit(AiModelRequest request);
    // dispatches on request.modelId().provider()
}

public class GatewayException extends RuntimeException { ... }
```

- **Why**: single provider port. The system has exactly one place where
  "talk to a model" happens. Everything else composes around it.
- **First-version responsibilities**: accept a request, return a handle.
  Routing implementation included so multi-provider setups work without
  custom glue.
- **Deliberate omissions**:
  - no batching API,
  - no built-in caching,
  - no built-in retry (refine handles retries at the harness lifecycle
    level; transport-level retry is a provider concern),
  - no provider-specific rate-limit policy (host uses `AiResourceGovernor`
    before submission; providers may still enforce their own limits).
- **Future**: `BatchAiModelGateway` for providers that batch; a
  `CachingAiModelGateway` decorator for idempotent prompts;
  `Observed` decorators that emit trace events. The preferred first production
  adapter is `SpringAiModelGateway` in `flower-ai-harness-spring-ai`, delegating
  to Spring AI ChatClient / ChatModel. Spring Boot users can add
  `flower-ai-harness-spring-boot-starter` to auto-configure that gateway when a
  single `ChatClient` or a user-provided `SpringAiModelResolver` is present.
- **ArchDox separation**: ArchDox injects a single `AiModelGateway` bean.
  Which provider sits behind it is a host-application concern. ArchDox does
  not implement `AiModelGateway` itself.

---

## 6. `AiSchemaValidator<T>`, `ValidationResult<T>`, `ValidationError`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.validate;

public interface AiSchemaValidator<T> {
    ValidationResult<T> validate(AiModelResponse response);
}

public sealed interface ValidationResult<T> {
    boolean isValid();
    record Valid<T>(T value) implements ValidationResult<T> {
        public boolean isValid() { return true; }
    }
    record Invalid<T>(List<ValidationError> errors) implements ValidationResult<T> {
        public boolean isValid() { return false; }
    }
}

public record ValidationError(
    String path,        // optional JSONPath-ish locator, may be ""
    String code,        // stable machine code; e.g., "MISSING_FIELD"
    String message      // human-readable
) {}
```

A reference implementation lives in `flower-ai-harness-validator-jackson`:

```java
package io.github.flowerjvm.flower.ai.harness.validator.jackson;

public final class JacksonPojoSchemaValidator<T> implements AiSchemaValidator<T> {
    public JacksonPojoSchemaValidator(Class<T> type, ObjectMapper mapper);
    // parses response.rawText() as JSON into T; collects parse errors and
    // field-level structural problems that Jackson can detect. Required-field
    // semantics that Jackson would otherwise treat as null must be supplied by
    // a post-bind check or a custom AiSchemaValidator.
}
```

- **Why**: structured output is a first-class feature. The harness must
  treat "did the model return what we asked for?" as a fundamental
  lifecycle decision, not an afterthought.
- **First-version responsibilities**: validate raw text against an
  expected structure; return either the typed value or a list of errors.
- **Deliberate omissions**:
  - no auto-repair (`Invalid` does not include a "fixed" value),
  - no partial parse (no half-valid result),
  - no JSON Schema dependency in core.
- **Future**: JSON Schema validator (`flower-ai-harness-validator-jsonschema`),
  Bean Validation integration, structural diff for refine prompts.
- **ArchDox separation**: ArchDox declares its expected output type
  (`DocumentReviewDraft`, say) and provides either a
  `JacksonPojoSchemaValidator<DocumentReviewDraft>` or a custom validator.
  Core does not know what document fields are.

---

## 7. `AiRefinePolicy` + `RefineDecision`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.refine;

public interface AiRefinePolicy {
    RefineDecision decide(RefineContext ctx);
}

public record RefineContext(
    AiHarnessRunContext run,
    AiModelRequest lastRequest,
    AiModelResponse lastResponse,         // may be null on FAILED call
    ValidationResult<?> lastValidation,   // may be null if call failed
    Throwable callError,                   // null unless transport failed
    int attempt,
    int maxAttempts
) {}

public sealed interface RefineDecision {
    record Continue() implements RefineDecision {}
    record Retry(AiModelRequest nextRequest) implements RefineDecision {}
    record Fail(String reason) implements RefineDecision {}
}
```

A reference implementation:

```java
public final class MaxAttemptsRefinePolicy implements AiRefinePolicy {
    public MaxAttemptsRefinePolicy(int maxAttempts);
    // On invalid response: rebuilds prompt with errors appended as a system
    // message, returns Retry. On transport error: returns Retry up to N
    // attempts, then Fail. On valid response: Continue.
}

public final class ModelFallbackRefinePolicy implements AiRefinePolicy {
    public ModelFallbackRefinePolicy(ModelFallbackPlan plan);
    // Uses the plan to choose the next attempt's ModelId. Validation failures
    // still append validation errors to the prompt; transport failures retry
    // the same prompt on the next planned model.
}

public final class ModelFallbackPlan {
    public static Builder builder();
    public static ModelFallbackPlan single(ModelId modelId, int maxAttempts);
    public int maxAttempts();
    public ModelId modelForAttempt(int attempt);
}
```

- **Why**: retry/refine is a lifecycle, not ad-hoc loop code. Policies are
  pluggable so harnesses can choose simple retry or richer critique-revise
  flows without touching the framework.
- **First-version responsibilities**: decide whether to continue, retry
  with a new request, fallback to another model, or fail. Carry enough
  context to make that call, including the current `AiHarnessRunContext`.
- **Deliberate omissions**:
  - no automatic critique LLM call (that would couple the policy to a
    gateway; if needed, it can be implemented as a custom policy that uses
    an injected gateway),
  - no backoff scheduling (policy returns "retry next tick"; if delay is
    needed, it can be a future extension on `RefineDecision.Retry`),
  - no human-in-the-loop branch in v0.
- **Future**: `Retry(delay: Duration)`, `Escalate(humanReviewer)`,
  policies that compose multiple strategies.
- **ArchDox separation**: ArchDox can use `MaxAttemptsRefinePolicy` for
  simple retry or `ModelFallbackRefinePolicy` for cheap-to-strong model
  fallback. ArchDox-specific escalation rules live in ArchDox, not in core.

---

## 8. `AiFinding` + `AiFindingSeverity`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.finding;

public enum AiFindingSeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

public record AiFinding(
    String code,                        // stable machine code, host-defined
    AiFindingSeverity severity,
    String message,
    String evidence,                    // verbatim or summarized supporting text
    String location,                    // opaque locator string, host-interpreted
    Map<String, String> attributes      // unmodifiable
) {
    public static AiFinding of(String code, AiFindingSeverity severity, String message);
    public AiFinding withEvidence(String evidence);
    public AiFinding withLocation(String location);
    public AiFinding withAttribute(String key, String value);
}
```

- **Why**: the harness produces structured findings, not free text.
  Findings are the public contract between an AI step and the surrounding
  business workflow.
- **First-version responsibilities**: capture severity, machine code,
  human message, evidence text, and a host-interpreted location string.
- **Deliberate omissions**:
  - no domain-specific location type — document page/paragraph/character
    offsets are an ArchDox concept,
  - no remediation suggestion field (a future extension if it repeats
    across domains),
  - no confidence score in v0 (open question; can be added compatibly).
- **Future**: optional `confidence: double`, optional
  `remediation: String`, optional structured `evidenceRef`.
- **ArchDox separation**: ArchDox's `DocumentFinding` extends or wraps
  `AiFinding` with document-specific structure (page, paragraph, citation).
  The mapping happens in ArchDox via its `FindingExtractor` /
  `FindingMapper`.

### `FindingExtractor<T>`

```java
public interface FindingExtractor<T> {
    List<AiFinding> extract(T value, AiHarnessRunContext ctx);
}
```

- **Why**: bridge between a validated typed payload and the universal
  finding list.
- **First-version**: pure mapping from `T` plus the current run context to
  `List<AiFinding>`.
- **ArchDox separation**: ArchDox supplies
  `DocumentReviewDraftExtractor implements FindingExtractor<DocumentReviewDraft>`
  that maps its internal draft type into `AiFinding`s.

### `FindingSink`

```java
public interface FindingSink {
    void accept(List<AiFinding> findings, AiHarnessRunContext ctx);
}
```

- **Why**: the host-side publication boundary. Keeps storage out of core.
- **First-version**: synchronous accept with a strict "fast only" contract.
  Implementations should publish to an event bus, enqueue a background write,
  or store in memory for tests. They must not perform slow JDBC/network I/O on
  the Flower worker tick.
- **Deliberate omissions**: no async variant in v0. If sinks need to be
  async, they internally hand off.
- **Future**: an async variant returning a `CompletionStage<Void>` if
  real usage shows persistence is regularly the slow path.
- **ArchDox separation**: `ArchDoxFindingEventSink implements FindingSink`
  should publish or enqueue findings quickly. Direct database persistence
  belongs to an ArchDox-side worker/listener so the Flower worker tick is not
  blocked by JDBC.

---

## 9. `AiHarnessRunContext`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.run;

public final class AiHarnessRunContext {
    public AiHarnessRunId runId();
    public String harnessId();
    public PromptVersion promptVersion();
    public int attempt();
    public AiHarnessRunStatus status();
    public Instant startedAt();
    public AiCancellationToken cancellationToken();

    // current state of the run
    public AiModelRequest currentRequest();
    public Optional<AiModelCall> currentCall();
    public Optional<AiModelResponse> latestResponse();
    public Optional<ValidationResult<?>> latestValidation();
    public Optional<String> terminalReason();

    // host extension surface
    public <T> Optional<T> attribute(AttributeKey<T> key);
    public <T> void putAttribute(AttributeKey<T> key, T value);

    public static final class AttributeKey<T> {
        public static <T> AttributeKey<T> of(String name, Class<T> type);
    }

    // Internal mutators are package-private or exposed through an internal
    // companion type. Host code can read lifecycle state and set attributes,
    // but only framework steps advance request/call/response/validation state.
}

public record AiHarnessRunId(String value) {
    public static AiHarnessRunId random();
}
```

- **Why**: the per-run state object threaded through every step. Holds
  identity, lifecycle state, and a typed attribute bag for host
  extensions (e.g., a tenant ID, a correlation ID, a request submitter).
- **First-version responsibilities**: identity, attempt counter, AI-run
  status, current request/call/response/validation, cancellation token,
  terminal reason, typed attributes.
- **Deliberate omissions**:
  - no built-in OpenTelemetry context propagation (left to listeners),
  - no direct DB persistence — snapshots go through `AiHarnessRunStore`,
  - no global lookup — the context is always passed explicitly.
- **Future**: a `TraceContext` field if observability adoption demands
  it; typed correlation IDs.
- **ArchDox separation**: ArchDox attaches its tenant/org IDs via
  `putAttribute(ArchDoxKeys.TENANT_ID, ...)`. The keys are declared in
ArchDox; core knows nothing about them.

---

## 10. Operational Run Control

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.run;

public enum AiHarnessRunStatus {
    QUEUED, PREPARING_PROMPT, WAITING_PROVIDER, VALIDATING, REFINING,
    EMITTING_FINDINGS, SUCCEEDED, FAILED, CANCELLED
}

public record AiHarnessRunSnapshot(...);

public interface AiHarnessRunStore {
    void save(AiHarnessRunSnapshot snapshot);
    Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId);
}
```

```java
package io.github.flowerjvm.flower.ai.harness.control;

public interface AiCancellationToken {
    Optional<String> cancellationReason();
}

public interface AiBudgetPolicy {
    AiBudgetDecision evaluate(AiBudgetContext context);
}

public interface AiResourceGovernor {
    Optional<AiResourcePermit> tryAcquire(AiModelRequest request,
                                          AiHarnessRunContext context);
}
```

```java
package io.github.flowerjvm.flower.ai.harness.recovery;

public record AiRecoveryContext(
    AiHarnessRunSnapshot snapshot,
    AiHarnessSpec<?, ?> spec
) {}

public sealed interface AiRecoveryDecision {
    record RetryCurrentRequest(AiModelRequest request) implements AiRecoveryDecision {}
    record ContinueFromFlow() implements AiRecoveryDecision {}
    record FailRecoverable(String reason) implements AiRecoveryDecision {}
    record MarkCancelled(String reason) implements AiRecoveryDecision {}
}

public interface AiRecoveryPolicy {
    AiRecoveryDecision decide(AiRecoveryContext context);
    static AiRecoveryPolicy conservative();
}
```

- **Why**: Flower knows how to resume a flow position, but it does not know
  AI provider semantics, cost risk, provider call ids, or late-response
  handling. These types record and control the AI-run layer above Flower.
- **First-version**: no-op store, in-memory store, manual cancellation token,
  max-attempts budget guard, semaphore-based in-JVM concurrency guard, and a
  conservative snapshot recovery policy.
- **Deliberate omissions**:
  - no JDBC/JPA store in core,
  - no provider-specific cancellation guarantee,
  - no token estimator,
  - no durable replay engine,
  - no automatic startup scanner or provider-call reconciliation.
- **Future**: provider-specific cost estimators, distributed rate limiters,
  database-backed run stores, and richer recovery policies that coordinate
  with Flower durable checkpoints.
- **Recovery note**: the built-in conservative recovery policy retries the
  persisted `currentRequest` and can therefore duplicate provider calls/cost.
  Hosts with expensive or non-idempotent AI work should choose a custom
  `AiRecoveryPolicy` that fails recoverably or requires manual review.
- **ArchDox separation**: ArchDox stores user-facing document QA state and
  findings in its own DB. The harness store records framework-level run
  state that ArchDox can correlate by `runId`.

---

## 11. `AiHarnessSpec<I,T>`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.spec;

public final class AiHarnessSpec<I, T> {
    public String harnessId();
    public ModelId defaultModelId();
    public ProviderOptions defaultOptions();
    public Duration defaultTimeout();
    public PromptVersion promptVersion();
    public PromptBuilder<I> promptBuilder();
    public AiSchemaValidator<T> validator();
    public AiRefinePolicy refinePolicy();
    public AiBudgetPolicy budgetPolicy();
    public AiResourceGovernor resourceGovernor();
    public AiHarnessRunStore runStore();
    public FindingExtractor<T> findingExtractor();
    public FindingSink findingSink();
    public List<TraceListener> traceListeners();

    public static <I, T> Builder<I, T> builder();

    public static final class Builder<I, T> {
        public Builder<I, T> harnessId(String id);
        public Builder<I, T> defaultModelId(ModelId id);
        public Builder<I, T> defaultOptions(ProviderOptions opts);
        public Builder<I, T> defaultTimeout(Duration t);
        public Builder<I, T> promptVersion(PromptVersion v);
        public Builder<I, T> promptBuilder(PromptBuilder<I> pb);
        public Builder<I, T> validator(AiSchemaValidator<T> v);
        public Builder<I, T> refinePolicy(AiRefinePolicy p);
        public Builder<I, T> budgetPolicy(AiBudgetPolicy p);
        public Builder<I, T> resourceGovernor(AiResourceGovernor governor);
        public Builder<I, T> runStore(AiHarnessRunStore store);
        public Builder<I, T> findingExtractor(FindingExtractor<T> fe);
        public Builder<I, T> findingSink(FindingSink fs);
        public Builder<I, T> addTraceListener(TraceListener l);
        public AiHarnessSpec<I, T> build();
    }
}
```

- **Why**: one immutable description of "what a harness type is." Built
  once at application startup, reused for every run of that harness type.
- **First-version**: builder with required fields enforced at `build()`.
- **Deliberate omissions**:
  - no live mutation,
  - no per-tenant variants in the spec itself (host can hold a map of
    specs).
- **Future**: spec composition (e.g., "this harness wraps that harness");
  variant selection at run time via a `SpecResolver`.
- **ArchDox separation**: ArchDox builds one or more specs in
  `@Configuration`. The spec type parameters are ArchDox types
  (`DocumentSnapshot`, `DocumentReviewDraft`). The spec itself is a core
  class.

---

## 12. `AiHarnessFlowFactory<I,T>`

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.flow;

public record AiHarnessFlow(
    Flow flow,
    AiHarnessRunContext context
) {}

public final class AiHarnessFlowFactory<I, T> {

    public AiHarnessFlowFactory(
        AiModelGateway gateway,
        AiHarnessSpec<I, T> spec,
        AiHarnessClock clock          // SPI; defaults available
    );

    public AiHarnessFlow createFlow(I input);

    public AiHarnessFlow createFlow(I input, RunOverrides overrides);

    public AiHarnessFlow createRecoveredFlow(AiHarnessRunSnapshot snapshot);

    public AiHarnessFlow createRecoveredFlow(
        AiHarnessRunSnapshot snapshot,
        AiRecoveryPolicy recoveryPolicy
    );

    public AiHarnessFlow createRecoveredFlow(
        AiHarnessRunSnapshot snapshot,
        AiRecoveryPolicy recoveryPolicy,
        RunOverrides overrides
    );

    public record RunOverrides(
        Optional<ModelId> modelId,
        Optional<ProviderOptions> providerOptions,
        Optional<Duration> timeout,
        Optional<AiCancellationToken> cancellationToken,
        Map<AiHarnessRunContext.AttributeKey<?>, Object> attributes
    ) {
        public static RunOverrides none();
        public static Builder builder();
        // ...
    }
}
```

- **Why**: the single public entry point for building runnable Flower
  flows. Hides every internal step class while exposing the run context that
  Flower itself does not store as a generic flow-level slot.
- **First-version**: build new flows from `(spec, input, overrides)` and
  rebuild recovered flows from `(snapshot, recoveryPolicy, overrides)`. Step
  classes are package-private implementation details instantiated by the
  factory.
- **Deliberate omissions**:
  - no direct step access,
  - no flow modification API,
  - no run-id override for fresh runs (one is generated per call and exposed
    through `AiHarnessFlow.context()`),
  - no provider-call replay; recovered flows retry, cancel, or fail based on
    the recovery decision.
- **Future**: a `createFlowWithRunId(...)` variant if hosts need to
  pre-assign run IDs for idempotency.
- **ArchDox separation**: ArchDox wires a singleton
  `AiHarnessFlowFactory<DocumentSnapshot, DocumentReviewDraft>` and
  submits flows from its own event listeners. The factory and the spec
  parameter types are the only contact surface.

---

## 13. `FakeAiModelGateway`

Lives in `flower-ai-harness-test`. Designed as a first-class deterministic
provider, not a throwaway stub.

### Sketch

```java
package io.github.flowerjvm.flower.ai.harness.test.fake;

public final class FakeAiModelGateway implements AiModelGateway {

    public FakeAiModelGateway(FixedAiHarnessClock clock);

    // Programmed responses
    public FakeAiModelGateway respondTo(RequestMatcher matcher, FakeResponseProgram program);
    public FakeAiModelGateway respondToAny(FakeResponseProgram program);

    // Inspection
    public List<RecordedCall> recordedCalls();
    public RecordedCall lastCall();
    public void reset();

    @Override
    public AiModelCall submit(AiModelRequest request);
}

public interface RequestMatcher {
    boolean matches(AiModelRequest request);
    // Static helpers:
    static RequestMatcher modelEquals(ModelId id);
    static RequestMatcher promptContains(String fragment);
    static RequestMatcher and(RequestMatcher... matchers);
}

public sealed interface FakeResponseProgram {
    record ImmediateText(String text) implements FakeResponseProgram {}
    record ImmediateError(Throwable error) implements FakeResponseProgram {}
    record DelayedText(String text, int ticksUntilReady) implements FakeResponseProgram {}
    record DelayedError(Throwable error, int ticksUntilFailed) implements FakeResponseProgram {}
    record Sequence(List<FakeResponseProgram> steps) implements FakeResponseProgram {}
    // Sequence iterates one program per matched request — useful for
    // testing refine cycles ("first response invalid, second valid").
}

public record RecordedCall(
    AiModelRequest request,
    Instant submittedAt,
    AiModelCallStatus terminalStatus
) {}
```

- **Why**: deterministic, programmable end-to-end testing without any
  real network or vendor dependency. Refine cycles, validation failures,
  timeouts, and provider errors must all be reproducible.
- **First-version responsibilities**:
  - program responses by request match,
  - drive both immediate and tick-deferred completion,
  - inject errors,
  - record every call for assertion,
  - cooperate with `FixedAiHarnessClock` so tick-count-based delays are
    deterministic.
- **Deliberate omissions**:
  - no random latency simulation in v0 (out of scope; can be a
    `FakeResponseProgram.Variant` later),
  - no token-by-token streaming simulation,
  - no on-disk replay fixture support (later module).
- **Future**: streaming program; pre-recorded fixtures loaded from
  `*.fake-call.json`; chaos modes (random drop, malformed JSON
  injection).
- **ArchDox separation**: ArchDox uses `FakeAiModelGateway` in its own
  tests to verify document QA harness behavior. ArchDox does not extend
  or alter the fake gateway — it uses it as-is.

---

## 14. SPI types

### `TraceListener`

```java
package io.github.flowerjvm.flower.ai.harness.spi;

public interface TraceListener {
    default void onRunStarted(AiHarnessRunContext ctx) {}
    default void onRequestSubmitted(AiHarnessRunContext ctx, AiModelRequest req, String callId) {}
    default void onResponseReceived(AiHarnessRunContext ctx, AiModelResponse resp) {}
    default void onCallFailed(AiHarnessRunContext ctx, Throwable error) {}
    default void onValidationCompleted(AiHarnessRunContext ctx, ValidationResult<?> result) {}
    default void onRefineTriggered(AiHarnessRunContext ctx, AiModelRequest nextRequest) {}
    default void onRunCompleted(AiHarnessRunContext ctx, List<AiFinding> findings) {}
    default void onRunFailed(AiHarnessRunContext ctx, String reason) {}
}
```

- **Why**: passive observation hook for logging, metrics, and trace
  exporters.
- **First-version**: synchronous callbacks with default no-ops. Multiple
  listeners can be registered.
- **Deliberate omissions**: no async dispatch, no event filtering DSL,
  no exporter implementations in core.
- **Future**: an OTEL exporter module; an exporter that writes JSONL to a
  file; a Spring Actuator integration.
- **ArchDox separation**: ArchDox can ship its own `TraceListener` that
  writes runs to its `ai_run_log` table. Core does not know about it.

### `AiHarnessClock`

```java
public interface AiHarnessClock {
    Instant now();
    static AiHarnessClock system();
}
```

- **Why**: testable time. The flow factory, run-state snapshots, and the fake
  gateway consume it.
- **First-version**: minimal `now()`. All `AiHarnessRunSnapshot.capturedAt`
  values are produced through this clock. A `FixedAiHarnessClock` in the test
  module supports controlled tick advancement.

---

## 15. What v0 deliberately does not include

A consolidated negative-space list. None of these belong in v0 even
though they will tempt early contributors.

- A `Tool` / `FunctionCall` / multi-turn tool loop type.
- A `RetrievalProvider` / embedding gateway / vector store.
- A `Stream<Token>` API.
- A `PromptRegistry` interface backed by anything real.
- An `AgentDefinition` type.
- A database-backed `RunStore` implementation.
- A provider-specific token/cost estimator.
- A `HumanReviewQueue` type.
- An ArchDox-specific package or class anywhere in the harness.

When any of these is needed, it is added in a later module or version —
never by overloading an existing v0 type.

---

## 16. ArchDox-side counterpart (illustrative, lives in ArchDox repo)

To make the boundary concrete, here is what ArchDox writes against the v0
API. None of these types live in `flower-ai-harness`.

```java
// In ArchDox, package com.archdox.documentai

public class DocumentQaPromptBuilder implements PromptBuilder<DocumentSnapshot> { ... }

public record DocumentReviewDraft(List<DocumentIssueDraft> issues) {}
public record DocumentIssueDraft(String code, String severity, String text,
                                 int page, String paragraphId) {}

public class DocumentReviewDraftExtractor
    implements FindingExtractor<DocumentReviewDraft> { ... }

public class ArchDoxFindingEventSink implements FindingSink { ... }

@Configuration
class DocumentAiHarnessConfig {
    @Bean
    AiHarnessSpec<DocumentSnapshot, DocumentReviewDraft> documentQaSpec(...) {
        return AiHarnessSpec.<DocumentSnapshot, DocumentReviewDraft>builder()
            .harnessId("archdox.document-qa")
            .defaultModelId(ModelId.parse("anthropic:claude-opus-4-7"))
            .promptVersion(new PromptVersion("document-qa", "1.0.0"))
            .promptBuilder(new DocumentQaPromptBuilder(...))
            .validator(new JacksonPojoSchemaValidator<>(DocumentReviewDraft.class, mapper))
            .refinePolicy(new MaxAttemptsRefinePolicy(3))
            .findingExtractor(new DocumentReviewDraftExtractor())
            .findingSink(archDoxFindingEventSink)
            .build();
    }

    @Bean
    AiHarnessFlowFactory<DocumentSnapshot, DocumentReviewDraft> documentQaFlowFactory(
        AiModelGateway gateway,
        AiHarnessSpec<DocumentSnapshot, DocumentReviewDraft> spec
    ) {
        return new AiHarnessFlowFactory<>(gateway, spec, AiHarnessClock.system());
    }
}
```

If any line in the illustrative snippet above would require importing or
modifying a `flower-ai-harness` internal type, that is a signal the API
boundary is wrong and must be fixed in core — not papered over in ArchDox.

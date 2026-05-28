# PHASED_IMPLEMENTATION.md

This is the implementer's worksheet. It assumes `ARCHITECTURE.md`,
`API_DESIGN.md`, and `IMPLEMENTATION_PLAN.md` are already accepted.

Each phase is a small, self-contained unit of work. The reader should be
able to pick up any phase, follow the items in order, satisfy the done
criteria, and stop — without needing to invent anything.

Phases are sized to be doable in roughly one focused work-session each
(one day or less for a senior Java engineer who already knows Flower).
Larger work is split into smaller phases rather than packed into one.

Conventions used in every phase:

- **Goal** — one sentence about what this phase achieves.
- **Implementation items** — ordered checklist.
- **Deliverables** — the artifacts that exist when the phase ends.
- **Test method** — how the phase is verified.
- **Done criteria** — binary; if any fail, the phase is not done.
- **Do not do in this phase** — a deliberate exclusion list.

The phase order is fixed. Do not skip ahead.

---

## Phase 0 — Repository Skeleton

**Goal**: a buildable, empty multi-module project that the rest of the work
slots into.

**Implementation items**:

1. Initialize the git repository in `flower-ai-harness/` (already exists).
2. Choose build tool: Gradle (Kotlin DSL) **or** Maven. Match the
   convention of the host's other Java projects (e.g., `abyss-runner`,
   `flower`). Document the choice in a one-line note in this file under
   "Build tool: <Gradle|Maven>".
3. Create the four v0 modules as empty subprojects/modules:
   - `flower-ai-harness-core`
   - `flower-ai-harness-validator-jackson`
   - `flower-ai-harness-test`
   - `flower-ai-harness-samples`
4. Set Java toolchain to 21.
5. Configure each module's group/artifact:
   - group: `io.github.parkkevinsb.flower.ai.harness`
   - artifacts: `flower-ai-harness-core`, `-test`, `-samples`
6. Add a compile dependency from core to Flower (use the same version
   coordinate as `abyss-runner` consumes).
7. Add JUnit 5 and AssertJ as test dependencies in all three modules.
8. Add module dependencies without creating a Maven reactor cycle:
   - `flower-ai-harness-validator-jackson` depends on core.
   - `flower-ai-harness-test` depends on core.
   - `flower-ai-harness-samples` depends on core, validator-jackson, and test.
   - core does not depend on test, even in test scope.
9. Configure a CI workflow (GitHub Actions or equivalent) that runs
   `./gradlew build` (or `mvn verify`) on every push.
10. Add a `.gitignore` appropriate for the chosen build tool plus IDE
    folders (`.idea/`, `*.iml`, `out/`, `build/`, `target/`).
11. Create empty package directories matching `API_DESIGN.md §6`:
    `model/`, `gateway/`, `prompt/`, `validate/`, `refine/`,
    `finding/`, `run/`, `spec/`, `flow/`, `flow/step/`, `spi/`.
12. Add a single `package-info.java` to `flow.step` documenting that the
    package is internal.

**Deliverables**:

- A repository that builds with zero source files (or one placeholder
  `package-info.java` per package).
- A green CI run.
- A one-line build-tool decision recorded in this file.

**Test method**:

- `./gradlew build` (or `mvn verify`) succeeds locally and in CI.

**Done criteria**:

- All four v0 modules are present and buildable.
- CI is green.
- Java 21 toolchain is in effect (verified by a `Runtime.version()` check
  in a sanity test or a build script assertion).
- No vendor SDK, no Spring, no ArchDox dependency anywhere.

**Do not do in this phase**:

- Do not start writing the model types yet.
- Do not add Spring Boot dependencies, even "to be ready."
- Do not add Jackson to core. It shows up only in the validator-jackson module
  in Phase 2.
- Do not create the deferred modules (starter, providers, observability).

---

## Phase 1 — Model + Gateway Contracts

**Goal**: define the provider-neutral request/response/call types and the
gateway interface — without any flow logic yet.

**Implementation items**:

1. In `model/`, create:
   - `ModelId` record with `parse` and `asString`.
   - `ProviderOptions` immutable holder.
   - `AiModelRequest` record with the v0 fields and `with*` helpers.
   - `AiModelResponse` record and nested `ResponseMetadata`.
   - `AiModelCallStatus` enum.
   - `AiModelCall` interface.
2. In `gateway/`, create:
   - `AiModelGateway` interface (`submit` only).
   - `GatewayException` class.
   - `RoutingAiModelGateway` class with map-based dispatch on
     `ModelId.provider()` and an optional fallback.
3. In `prompt/`, create:
   - `PromptVersion` record.
   - `PromptTemplate` record.
   - `RenderedPrompt` record (with nested `Message` and `Role`).
   - Defer `PromptBuilder<I>` to Phase 3 because its final signature depends
     on `AiHarnessRunContext`, which is intentionally introduced with run
     lifecycle types.
4. Add a `package-info.java` to each new package summarizing scope.
5. Unit tests:
   - `ModelIdParseTest` — covers valid, invalid, round-trip cases.
   - `ProviderOptionsTest` — immutability and `with(...)` semantics.
   - `RoutingAiModelGatewayTest` — dispatches to the right provider;
     uses fallback when no provider is registered; raises a
     `GatewayException` if no fallback either.

**Deliverables**:

- The Phase 1 set of model, gateway, and prompt value types from
  `API_DESIGN.md §0–§5` (except `PromptBuilder<I>`, which lands in Phase 3,
  and `AiModelGateway` implementations beyond `RoutingAiModelGateway`).
- Unit tests covering parsing, immutability, and routing dispatch.

**Test method**:

- JUnit 5 tests run via `mvn -pl flower-ai-harness-core test`.

**Done criteria**:

- All Phase 1 types compile.
- All listed unit tests pass.
- No type in `model/`, `gateway/`, or `prompt/` imports anything outside
  the JDK, Flower core, and the project's own packages.

**Do not do in this phase**:

- Do not create the fake gateway yet.
- Do not introduce validation, refine, finding, or run types.
- Do not build the flow factory.
- Do not call any real network or add an HTTP client.

---

## Phase 2 — Validation + Refine + Finding Model

**Goal**: define the structured-output and retry/refine contracts, plus the
neutral finding model.

**Implementation items**:

1. In core `validate/`:
   - `ValidationError` record.
   - `ValidationResult<T>` sealed interface with `Valid<T>` and `Invalid<T>`.
   - `AiSchemaValidator<T>` interface.
2. In `flower-ai-harness-validator-jackson`:
   - add Jackson (`jackson-databind`) as a compile dependency.
   - create `JacksonPojoSchemaValidator<T>` reference implementation.
   - keep the package clearly named, e.g.
     `io.github.parkkevinsb.flower.ai.harness.validator.jackson`.
3. In core `refine/`:
   - `RefineContext` record.
   - `RefineDecision` sealed interface with `Continue`, `Retry`, `Fail`.
   - `AiRefinePolicy` interface.
   - `MaxAttemptsRefinePolicy` reference implementation. On invalid
     response: rebuild the request by appending a `Role.SYSTEM` message
     listing the validation errors verbatim, return `Retry(nextRequest)`.
     On transport failure: `Retry` up to N times with the same request,
     then `Fail`. On valid: `Continue`.
   - `ModelFallbackPlan` and `ModelFallbackRefinePolicy` for changing
     `ModelId` across attempts without changing the flow graph.
   - Phase 2 starts with a lifecycle-independent `RefineContext`; Phase 3
     upgrades it to carry `AiHarnessRunContext` once the run type exists.
4. In core `finding/`:
   - `AiFindingSeverity` enum.
   - `AiFinding` record with builder-like `with*` helpers.
   - `FindingExtractor<T>` interface.
   - `FindingSink` interface.
   - Phase 2 starts with context-free finding interfaces; Phase 3 upgrades
     them to receive `AiHarnessRunContext`.
5. Unit tests:
   - `JacksonPojoSchemaValidatorTest` — valid JSON, wrong type, malformed
     JSON, and a missing-field case. If plain Jackson binding would silently
     accept a missing nullable field, the validator module must either add a
     post-bind required-field check or document that such checks belong in a
     custom `AiSchemaValidator`.
   - `MaxAttemptsRefinePolicyTest` — happy path, retry-then-succeed
     (simulated by hand-built `RefineContext`s), retry-then-fail,
     transport-failure path.
   - `AiFindingTest` — `with*` immutability, equality.

**Deliverables**:

- All validation, refine, and finding types from `API_DESIGN.md §6–§8`.
- Unit tests covering both reference implementations and the value
  types.

**Test method**:

- JUnit 5.
- Tests must not use the flow factory yet (it does not exist).

**Done criteria**:

- All Phase 2 types compile and pass tests.
- `JacksonPojoSchemaValidator` lives outside core in
  `flower-ai-harness-validator-jackson`.
- The refine policy never references a specific validator type or
  finding code.

**Do not do in this phase**:

- Do not add JSON Schema validation. It is a later module.
- Do not add a critique-LLM refine policy.
- Do not add domain-shaped fields to `AiFinding` (no page, no paragraph,
  no document reference).
- Do not introduce `AiHarnessRunContext` yet.

---

## Phase 3 — Run Context, Spec, Flow Factory

**Goal**: assemble the lifecycle. Add the run context, the spec, the flow
factory, and the internal step set.

**Implementation items**:

1. In `run/`:
   - `AiHarnessRunId` record.
   - `AiHarnessRunContext` class with the fields and accessors from
     `API_DESIGN.md §9`.
   - `AiHarnessRunContext.AttributeKey<T>` static inner class.
2. In `spi/`:
   - `AiHarnessClock` interface with `system()` static factory.
   - `TraceListener` interface with default no-op methods.
3. In `spec/`:
   - `AiHarnessSpec<I,T>` with a builder. The builder enforces required
     fields (`harnessId`, `defaultModelId`, `promptVersion`,
     `promptBuilder`, `validator`, `refinePolicy`, `findingExtractor`,
     `findingSink`) at `build()` time.
4. In `flow/` as package-private internal step classes:
   - `PreparePromptStep` — renders the prompt via the spec's
     `PromptBuilder` and stores `currentRequest` in the run context.
   - `AwaitResponseStep` — on `onEnter`, calls `gateway.submit(...)` and
     stores the call handle. On `onTick`, polls; transitions on
     `READY` / `FAILED`; stays on `PENDING`.
   - `ValidateResponseStep` — calls the spec's validator, stores result
     in the run context.
   - `RefineDecisionStep` — calls the spec's `AiRefinePolicy`. On
     `Retry`, updates `currentRequest` and transitions back to
     `AwaitResponseStep`. On `Continue`, proceeds. On `Fail`, fails the
     flow.
   - `EmitFindingsStep` — runs the `FindingExtractor`, calls the
     `FindingSink`, fires `TraceListener.onRunCompleted`.
5. In `flow/`:
   - `AiHarnessFlowFactory<I,T>` with constructor `(gateway, spec, clock)`
     and the two `createFlow` overloads.
   - `AiHarnessFlow` record wrapping the Flower `Flow` plus the
     `AiHarnessRunContext`.
   - `RunOverrides` record (nested in `AiHarnessFlowFactory`).
6. Implement `TraceListener` event emission from each step transition
   (request submitted, response received, validation completed, refine
   triggered, etc.).
7. Unit tests:
   - `AiHarnessRunContextTest` — attribute set/get, type safety.
   - `AiHarnessSpecBuilderTest` — required-field enforcement.
   - One smoke test that constructs a flow via the factory and asserts
     the flow's step layout (without actually running it). This catches
     wiring regressions early.

**Deliverables**:

- Complete v0 core: every type listed in `API_DESIGN.md` exists.
- The internal step set is package-private.
- The factory is the single public construction path.
- Factory methods return `AiHarnessFlow`, so callers can submit
  `aiHarnessFlow.flow()` to Flower and inspect/correlate
  `aiHarnessFlow.context()`.

**Test method**:

- JUnit 5 unit tests, plus the smoke wiring test.
- End-to-end flow tests are deferred to Phase 5 (after the fake gateway
  exists).

**Done criteria**:

- The core module compiles and all unit tests pass.
- Public surface audit (`CP-D` from `IMPLEMENTATION_PLAN.md`) matches
  `API_DESIGN.md`. Any public type not listed there is made
  package-private or added to the document.
- Step classes are not accessible from outside the core module.

**Do not do in this phase**:

- Do not implement the fake gateway here (next phase).
- Do not add a real provider.
- Do not run a flow end-to-end yet.
- Do not add Spring annotations to any class.

---

## Phase 4 — FakeAiModelGateway and Test Module

**Goal**: a first-class deterministic provider plus the test helpers needed
for end-to-end flow tests.

**Implementation items**:

1. In `flower-ai-harness-test` module, package
   `io.github.parkkevinsb.flower.ai.harness.test.fake`:
   - `RecordedCall` record.
   - `RequestMatcher` interface with static helpers (`modelEquals`,
     `promptContains`, `and`, `any`).
   - `FakeResponseProgram` sealed interface with all variants from
     `API_DESIGN.md §12`.
   - `FakeAiModelCall` package-private implementation of `AiModelCall`
     backed by a tick-counter.
   - `FakeAiModelGateway` implementing `AiModelGateway`. It owns the
     program registry, the recorded-calls list, and a reference to a
     `FixedAiHarnessClock`.
2. In `flower-ai-harness-test`, package `...test.time`:
   - `FixedAiHarnessClock` with `tick()`, `tickBy(int)`, `instant()`.
3. In `...test.assertion`:
   - `AiFindingAssertions` — AssertJ-style fluent assertions for
     `AiFinding` lists (by severity, by code, by message contains).
   - `AiHarnessRunAssertions` — fluent assertions on a recorded run
     (attempts taken, final state, findings emitted).
4. In `...test`, a JUnit 5 extension `AiHarnessTestExtension`:
   - injects a configured `FakeAiModelGateway`,
   - drives the flow by repeatedly ticking the engine until the flow
     terminates (with a max-tick safety bound),
   - exposes a fluent `runHarness(spec, input)` helper.
5. Tests of the fake gateway itself (in the test module's own
   `src/test/java`):
   - `FakeAiModelGatewayImmediateTest` — programmed immediate text.
   - `FakeAiModelGatewayDelayedTest` — pending for N ticks then ready.
   - `FakeAiModelGatewayErrorInjectionTest` — failure programs.
   - `FakeAiModelGatewaySequenceTest` — multi-call sequence used for
     refine-cycle testing.

**Deliverables**:

- A complete fake provider that is rich enough to drive every v0
  lifecycle path.
- A test extension and assertion library.

**Test method**:

- JUnit 5 tests inside the test module.
- The fake gateway's tests must not require the core flow factory —
  they exercise the gateway directly.

**Done criteria**:

- Every `FakeResponseProgram` variant has at least one passing test.
- `FakeAiModelGateway.recordedCalls()` accurately captures requests in
  order.
- The test module compiles standalone.
- No vendor SDK or Spring dependency in the test module.

**Do not do in this phase**:

- Do not add network-based fixture replay.
- Do not simulate token streaming.
- Do not couple the fake gateway to any specific real provider's quirks.
- Do not put the fake gateway in `core` — it stays in `-test` so v0
  consumers (including ArchDox) explicitly opt into it as a test
  dependency.

---

## Phase 5 — Sample Harness (text-review-sample)

**Goal**: prove the whole stack works by running a real end-to-end
domain-neutral harness against the fake provider.

**Implementation items**:

1. In `flower-ai-harness-samples`, create the `text-review-sample` source
   set (still inside the samples module — sub-packages, not a sub-module).
2. Implement the sample harness:
   - `TextReviewInput(String text)` — input record.
   - `TextReviewDraft(List<TextIssue> issues)` — output record validated
     by `JacksonPojoSchemaValidator<TextReviewDraft>`.
   - `TextIssue(String code, String severity, String message,
     String quote)`.
   - `TextReviewPromptBuilder implements PromptBuilder<TextReviewInput>`
     — produces a fixed system message + a user message containing the
     text.
   - `TextReviewFindingExtractor implements FindingExtractor<TextReviewDraft>`
     — maps each issue to an `AiFinding` (with `quote` becoming
     `evidence`, `severity` parsed into `AiFindingSeverity`).
   - `InMemoryFindingSink implements FindingSink` — collects findings
     for the sample's own reporting.
   - `TextReviewSampleApplication` — `main(...)` builds the spec,
     constructs the flow factory with a `FakeAiModelGateway` that
     returns a hardcoded JSON response, submits a flow to a small Flower
     `Engine`, prints the findings.
3. End-to-end tests in the samples module:
   - `TextReviewHappyPathTest` — fake returns valid JSON, harness emits
     findings, attempt count = 1.
   - `TextReviewRetryThenSucceedTest` — fake returns malformed JSON
     first, valid JSON second; harness emits findings, attempt count
     = 2.
   - `TextReviewExhaustedRetryTest` — fake returns malformed JSON
     repeatedly; harness fails after `MaxAttemptsRefinePolicy`'s limit.
   - `TextReviewTransportFailureTest` — fake errors first call, succeeds
     second; harness emits findings.
   - `TextReviewValidationFailureFindingsTest` — verifies the trace
     listener sees `onRefineTriggered`.
4. A simple `README.md` inside the samples module explaining how to
   run the `main` and what it prints. (No marketing copy. Just usage.)

**Deliverables**:

- A runnable sample.
- Five named end-to-end test scenarios covering the v0 lifecycle paths.

**Test method**:

- JUnit 5 tests on the samples module.
- Manual run of the `main` method as a sanity check.

**Done criteria**:

- All five end-to-end tests pass.
- The sample uses **only** public types from
  `flower-ai-harness-core` and `flower-ai-harness-test`.
- The sample contains zero ArchDox concepts.
- The samples module has no Spring dependency.
- Validation CP-B (end-to-end through fake) is satisfied.

**Do not do in this phase**:

- Do not add a second sample (one is enough for v0).
- Do not add a real provider integration.
- Do not add Spring or REST scaffolding to the sample.
- Do not test against any vendor sandbox.

---

## Phase 6 — ArchDox Integration (in the ArchDox repository)

**Goal**: validate the framework by writing ArchDox's document QA harness
against the published public API.

This phase lives in the ArchDox repo, not in `flower-ai-harness`. The
work itemized here is the contract from the harness side; ArchDox's
internal work (DB schema, UI integration) is out of scope for this
document.

**Implementation items** (ArchDox side):

1. Add `flower-ai-harness-core` as a main dependency and
   `flower-ai-harness-test` as a test dependency of the relevant ArchDox
   module. Add `flower-ai-harness-validator-jackson` only if ArchDox chooses
   the reference validator instead of a custom validator. Refer to
   `D:\Code\Personal\git\abyss-runner\server\src\main\java\com\abyssrunner\global\flow\FlowerBloomConfiguration.java`
   for the Flower/Bloom bootstrap pattern.
2. Create the ArchDox-side module/package `document-ai-harness`:
   - `DocumentQaPromptBuilder implements PromptBuilder<DocumentSnapshot>`.
   - `DocumentReviewDraft` record and supporting issue records.
   - `DocumentReviewDraftExtractor implements
     FindingExtractor<DocumentReviewDraft>`.
   - `ArchDoxFindingEventSink implements FindingSink` (publishes or enqueues
     findings quickly; the actual database write happens in ArchDox-side
     infrastructure outside the Flower worker tick).
3. A `@Configuration` class wiring:
   - an `AiModelGateway` bean (initially `FakeAiModelGateway` for tests,
     real provider TBD when one is implemented),
   - an `AiHarnessSpec<DocumentSnapshot, DocumentReviewDraft>` bean,
   - an `AiHarnessFlowFactory<DocumentSnapshot, DocumentReviewDraft>`
     bean.
4. A `DocumentQaFlowListener` (following the `@Subscribe` →
   `worker.submit(factory.create(...))` pattern from
   `HeroDefeatFlowListener.java` in `abyss-runner`).
5. ArchDox-side tests:
   - At least one integration test that drives the document QA harness
     against `FakeAiModelGateway` and asserts that
     `ArchDoxFindingEventSink` published or enqueued expected findings.
6. Maintain a running list of friction points encountered during this
   work. Examples of the kind of friction to log:
   - "I had to import an internal class to do X."
   - "I needed to subclass an internal step."
   - "I needed to wrap `AiHarnessRunContext` because Y was missing."
   - "I needed to convert types from `AiFinding` repeatedly in a way
     that suggests a missing builder helper."

   The list becomes the input to Phase 7.

**Deliverables**:

- A working ArchDox document QA harness on `flower-ai-harness-core`.
- A written list of friction points from the integration.

**Test method**:

- ArchDox's own integration test suite, executed against
  `FakeAiModelGateway`.

**Done criteria**:

- ArchDox compiles and passes its tests with `flower-ai-harness-core`
  and `flower-ai-harness-test` on the classpath.
  - No internal flow step class is referenced by ArchDox.
- No reflection workaround in ArchDox to access framework internals.
- Validation CP-E (ArchDox boundary test) is satisfied.

**Do not do in this phase**:

- Do not change `flower-ai-harness` code. If a change feels necessary,
  record it as a friction point and proceed with a temporary
  ArchDox-side workaround. The change is evaluated in Phase 7, not
  hot-patched in mid-integration.
- Do not add a real provider yet unless ArchDox testing explicitly
  needs one. Even then, prefer the fake gateway in tests; only wire a
  real provider for live demos.
- Do not move document concepts into the framework, regardless of how
  tempting it is.

---

## Phase 7 — Abstraction Refinement Based on ArchDox Feedback

**Goal**: apply the friction-point list from Phase 6 back into the
framework, keeping each change justified by concrete evidence.

**Implementation items**:

1. Open `IMPLEMENTATION_PLAN.md §7.4` and triage each friction point
   from Phase 6:
   - Outcome 1 (solved locally in ArchDox): close the item with a note.
   - Outcome 2 (cross-cutting, schedule a core change): create a
     concrete change item below.
   - Outcome 3 (wrong boundary, immediate core change): mark for this
     phase.
2. For each Outcome 2 / Outcome 3 item, write a small design note (one
   paragraph) covering:
   - what changed,
   - why it had to change (cite the friction point),
   - what the new public surface is.
3. Apply the changes to `flower-ai-harness-core` (and `-test` if a fake
   gateway capability is involved).
4. Update `API_DESIGN.md` and `ARCHITECTURE.md` to reflect the new
   shape. If an Open Question from `ARCHITECTURE.md §14` was resolved,
   mark it as resolved in this phase.
5. Update the text-review sample and ArchDox integration if a public
   type signature changed.
6. Re-run all tests: core unit tests, fake gateway tests, sample
   end-to-end tests, ArchDox integration tests. Everything must remain
   green.

**Examples of changes that *might* land in this phase** (illustrative;
actual list depends on Phase 6 findings):

- Add an `Optional<Double> confidence` field to `AiFinding`.
- Add a `MultiPassRefinePolicy` (where the validator runs different
  passes per attempt) — but only if ArchDox actually needed it.
- Promote a helper method from ArchDox into a core utility — only if it
  has no document semantics.
- Add a `RunOverrides.modelId()` short-circuit for a specific scenario.
- Adjust `AiHarnessRunContext` attribute API ergonomics.

**Examples of changes that should *not* land in this phase**:

- Adding tool-calling support.
- Adding streaming.
- Adding a hosted dashboard.
- Pre-emptive refactors for "future flexibility" that have no Phase 6
  evidence.

**Deliverables**:

- A revised core matching the documented surface.
- Updated `API_DESIGN.md`, `ARCHITECTURE.md`, and (if scope changed)
  `IMPLEMENTATION_PLAN.md`.
- A short "Phase 6 → Phase 7 changelog" appended to this file recording
  what was refined and why.

**Test method**:

- The full v0 test corpus: core unit tests, fake gateway tests, sample
  end-to-end tests, ArchDox integration tests. All green.
- Validation CP-F (each change justified by Phase 6 evidence) is
  satisfied by the changelog.

**Done criteria**:

- All Phase 6 friction points are triaged (resolved locally, refined in
  core, or explicitly deferred to post-v0 with a written reason).
- All four design documents accurately describe the shipped state.
- Validation invariants in `ARCHITECTURE.md §15` all hold.
- Validation checkpoints CP-A through CP-F all pass.

**Do not do in this phase**:

- Do not add features unrelated to the friction list.
- Do not expand the Spring Boot starter beyond thin infrastructure
  auto-configuration.
- Do not start any real provider module.
- Do not break public types without a written justification tied to a
  Phase 6 finding.

---

## After Phase 7: Definition of v0 Done

At the end of Phase 7, v0 is complete per
`IMPLEMENTATION_PLAN.md §13`:

1. Core, test, and samples modules are buildable and green.
2. The text-review sample runs end-to-end against
   `FakeAiModelGateway`.
3. ArchDox's document QA harness is running on the published API.
4. Phase 7 refinement is merged.
5. All four design documents (this one, `ARCHITECTURE.md`,
   `API_DESIGN.md`, `IMPLEMENTATION_PLAN.md`) match the code.
6. The architectural invariants list passes.

Post-v0 phases are intentionally not planned here. They depend on what
the ArchDox-driven refinement actually reveals. Likely candidates,
sketched for context only:

- **Phase 8** — Spring AI adapter (`flower-ai-harness-spring-ai`) with
  `SpringAiModelGateway implements AiModelGateway`. This is the preferred
  Java production model-integration path.
  Update: this adapter has been pulled forward and implemented.
- **Phase 9** — Spring Boot starter.
  Update: a thin starter has been pulled forward and implemented. It only
  auto-configures executor, `SpringAiModelResolver`, and
  `SpringAiModelGateway`; domain harness wiring still belongs in the host
  application.
- **Phase 10** — observability module (TraceListener exporters: file,
  OTEL).
- **Phase 11** — JSON Schema validator module.
- **Phase 12+** — direct provider modules (`provider-openai`,
  `provider-anthropic`, `provider-local`) only if Spring AI does not cover
  a real need cleanly.

These belong in a future planning document, not this one. The point of
v0 is to earn the right to plan them by learning from real usage first.

---

## Phase 6 → Phase 7 Changelog

(To be filled during Phase 7. One bullet per change. Each bullet cites
a Phase 6 friction point.)

- _(empty until Phase 7 starts)_

---

## Build Tool Decision

- Build tool: **Maven**
- Decision date: 2026-05-28
- Reason: matches the convention of the user's other Java libraries
  (`flower`, `bloom` both use Maven), aligns with the open-source Java
  framework ecosystem the project targets (Spring AI / LangChain4j /
  Resilience4j are all Maven), and keeps the path to Maven Central
  publishing on the canonical `central-publishing-maven-plugin` route.

### Plugin Set (Phase 0 setup)

```text
maven-compiler-plugin            Java 21 toolchain
maven-surefire-plugin            unit tests (JUnit 5)
maven-failsafe-plugin            integration tests (used from Phase 6)
maven-enforcer-plugin            dependency whitelist — enforces no forbidden
                                 dependencies in core
forbiddenapis or Checkstyle      import/package rule — enforces no Spring,
                                 vendor SDK, Jackson, or ArchDox imports in
                                 core source
maven-source-plugin              source jar (Maven Central requirement)
maven-javadoc-plugin             javadoc jar (Maven Central requirement)
maven-gpg-plugin                 signing (used at publish time only)
central-publishing-maven-plugin  Sonatype Central publishing (deferred
                                 until v0 ships; configured but inactive
                                 in Phase 0)
```

Notes:

- `maven-enforcer-plugin` is wired in Phase 0 for dependency bans. Use
  `forbiddenapis`, Checkstyle, or an equivalent source-level check for
  forbidden imports. This makes CP-C a build failure from day one rather than
  a manual review item.
- GPG signing and Central publishing are configured in Phase 0 but
  bound to a non-default profile (e.g., `-P release`) so they do not
  run in normal CI builds.
- Module POMs inherit from a single parent POM. The parent declares
  Java 21, plugin versions, and dependency management; the child POMs
  only declare their own dependencies.

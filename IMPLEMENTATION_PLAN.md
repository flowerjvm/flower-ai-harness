# IMPLEMENTATION_PLAN.md

This document describes the implementation **strategy**: how the project is
built up, in what order modules and concerns are introduced, which risks the
strategy is designed to absorb, and how decisions are re-evaluated as
ArchDox usage produces real feedback.

The phase-by-phase task list lives in `PHASED_IMPLEMENTATION.md`. This
document is about *why* the work is sequenced the way it is and *what could
go wrong*.

---

## 1. Overall Strategy

Three commitments guide everything below.

1. **Boundary-first, feature-last.** Build the public contracts and the
   internal seams before any feature. A small API surface is the deliverable
   that protects the project from regret later. Features are added behind
   that surface and removed easily.

2. **Fake provider is first-class infrastructure.** The fake gateway is
   built early and used to validate every later abstraction. If a feature
   cannot be tested end-to-end against the fake provider, the abstraction
   is wrong.

3. **ArchDox is a validation target, not the host.** The framework is
   completed (in v0 sense) before ArchDox starts depending on it. ArchDox
   integration is treated as a test of the framework, not a development
   environment for it.

These commitments translate directly into the phase order in
`PHASED_IMPLEMENTATION.md`: skeleton, contracts, fake provider, sample,
ArchDox application, refinement.

---

## 2. Module Creation Order

```text
Phase 0   flower-ai-harness                (repo + tooling)
Phase 1   flower-ai-harness-core           (model + gateway contracts)
Phase 2   flower-ai-harness-core           (validation + refine + finding)
Phase 2   flower-ai-harness-validator-jackson (reference validator)
Phase 3   flower-ai-harness-core           (run + spec + flow factory)
Phase 4   flower-ai-harness-test           (FakeAiModelGateway + clock)
Phase 5   flower-ai-harness-samples        (text-review-sample)
Phase 6   (in archdox repo) document-ai-harness integration
Phase 7   flower-ai-harness-core           (refinement based on Phase 6 feedback)
```

Note that the core module is built in three passes (Phases 1, 2, 3). This
is deliberate: each pass produces a coherent set of types that can be code-
reviewed independently. Each pass ends with a runnable, testable state of
the core module.

Deferred modules — direct provider adapters and `observability` — are created
only after Phase 7 has stabilized the core contracts. Creating them earlier
would expose them to API churn that hasn't earned its keep yet. When production
model integration begins, the first adapter should be
`flower-ai-harness-spring-ai`, not direct OpenAI/Anthropic SDK modules.

Update: `flower-ai-harness-spring-ai` and
`flower-ai-harness-spring-boot-starter` have been pulled forward. The starter
is intentionally thin: it auto-configures the Spring AI gateway for common
Spring Boot wiring while keeping all harness lifecycle behavior in core.

---

## 3. Dependency Graph

Compile-time dependencies (arrows point *to* the dependency):

```text
flower-ai-harness-validator-jackson ──→ flower-ai-harness-core ──→ flower
flower-ai-harness-test              ──→ flower-ai-harness-core
flower-ai-harness-samples           ──→ flower-ai-harness-core
flower-ai-harness-samples           ──→ flower-ai-harness-test
flower-ai-harness-samples           ──→ flower-ai-harness-validator-jackson
flower-ai-harness-spring-ai         ──→ flower-ai-harness-core
flower-ai-harness-spring-ai         ──→ Spring AI Chat Client
flower-ai-harness-spring-boot-starter ──→ flower-ai-harness-spring-ai
flower-ai-harness-spring-boot-starter ──→ Spring Boot autoconfigure
archdox/document-ai-harness         ──→ flower-ai-harness-core
archdox/document-ai-harness         ──→ flower-ai-harness-test        (test scope)
archdox/document-ai-harness         ──→ flower-ai-harness-validator-jackson (if used)
```

Forbidden dependencies (compile-time):

```text
flower-ai-harness-core ─/→ Spring
flower-ai-harness-core ─/→ any vendor SDK (OpenAI/Anthropic/etc.)
flower-ai-harness-core ─/→ ArchDox
flower-ai-harness-core ─/→ jackson-databind
flower-ai-harness-test ─/→ vendor SDKs, ArchDox, Spring
```

The Jackson reference validator is in its own module from day one. This keeps
core dependency-light and prevents a later module extraction from becoming the
first breaking change.

Test-only dependencies allowed in core:

- JUnit 5
- AssertJ
- Mockito (used sparingly; prefer the fake gateway over mocks)

---

## 4. High-Level Development Flow

For every phase the work proceeds in this loop:

```text
1. Pin the contract.
   Write API_DESIGN.md updates first if anything changed since the previous
   draft. Do not write production code until the type signatures are
   committed.

2. Implement the smallest viable behavior.
   No premature generalization. No "we'll also need X later" code.

3. Cover with fake-gateway tests.
   Every behavior is exercised end-to-end through a Flower flow driven by
   FakeAiModelGateway, against a FixedAiHarnessClock. Unit tests on
   individual classes are secondary.

4. Run the architectural invariants check (see §10) before merging.

5. Revisit Open Questions in ARCHITECTURE.md §14.
   If the phase answered any of them, update the doc in the same change.
```

This rhythm protects three properties at once: API stability, behavior
correctness, and architectural drift.

---

## 5. Risks and Mitigations

### R1. Building a generic framework before the patterns are real

This is the single largest risk for the project and is called out in
`STRATEGY.md` as well. Symptom: abstractions that fit no real harness
cleanly.

**Mitigation**: keep v0 small enough that ArchDox integration happens
quickly. The first checkpoint that the framework is on the right track is
not "the code compiles" — it is "ArchDox's document QA harness can be
written using only the public API." If that requires bending the public
API, the framework is wrong, not ArchDox.

### R2. Provider neutrality drifts

Symptom: a vendor concept (OpenAI tool call format, Anthropic-style
content blocks) leaks into a core type.

**Mitigation**:

- enforce the "no vendor SDK in core" rule via a Maven dependency
  whitelist test;
- review every PR that touches `AiModelRequest`, `AiModelResponse`, or
  `AiModelGateway` against the question "is this neutral?"
- treat `ProviderOptions` as the *only* place provider-specific data
  enters the system.

### R3. Flower assumptions baked in

Symptom: harness types assume Flower internals beyond `Step.onEnter` /
`onTick` / `stay()` / `done()` / `fail()`. This makes Flower evolution
risky.

**Mitigation**: depend only on the public Flower API. Document the
specific Flower surface used in `ARCHITECTURE.md`. If Flower changes
shape, the impact area is small.

### R4. Worker-tick blocking creeps in

Symptom: a provider implementation or a finding sink does synchronous
I/O on the worker tick.

**Mitigation**:

- `FindingSink` and `TraceListener` are documented as "must return quickly."
  Implementations that need JDBC/network I/O must publish to an event bus or
  delegate to a background writer;
- the test harness includes a "slow tick detector" that fails any test
  whose step `onTick` exceeds a configured budget;
- in CI, all sample and ArchDox harness runs are executed against the
  fake gateway, where no real network ever happens.

### R5. ArchDox-shaped types leak into core

Symptom: a PR adds `DocumentSnapshot` or similar to a core signature.

**Mitigation**: a static check that no class under
`io.github.parkkevinsb.flower.ai.harness.*` imports anything from the
ArchDox package namespace. This is trivial to add as an ArchUnit-style
test or a simple grep in CI.

### R6. Refine semantics get tangled with validation semantics

Symptom: `AiSchemaValidator` starts producing "fixed" values, or
`AiRefinePolicy` starts caring about specific validation error codes.

**Mitigation**: the two contracts share only `ValidationResult` and
`ValidationError`. `AiSchemaValidator` never returns a "partially fixed"
value. `AiRefinePolicy` decides on retry based on error *presence*, not
on parsing concerns.

### R7. The fake gateway becomes a second-class citizen

Symptom: the fake gateway is treated as "just a test stub," features
land in real providers but not in the fake, and tests stop reflecting
real behavior.

**Mitigation**: every new capability added to the gateway interface
must be implementable by the fake first. If it cannot be expressed in
`FakeResponseProgram`, the capability is either provider-internal (and
shouldn't be in the gateway interface) or the gateway interface is
wrong.

### R8. Overgrown Spring Boot starter

Symptom: the starter starts defining harness specs, validators, persistence,
or domain behavior before ArchDox proves the actual wiring shape.

**Mitigation**: the starter remains thin. It may create executor,
`SpringAiModelResolver`, and `SpringAiModelGateway` beans. Domain harness
specs, validators, prompt builders, finding sinks, and persistence stay in
the host application.

---

## 6. Validation Checkpoints

Each checkpoint is a binary gate. If it does not pass, the phase is not
done. These checkpoints are referenced from `PHASED_IMPLEMENTATION.md`.

### CP-A: Contract Coherence

Before any implementation in a phase: every type added or modified in
that phase has its `Why / First-version / Deliberate omissions / Future
extensions / ArchDox separation` block in `API_DESIGN.md`. If those
sections cannot be written cleanly, the type is wrong.

### CP-B: End-to-End Through Fake

For Phases 3 and later: at least one test runs a full Flower flow,
driven by the actual `AiHarnessFlowFactory`, against
`FakeAiModelGateway`, producing real `AiFinding` objects through a real
`FindingSink`. If a feature cannot be reached through this path, it is
not real yet.

### CP-C: No Forbidden Imports

Automated check: no `io.github.parkkevinsb.flower.ai.harness.*` class
imports Spring, ArchDox, Jackson, or a vendor SDK. CI fails on violation.

### CP-D: Public Surface Audit

End of Phase 5: produce a list of all public types in the core module
and assert they match the public surface declared in `API_DESIGN.md`.
Any unannounced public type is either added to `API_DESIGN.md` or made
package-private.

### CP-E: ArchDox Boundary Test

End of Phase 6: ArchDox's document QA harness compiles, runs, and
passes its tests using only the published public API of
`flower-ai-harness-core`. No use of internal classes, reflection
workarounds, or shadow-copying of harness code.

### CP-F: Refinement Justified by Evidence

In Phase 7, every change to a core public type must cite a concrete
ArchDox-usage finding from Phase 6 that the change addresses.
"Hypothetical future provider X might want…" is not a justification.

---

## 7. Decision Criteria: Before vs. After ArchDox Integration

The hardest decisions in this project are not "what to build" but "what to
postpone." The criteria below name the threshold for each kind of
addition.

### 7.1 What is allowed before ArchDox integration

A change is in scope before ArchDox application (Phases 0–5) if **all**
of the following hold:

- it is required to make the public lifecycle work end-to-end with the
  fake gateway,
- it appears in the v0 type list in `API_DESIGN.md`,
- removing it would force ArchDox to extend internal classes or duplicate
  framework code.

### 7.2 What is deferred until after ArchDox integration

A change is deferred to Phase 7+ unless ArchDox usage demonstrates
repeated need, if **any** of these apply:

- it adds a new top-level public type,
- it adds an enum value or a sealed-interface case (these are
  backward-incompatible to *remove* once shipped),
- it introduces a new module,
- it requires a network call from core.

### 7.3 What is permanently out of scope for v0

The non-goals list in `ARCHITECTURE.md §13`. These do not enter even
through Phase 7 unless the project's overall scope changes.

### 7.4 When ArchDox needs something core does not have

Three possible outcomes, in order of preference:

1. ArchDox solves it locally (in `document-ai-harness`) using existing
   seams. Most cases land here.

2. The need is genuinely cross-cutting (would help any harness, not just
   document QA). A core change is scheduled for Phase 7 and the
   ArchDox-side solution is treated as a temporary stand-in.

3. The need exposes a wrong boundary. A core API change is required
   immediately, and ArchDox cannot ship until it lands. This is the rare
   case where Phase 6 pauses on Phase 7.

Outcome 1 is the default. Outcome 3 should happen at most once or twice
across the entire ArchDox integration. If it happens more often, the
core design is structurally wrong and the phase plan must be revisited.

---

## 8. ArchDox Integration: Decision Boundary Rules

These rules are how to tell, in any specific situation, whether a
proposed class belongs in `flower-ai-harness-core` or in ArchDox's
`document-ai-harness`.

### Belongs in core

- The concept applies to any harness (not just document QA).
- Removing it would force every harness implementer to re-invent it.
- It has a clean type-theoretic description (no domain words in its
  signature).
- It can be exercised by the fake gateway without any document concept.

### Belongs in ArchDox

- It refers to documents, pages, paragraphs, legal rules, templates,
  inspection reports, or any other ArchDox-specific concept.
- It is a mapping between a harness-level abstraction and an ArchDox
  domain type.
- It is a persistence concern.
- It is a UI / API concern.
- It is a multi-tenant or org-scoping concern.

### Ambiguous cases

Examples and the v0 ruling for each:

| Question                                       | v0 ruling                                              |
| ---------------------------------------------- | ------------------------------------------------------ |
| Should `AiFinding.location` be structured?     | No. Opaque `String`. Document-page semantics are ArchDox's. |
| Should the harness know about retries cost?    | No. Cost accounting is observability/governance work.  |
| Should there be a tenant-id field in core?     | No. Use `AiHarnessRunContext.putAttribute`.            |
| Should `AiHarnessSpec` carry a description?    | No. `harnessId` and `PromptVersion` are enough.        |
| Should validators receive the raw prompt?      | No. They receive the response only. Prompt-aware validation is a future composite policy. |

---

## 9. Test Strategy (Strategic, Not Per-Phase)

Per-phase test specifics are in `PHASED_IMPLEMENTATION.md`. The strategic
shape is:

- **End-to-end-first.** The default test for any harness behavior is "drive
  a real flow through `AiHarnessFlowFactory` against `FakeAiModelGateway`."
- **Unit tests where they pay off.** `JacksonPojoSchemaValidator` in the
  validator module, `RoutingAiModelGateway` dispatch, and
  `MaxAttemptsRefinePolicy` are worth direct unit tests. Most other types are
  exercised end-to-end.
- **Fake provider scenarios are the test corpus.** Each documented
  lifecycle path (happy, retry-then-succeed, retry-then-fail,
  transport-fail-then-retry, immediate-validation-failure) becomes a
  named `FakeResponseProgram` plus a flow test.
- **Tick-budget enforcement.** A reusable test helper asserts that no
  `Step.onTick` takes longer than a configured budget (default: 1 ms,
  per Flower's tick philosophy). Any feature regression here fails CI.
- **No real network in CI.** Provider-specific modules ship with
  contract tests that can be wired against a recording fixture or a
  vendor sandbox, but the core CI pipeline runs only fake-based tests.

---

## 10. Architectural Invariants Check (Pre-Merge)

Restating `ARCHITECTURE.md §15` as a pre-merge checklist. Every PR is
verified against:

- [ ] No new vendor SDK / Spring / ArchDox import in core
- [ ] No internal flow step class is public
- [ ] No `Step` does blocking I/O
- [ ] No new public type missing from `API_DESIGN.md`
- [ ] Fake gateway can express the new capability if the gateway interface
      changed
- [ ] An invariant in `ARCHITECTURE.md §15` was not weakened without an
      explicit decision recorded in this document

PR descriptions are expected to address these explicitly when relevant.

---

## 11. Versioning and Compatibility

v0 is pre-1.0. The first public number once published would be `0.1.0`.
Until 1.0:

- Type signatures may change between minor versions.
- Removing a public type is allowed in any pre-1.0 release.
- `API_DESIGN.md` is the source of truth for what is "public" — anything
  not listed there is treated as internal even if it is `public` in Java
  visibility.

After 1.0 (post-ArchDox stabilization, not in scope of v0):

- Removing or renaming any type listed in `API_DESIGN.md` is a major
  version bump.
- Adding optional fields to records is a minor bump.
- Adding `default` methods to interfaces is a minor bump.
- Adding new modules is a minor bump.

---

## 12. Out-of-Scope for This Plan

These are explicitly *not* covered here because they are not on the v0
critical path:

- License selection (held open per `OPEN_SOURCE_STRATEGY.md`).
- Public release mechanics (Maven Central coordinates, signing).
- Documentation website.
- Community processes (CONTRIBUTING.md, issue templates).
- A marketing or positioning push.
- Any commercial layer.

When v0 lands and ArchDox is running on it, those items get their own
plan. Mixing them into the engineering plan now would dilute the goal.

---

## 13. Definition of Done for v0

v0 is done when:

1. `flower-ai-harness-core`, `flower-ai-harness-validator-jackson`,
   `flower-ai-harness-test`, and `flower-ai-harness-samples` are buildable,
   testable, and internally consistent.
2. The text-review sample runs end-to-end against `FakeAiModelGateway`
   and produces structured `AiFinding`s.
3. ArchDox's document QA harness is implemented against the published
   public API and passes ArchDox's own tests.
4. At least one round of Phase 7 refinement has been completed and any
   ArchDox-driven API changes have been merged.
5. `API_DESIGN.md`, `ARCHITECTURE.md`, and this document accurately
   describe the shipped state.
6. The architectural invariants list (§10) passes against the codebase.

What v0 explicitly does **not** require:

- A real provider implementation (OpenAI / Anthropic / local).
- A Spring AI adapter.
- A Spring Boot starter.
- A published artifact on Maven Central.
- A public README beyond what already exists.
- Any commercial layer or hosted component.

These are post-v0 work and have their own future plans.

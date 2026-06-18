# flower-ai-harness

A lightweight Java harness for reliable AI business steps.

`flower-ai-harness` does not try to be an AI platform, an agent framework, or
a workflow engine. It does one smaller job:

```text
Turn one AI call into a safe, repeatable business execution step.
```

In a real application, calling an AI model is the easy part. The hard part is
everything around it:

```text
What if the model is slow?
What if the call fails?
What if the JSON is malformed?
Should we retry, refine the prompt, or switch models?
How do we cancel the run?
How do we record status for the UI?
How do we test this without calling a real provider?
What happens after a restart?
```

`flower-ai-harness` standardizes that surrounding execution layer.

```text
Spring AI / provider SDK / internal LLM gateway
  = model access

Flower
  = flow and step execution

flower-ai-harness
  = validation, retry/refine, fallback, cancellation, snapshots,
    recovery policy, findings, and testability around an AI step

Your application
  = business workflow, domain data, persistence, UI, and orchestration
```

## Purpose

`flower-ai-harness` is a separate AI Harness framework built on top of
Flower.

It should not live inside the existing `flower` repository at the beginning.
Flower already has a clear identity as a lightweight Java orchestration toolkit
for event-driven and tick-driven workflows inside one JVM. The AI harness layer
should use Flower as its execution foundation while keeping AI-specific
concepts outside Flower core.

The first real validation target is ArchDox, but ArchDox must not become the
place where the generic AI framework grows. ArchDox is a document workflow
platform. `flower-ai-harness` is the reusable AI run lifecycle framework.

At this stage, the project is exploratory. The immediate goal is not to finish
a public framework or decide a business model. The immediate goal is to learn
AI harness engineering by applying a small reusable layer to ArchDox, then
adjusting the abstraction based on real document workflow needs.

## Project Identity

`flower-ai-harness` is a framework-level Java library for building reliable AI
execution blocks inside business workflows. More precisely, it is an **AI
harness framework**: applications plug in domain input, prompt builders,
validators, model gateways, refine policies, finding extractors, and finding
sinks; the harness owns the execution lifecycle around one AI task.

The framework's job is to turn AI work from "call a model and hope" into an
explicit workflow:

```text
prepare prompt
submit model request
wait without blocking Flower workers
validate structured output
retry, refine, or switch models when needed
emit findings/results
trace the run
finish or fail explicitly
```

It is intentionally narrower than a general AI platform:

- not a raw AI SDK wrapper
- not a Spring AI competitor
- not a Temporal or Camunda replacement
- not a general autonomous agent framework
- not an ArchDox module

In one sentence:

```text
Spring AI gives you model access.
flower-ai-harness gives you repeatable AI business workflows.
```

So yes, this project is a framework, but its identity is specific: a lightweight
AI execution harness for Java applications that want validation, retry/refine,
model fallback, cancellation, recovery metadata, testing, and integration
boundaries without adopting a heavy workflow platform.

It deliberately does not own the whole business process. For example, ArchDox
may decide to run a document QA harness, then a legal review harness, compare
the results, and rerun one of them. That larger orchestration belongs to
ArchDox's own Flower flows. `flower-ai-harness` only makes each individual AI
task a safer block to execute.

## Positioning

```text
flower
  = lightweight Java orchestration toolkit
  = Flow / Step / Worker / Engine
  = event-driven and tick-driven application workflow runtime

flower-ai-harness
  = generic AI Harness framework
  = depends on Flower
  = standardizes AI run lifecycle, provider calls, validation, retry/refine

archdox
  = document workflow platform
  = depends on flower-ai-harness
  = applies the harness to document QA, legal review, templates, reports
```

In the ArchDox ecosystem, keep these names separate:

```text
flower-ai-harness
  = generic AI execution framework

archdox-ai-harness
  = ArchDox-specific harness definitions using this framework

archdox-worker
  = ArchDox-controlled action orchestration and policy layer

archdox-agent
  = registered document/photo/artifact execution server
  = not an AI agent
```

The product position is not "Temporal replacement" and not "general agent
framework." The intended space is:

```text
More structured than ad-hoc prompt chains.
Lighter than Temporal/Camunda-style workflow platforms.
More workflow-centered than LangChain-style agent composition.
```

The core value is making AI-assisted business workflows explicit, testable,
retryable, observable, and easy to integrate into Java applications.

## Spring AI Relationship

Spring AI is the preferred Java model-integration layer for production Spring
applications. `flower-ai-harness` does not replace Spring AI. It wraps model
calls as explicit, non-blocking Flower workflow steps with validation,
retry/refine, finding publication, and deterministic tests.

```text
Spring AI gives you model access.
flower-ai-harness gives you repeatable AI business workflows.
```

The core module remains Spring-free. Spring AI support belongs in a separate
adapter module:

```text
flower-ai-harness-spring-ai
  - SpringAiModelGateway implements AiModelGateway
  - internally delegates to Spring AI ChatClient / ChatModel
  - treats AiModelRequest timeout as a harness-level wait bound; provider
    client / HTTP timeouts must still be configured in Spring AI

flower-ai-harness-spring-boot-starter
  - auto-configures SpringAiModelGateway in Spring Boot applications
  - uses a single ChatClient automatically, or a user-provided
    SpringAiModelResolver for multi-model routing
```

Direct provider modules such as OpenAI, Anthropic, or local-model adapters are
later options, not the first production integration path.

## Naming Decision

Recommended repository name:

```text
flower-ai-harness
```

Reasons:

- `flower-ai` is too broad and sounds like a general AI ecosystem for Flower.
- `flower-harness` hides the AI-specific purpose and can be confused with
  Flower itself.
- `flower-ai-harness` clearly says this is an AI harness layer that uses Flower.

Recommended package prefix:

```text
io.github.parkkevinsb.flower.ai.harness
```

Recommended initial module names:

```text
flower-ai-harness-core
flower-ai-harness-validator-jackson
flower-ai-harness-spring-ai
flower-ai-harness-spring-boot-starter
flower-ai-harness-test
flower-ai-harness-samples
```

Start small. Add modules only when repeated usage justifies them.

## Core Boundary

`flower-ai-harness` owns generic AI execution concerns:

- AI run lifecycle
- AI step execution pattern on top of Flower
- provider gateway interface
- model request and response objects
- retry and backoff policy
- critique/refine loop
- structured output validation
- schema validation abstraction
- finding/result model
- prompt template and prompt versioning model
- run context and trace metadata
- fake provider for deterministic tests

ArchDox owns document-specific concerns:

- inspection/report document domain
- report snapshots
- document template config
- output layout
- legal and domain rule sets
- document QA prompts
- legal review prompts
- document-specific finding mapping
- persistence into ArchDox DB
- display in ArchDox UI

ArchDox-specific concepts must not leak into `flower-ai-harness`.

## First Version Scope

The first version should be intentionally narrow.

Proposed core classes:

```text
AiHarnessFlowFactory
AiModelGateway
AiModelRequest
AiModelResponse
AiHarnessRunContext
AiFinding
AiFindingSeverity
AiSchemaValidator
AiRefinePolicy
ModelFallbackRefinePolicy
FakeAiModelGateway
SpringAiModelGateway
```

The goal is not to build a complete AI platform. The goal is to extract the
smallest reusable layer needed to run one practical ArchDox document QA harness
cleanly.

## Flower Execution Pattern

Flower steps should remain short and non-blocking. AI calls must respect that
model.

Preferred shape:

```text
onEnter:
  start AI work through an async gateway or external service
  record run id / request id / timeout

onTick:
  if the AI result is not ready, return stay()
  if the result is ready, validate it
  if valid, store result and return done()
  if invalid and retry/refine is allowed, schedule refine and return stay()
  if exhausted, return fail(...)
```

Avoid blocking a Flower worker tick with long LLM calls, sleeps, polling loops,
or synchronous network waits.

## Operational Control Model

Flower and `flower-ai-harness` split durability responsibilities:

```text
Flower checkpoint/resume
  = Flow position, current step, stepNo, recovery policy

flower-ai-harness run store
  = AI run status, attempts, current request, provider call id,
    latest response metadata, terminal reason

host application persistence
  = business request, document snapshot, findings, UI state, audit records
```

Flower can restore where a flow was. The harness decides what an AI run should
do after that point: keep waiting, retry the current request, fail safely, or
ignore a late provider response. That policy is intentionally harness-level
because Flower does not know provider call semantics or AI cost risk.

The first operational control surface is deliberately small:

```text
AiHarnessRunStatus
AiHarnessRunSnapshot
AiHarnessRunStore
AiRecoveryPolicy
AiCancellationToken
AiBudgetPolicy
AiResourceGovernor
```

- `AiHarnessRunStore` records snapshots of the AI run lifecycle. The default
  store is no-op; applications can provide JDBC/JPA/etc. adapters later.
- `AiRecoveryPolicy` decides what to do with a persisted run snapshot when a
  host application rebuilds a harness flow. The built-in conservative policy
  retries the current request when one exists, marks cancelled snapshots as
  cancelled, and otherwise fails recoverably.
- `AiCancellationToken` is per run. If cancellation is requested before or
  during provider wait, the harness calls `AiModelCall.cancel()`, marks the run
  `CANCELLED`, persists a snapshot, and terminates the Flower flow.
- `AiBudgetPolicy` runs before every provider submission. It is the guardrail
  for retry/cost runaway.
- `AiResourceGovernor` is a non-blocking provider-call gate. If no permit is
  available, the step returns `stay()` and tries again on the next tick.

This does not turn the project into a durable workflow platform. It gives AI
workflows the extra operational metadata and control points that Flower
intentionally leaves to the application layer. Recovery is explicit:
applications load an `AiHarnessRunSnapshot` and call
`AiHarnessFlowFactory.createRecoveredFlow(...)`. The harness does not replay
provider calls or ship a database-backed durable engine.

The built-in conservative recovery policy is at-least-once: if a snapshot has
a current request, recovery submits that request again. This is simple and
safe for many review workflows, but it can duplicate provider cost. Expensive
or non-idempotent harnesses should provide a custom `AiRecoveryPolicy` that
fails recoverably or sends the run to manual review instead of retrying.

## Suggested Harness Lifecycle

```text
1. Build input context
2. Render prompt from versioned template
3. Submit model request
4. Wait for model response
5. Parse structured output
6. Validate schema
7. Critique or refine if needed
8. Produce findings/results
9. Persist or publish result through host application adapter
10. Finish or fail the Flower flow
```

## ArchDox First Application

ArchDox should introduce a document-specific adapter layer rather than growing
the generic framework inside the ArchDox codebase.

Suggested ArchDox-side package/module:

```text
document-ai-harness
```

Suggested ArchDox-side classes:

```text
DocumentQaHarnessFlow
DocumentQaPromptBuilder
DocumentFindingMapper
ArchDoxAiHarnessPersistenceAdapter
```

ArchDox consumes `flower-ai-harness` as a dependency and supplies its own:

- document snapshot loader
- template/rule context
- prompt builders
- finding mappers
- persistence adapters
- UI/API integration

## Design Principles

- Keep Flower core clean. Do not add AI concepts to the existing Flower project.
- Keep the harness framework domain-neutral.
- Let ArchDox be the first customer, not the framework host.
- Prefer explicit workflow steps over magical agent autonomy.
- Prefer provider abstraction over direct vendor coupling.
- Prefer structured results over free-form text.
- Prefer deterministic tests with fake providers.
- Move only repeated, domain-neutral abstractions into the framework.
- Keep prompt versions traceable.
- Treat retry/refine as a first-class lifecycle, not ad-hoc loop code.

## Non-Goals

The first version should not try to be:

- a Temporal replacement
- a Camunda/BPMN engine
- a general autonomous agent framework
- a RAG platform
- a vector database abstraction
- a prompt marketplace
- a full observability platform
- an ArchDox-specific document QA library

Those can integrate later if needed, but they should not define the initial
architecture.

## Open Questions For Detailed Design

- Should `AiModelGateway` be fully async, or should sync providers be wrapped by
  an async executor owned by the host application?
- What is the minimal structured output format for `AiFinding`?
- Should schema validation use JSON Schema, Java records, Jackson validation,
  or a pluggable interface only?
- How should prompt versioning be represented: code constants, resource files,
  database-backed registry, or host-provided adapter?
- How much trace metadata belongs in core versus observability modules?
- Should refine loops be represented as Flower steps, internal step state, or a
  reusable mini-policy object?
- Which Spring Boot properties should remain in the starter after ArchDox
  proves the wiring shape?
- What should the first sample be: tiny document QA, generic text review, or
  ArchDox-adjacent but domain-neutral document inspection?

## Recommended Next Step

Ask Claude to turn this brief into a concrete architecture proposal with:

- module layout
- package layout
- initial public interfaces
- data model sketches
- Flower step patterns
- test strategy
- ArchDox integration boundary
- phased implementation plan

The architecture should stay small enough that the first implementation can be
used by one ArchDox Document QA harness without committing to a large platform.

## Current Decision Summary

- Keep `flower-ai-harness` as a separate project from both Flower and ArchDox.
- Do not add AI concepts to Flower core.
- Do not grow a generic AI framework inside ArchDox.
- Keep `flower-ai-harness-core` Spring-free, but make Spring AI the preferred
  production model integration adapter after the core/test lifecycle is proven.
- Provide a thin Spring Boot starter for the common case: one `ChatClient` or a
  host-supplied `SpringAiModelResolver`.
- Use ArchDox as the first practical validation target.
- Keep commercialization possibilities open, but do not optimize for them yet.
- Focus first on understanding and proving AI harness engineering.
- Keep operational recovery small: snapshot-based retry/cancel/fail decisions,
  not a Temporal-style durable runtime.
- Refine the framework only after real ArchDox usage shows repeated patterns.

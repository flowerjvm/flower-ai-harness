# Flower JVM AI Harness

Flower JVM AI Harness is a lightweight Java framework for running AI business
steps with validation, retry/refine, fallback, cancellation, recovery metadata,
and deterministic tests.

It does not try to be an AI platform, an agent framework, or a workflow engine.
It does one smaller job:

```text
Turn one AI call into a safer, repeatable business execution step.
```

In a real application, calling a model is usually the easy part. The hard part
is everything around that call:

```text
What if the model is slow?
What if the call fails?
What if the JSON is malformed?
Should we retry, refine the prompt, or switch models?
How do we cancel the run?
How do we record status for the UI?
How do we test without calling a real provider?
What happens after a restart?
```

This project standardizes that surrounding execution layer for Java
applications that already have business workflows.

## Position

```text
Spring AI / provider SDK / internal LLM gateway
  = model access

Flower
  = Flow / Step execution

Flower JVM AI Harness
  = validation, retry/refine, fallback, cancellation, snapshots,
    recovery policy, findings, and testability around one AI step

Your application
  = business workflow, domain data, persistence, UI, audit, and orchestration
```

In one sentence:

```text
Spring AI gives you model access.
Flower JVM AI Harness gives you repeatable AI business steps.
```

## Repositories

- Flower runtime: <https://github.com/flowerjvm/flower>
- AI Harness: <https://github.com/flowerjvm/flower-ai-harness>
- Bloom event bus: <https://github.com/flowerjvm/bloom>
- Samples: <https://github.com/flowerjvm/flower-sample>

## What It Is For

- wrapping an AI call as an explicit Flower flow/step pattern
- validating structured model output
- retrying or refining when validation fails
- switching models through policy when a provider fails
- limiting cost and retry runaway through budget policies
- cancelling in-progress AI runs from the host application
- recording run snapshots for UI/status/recovery
- testing AI workflows with fake providers

## What It Is Not

- not a Spring AI replacement
- not an OpenAI/Anthropic SDK replacement
- not a Temporal or Camunda replacement
- not a general autonomous agent framework
- not a RAG platform
- not a prompt marketplace
- not a domain-specific document QA library

The harness owns the lifecycle around one AI task. The host application owns
the larger business process.

For example, an application may run:

```text
document QA harness
-> legal review harness
-> narrative generation harness
-> approval workflow
```

The application decides that order. The harness only makes each AI task safer
to execute.

## Spring AI Relationship

Spring AI is the preferred Java model-integration layer for Spring
applications. Flower JVM AI Harness does not replace it. The Spring AI adapter
turns Spring AI calls into explicit, non-blocking Flower workflow steps with
validation, retry/refine, finding publication, cancellation, and tests.

The core module remains Spring-free. Spring AI support lives in separate
modules.

## Modules

```text
flower-ai-harness-core
  Core model, run context, flow factory, lifecycle steps, policies, snapshots.

flower-ai-harness-validator-jackson
  Jackson-based structured output/schema validation support.

flower-ai-harness-test
  Fake model gateway and test helpers for deterministic harness tests.

flower-ai-harness-spring-ai
  SpringAiModelGateway adapter for Spring AI ChatClient / ChatModel usage.

flower-ai-harness-provider-openai-compatible
  Raw HTTP adapter for OpenAI-compatible /chat/completions endpoints.

flower-ai-harness-provider-openai
  Official OpenAI Java SDK adapter for OpenAI chat completions.

flower-ai-harness-spring-boot-starter
  Spring Boot auto-configuration for common Spring AI gateway wiring.

flower-ai-harness-samples
  Small examples showing how the modules fit together.
```

## Execution Shape

AI calls should not block a Flower worker tick. The preferred shape is:

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

The reusable step pattern is:

```text
PreparePromptStep
AwaitResponseStep
ValidateResponseStep
RefineDecisionStep
EmitFindingsStep
```

Applications can compose these directly or wrap them in domain-specific
harness flows.

## Operational Boundary

Flower and the AI harness split durability responsibilities:

```text
Flower checkpoint/resume
  = Flow position, current step, stepNo, recovery policy

AI harness run store
  = AI run status, attempts, current request, provider call id,
    latest response metadata, terminal reason

Host application persistence
  = business request, domain snapshot, findings, UI state, audit records
```

Flower can restore where a flow was. The harness decides what an AI run should
do after that point: keep waiting, retry the current request, fail safely, or
ignore a late provider response. That policy belongs here because Flower core
does not know provider call semantics or AI cost risk.

The built-in conservative recovery policy is at-least-once: if a snapshot has
a current request, recovery may submit that request again. This is simple and
safe for many review workflows, but it can duplicate provider cost. Expensive
or non-idempotent harnesses should provide a custom recovery policy that fails
recoverably or sends the run to manual review.

## Status

This project is early. The core shape exists, but APIs may still move before a
first Maven Central release.

The intended maturity split is:

```text
core/test lifecycle: stabilize first
Spring AI adapter: preferred production integration path
OpenAI-compatible HTTP adapter: available for proxies and custom gateways
OpenAI SDK adapter: available for direct OpenAI integration
Anthropic SDK module: next provider expansion step
agent runtime/governance: separate project, not this harness
```

## Build

Artifacts are not published to Maven Central yet. Install Flower locally first,
then build this project:

```bash
cd ../flower && mvn install
cd ../flower-ai-harness && mvn test
```

Install local snapshots:

```bash
mvn install
```

## License

Apache License 2.0.

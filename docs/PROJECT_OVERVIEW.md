# Project overview

## Purpose

Flower JVM AI Harness turns one AI-dependent task into a controlled,
repeatable business execution unit inside a Flower flow.

The model call itself is usually not the difficult part. Production
applications also need to:

- prepare a versioned prompt;
- submit work without blocking a workflow worker;
- validate structured output;
- decide whether to retry, refine, or switch models;
- enforce attempt and concurrency limits;
- cancel in-progress work;
- expose run state for UI, audit, or recovery;
- convert validated output into business findings;
- test the complete lifecycle without a real provider.

The harness standardizes that outer lifecycle.

## Position in the stack

```text
Provider SDK / Spring AI / internal model service / agent runner
  supplies model or agent execution

Flower
  supplies Flow, Step, Worker, Engine, and tick-driven execution

flower-ai-harness
  supplies validation, refine decisions, fallback, cancellation,
  operational snapshots, findings, and deterministic tests

Host application
  supplies business inputs, prompts, persistence, UI, audit,
  authorization, and larger workflow orchestration
```

The central design sentence is:

```text
Make AI a controlled step inside a business workflow.
```

## What the project owns

- provider-neutral request, response, and asynchronous call contracts;
- model gateway routing;
- prompt identity and prompt-building seams;
- structured-output validation contracts;
- retry/refine and model-fallback policies;
- generic findings and host publication seams;
- run status, snapshots, cancellation, budgets, and resource governance;
- conservative recovery decisions;
- deterministic fake-provider test support;
- provider and Spring integration modules.

## What the project does not own

- autonomous agent planning or tool-loop internals;
- a general workflow engine;
- application persistence or database schema;
- domain models such as documents, pages, legal rules, or tenant records;
- RAG, vector stores, embeddings, or retrieval;
- a hosted dashboard or control plane;
- distributed execution;
- exactly-once provider execution;
- business authorization and account-sharing policy.

## Core boundary

The repository is intentionally split:

```text
flower
  no AI concepts

flower-ai-harness-core
  no Spring, Jackson, vendor SDK, or host-domain concepts

adapter modules
  Spring AI, OpenAI-compatible HTTP, OpenAI SDK, Anthropic SDK, Jackson

host application
  domain-specific prompts, schemas, findings, storage, and orchestration
```

This boundary keeps the lifecycle reusable while allowing each application to
choose its provider and domain model.

## The unit of execution

A harness type is described by an immutable `AiHarnessSpec<I, T>`:

- `I` is the host input type;
- `T` is the validated structured output type;
- the spec selects the default model, prompt builder, validator, refine
  policy, finding extractor, finding sink, operational policies, and trace
  listeners.

`AiHarnessFlowFactory<I, T>` creates one `AiHarnessFlow` per run. The returned
value contains:

- the Flower `Flow` submitted to a worker;
- the `AiHarnessRunContext` used for identity, state, attempts, outputs, and
  correlation.

Host code should use the factory rather than instantiate internal lifecycle
steps.

## Model providers and agent runners

The current providers call model APIs through Spring AI, OpenAI-compatible
HTTP, official vendor SDKs, or an external agent subprocess.

An external agent runner is still compatible with the same design. From the
outer harness perspective, an agent run is also:

```text
request -> asynchronous work -> final raw output or failure
```

`flower-ai-harness-provider-agent-cli` adapts that execution shape behind
`AiModelGateway`; it does not move agent-loop concepts into core. See
[`AGENT_CLI_PROVIDER.md`](AGENT_CLI_PROVIDER.md).

## Typical use cases

- document or text review producing structured findings;
- classification with schema validation and retry;
- report or narrative generation with output validation;
- cheap-model first pass with stronger-model fallback;
- AI-assisted checks embedded in an existing Java/Spring workflow;
- local Codex or Claude maintenance-agent execution through a governed runner.

## Maturity

The repository has a released `v0.1.2` tag. The release aligns the harness
with Flower `0.1.1` without changing the harness API.

The lifecycle, validation, refine, operational-control, fake-provider, Spring,
and direct provider modules are implemented. APIs remain pre-1.0 and may
change between minor releases.

See [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) for the exact
implemented and deferred capability list.

## Terminology

| Term | Meaning |
| --- | --- |
| Harness | The outer lifecycle around one AI-dependent business task. |
| Provider | An `AiModelGateway` implementation that executes a request. |
| Call | A non-blocking `AiModelCall` handle returned by a provider. |
| Refine | A policy decision to retry with an adjusted or differently routed request. |
| Finding | A provider-neutral business observation derived from validated output. |
| Run snapshot | Persistable harness-level state; not a Flower checkpoint. |
| Recovery | A policy decision to retry, fail, or mark cancelled from a snapshot. |
| Agent runner | An external process that owns its own tool loop and returns a final artifact. |

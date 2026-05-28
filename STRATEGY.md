# flower-ai-harness Strategy Notes

## Strategic Thesis

`flower-ai-harness` should become a practical Java-first framework for
building reliable AI-assisted business workflows.

This is a possible long-term direction, not the immediate execution priority.
The current priority is to validate the harness engineering approach through
ArchDox and improve the abstraction from real usage.

The goal is not to compete head-on with Temporal, LangGraph, Spring AI, or
OpenAI Agents SDK. If the project later becomes public or commercial, it should
occupy a smaller and useful space:

```text
AI workflow harnesses for Java/Spring business applications
that need explicit steps, validation, retry/refine loops, findings,
human review, and deterministic tests without adopting a large platform.
```

## Market Context

Current neighboring projects already own important categories:

- Temporal owns durable execution at serious production scale.
  See: https://temporal.io/
- LangGraph owns graph-shaped agent workflows with persistence/checkpoints.
  See: https://docs.langchain.com/oss/python/langgraph/durable-execution
- Spring AI owns Java/Spring model integration, tool calling, and structured
  output helpers.
  See: https://docs.spring.io/spring-ai/reference/
- OpenAI Agents SDK owns model-native agent runtime concepts such as tools,
  handoffs, guardrails, and tracing.
  See: https://developers.openai.com/api/docs/guides/agents

`flower-ai-harness` should integrate with or learn from these projects, not
try to clone them.

Spring AI is the preferred Java model-integration layer. The strategic posture
is:

```text
Spring AI gives you model access.
flower-ai-harness gives you repeatable AI business workflows.
```

`flower-ai-harness` should not compete with Spring AI's provider abstraction.
It should wrap Spring AI calls as explicit, non-blocking Flower workflow steps
with validation, retry/refine, finding publication, and deterministic tests.

## Recommended Position

Recommended public positioning:

```text
flower-ai-harness is a lightweight Java framework for building
explicit, testable AI workflow harnesses on top of Flower.
```

Longer version:

```text
Use flower-ai-harness when an AI task is not just one prompt call:
it needs input preparation, model calls, structured output validation,
retry/refine loops, findings, human review, persistence hooks, and clear
workflow state inside a Java application.
```

Avoid positioning it as:

- a general agent framework
- a Temporal replacement
- a LangGraph clone
- a prompt-chain library
- an AI platform
- an ArchDox submodule

## Differentiation

The strongest differentiation should be:

1. Java-first and Spring-friendly.
2. Flower-based explicit workflow steps.
3. Harness-oriented rather than chat/agent-oriented.
4. Structured findings and validation as first-class concepts.
5. Deterministic local testing through fake providers and Flower ticks.
6. Small enough to embed inside existing business applications.

This is the important message:

```text
Not "let the agent do everything."
Instead: "make AI a controlled step inside a real business workflow."
```

## Target Users

Best early users:

- Java/Spring teams adding AI to existing business applications.
- SaaS teams that need document review, classification, QA, or report
  generation workflows.
- Teams that want structured AI outputs and reviewable findings.
- Developers who think Temporal is too heavy for their current need.
- Developers who think LangChain/LangGraph is too Python/agent-centric for
  their Java application.

Poor early targets:

- companies already committed to Temporal Cloud for all workflows
- teams looking for no-code automation
- teams building open-ended autonomous agents
- teams needing full RAG/vector infrastructure out of the box

## Product Shape

Start as an open-source developer framework.

This remains an option, not a current commitment. The project should first earn
its shape through ArchDox integration.

Potential later commercial layers:

- hosted trace/run dashboard
- prompt and harness version registry
- run analytics and cost monitoring
- evaluation suite for harness regression tests
- team collaboration around prompt review and approval
- enterprise support for Java/Spring teams
- ArchDox-specific commercial harness packs

Keep the open-source core useful on its own. Commercial value should come from
operations, visibility, governance, and domain packs rather than locking away
the basic framework.

## Open Source Strategy

Recommended license direction:

- Use a permissive license if adoption and ecosystem growth are the priority.
- Consider Apache-2.0 if patent clarity matters.
- Avoid a restrictive license in the beginning unless the business model is
  already clear.

Recommended repository contents for the first public release:

- crisp README
- one tiny runnable sample
- fake provider tests
- one Spring Boot sample
- architecture decision records
- "when not to use this" section
- ArchDox kept separate as proof, not bundled as core

## First Wedge

The first wedge should be document QA / document review.

Reasons:

- It maps naturally to structured findings.
- It needs validation and review.
- It is easy to explain.
- ArchDox can validate it in a real product context.
- It does not require broad autonomous agent behavior.

Example public sample:

```text
document-review-sample
  input: document text or markdown
  AI step: identify issues
  validation step: ensure findings have severity, location, rationale
  refine step: ask model to repair invalid findings
  output: structured findings JSON
```

Keep the sample domain-neutral. ArchDox can have its own private or separate
integration.

## Roadmap

### Phase 0: Harness Learning And ArchDox Validation

- build a very small harness core
- apply it to one ArchDox document QA workflow
- learn the real failure modes
- identify which abstractions repeat
- keep business strategy open but secondary

### Phase 1: Credible Core

- core interfaces
- fake provider
- structured finding model
- schema validator abstraction
- simple refine policy
- Flower flow helper
- deterministic tests
- one tiny sample

### Phase 2: Java/Spring Usability

- Spring AI adapter (`flower-ai-harness-spring-ai`)
- Spring Boot starter after manual wiring stabilizes
- direct provider adapters only when they provide value beyond Spring AI
- prompt resource loading
- basic trace listener
- sample REST endpoint
- documentation for host application integration

### Phase 3: Real-World Validation

- ArchDox document QA harness
- feedback from actual document workflows
- clarify which abstractions are truly generic
- remove or demote abstractions that only ArchDox needs

### Phase 4: Adoption

- publish artifacts
- improve README and examples
- write comparison docs
- add recipes for common harnesses
- collect issue feedback from outside users

### Phase 5: Commercial Options

- dashboard or run viewer
- prompt/version registry
- evaluation and regression runner
- hosted or self-hosted governance features
- paid support or domain harness packs

## Business Model Options

Best-fit models:

1. Open-core:
   keep core framework open; sell dashboards, governance, prompt registry,
   analytics, and team features.

2. Consulting-to-product:
   use ArchDox and similar document workflows to discover repeatable harness
   patterns, then productize the repeated pieces.

3. Domain harness packs:
   sell packaged document QA, legal review, compliance review, or report review
   harnesses that depend on the open core.

4. Enterprise support:
   support Java/Spring teams adopting the framework in regulated or internal
   workflow-heavy environments.

Avoid depending only on:

- generic API wrapper monetization
- prompt templates alone
- yet another hosted chat UI
- broad autonomous agent branding

## Messaging

Useful phrases:

- "AI workflow harnesses for Java applications"
- "Controlled AI steps inside real business workflows"
- "Structured findings, validation, and retry/refine loops"
- "Flower-powered AI workflow execution"
- "Small enough to embed, explicit enough to trust"

Avoid overclaiming:

- "enterprise workflow platform"
- "Temporal replacement"
- "general agent OS"
- "fully autonomous business automation"

## Key Risk

The biggest risk is building a generic framework before the repeated use cases
are proven.

Mitigation:

- keep the core small
- validate with ArchDox first
- only promote repeated patterns into the framework
- keep domain-specific logic outside the framework
- document non-goals aggressively

## Recommended Immediate Move

Create the technical skeleton only after the architecture is clarified:

1. Keep this project separate from Flower and ArchDox.
2. Ask for a concrete architecture proposal from this brief.
3. Implement the smallest core.
4. Apply it to one ArchDox document QA flow as the first real integration.
5. Build a small public-style sample only if it helps clarify the abstraction.
6. Revise the abstractions after real usage.

The winning strategy is not to look big early. The winning strategy is to be
small, understandable, and obviously useful to Java teams adding AI to serious
business workflows.

## Current Strategic Posture

Commercialization is intentionally not the current priority.

Keep the option open, but do not let monetization ideas distort the first
engineering decisions. The next useful milestone is not a market launch. It is
a working ArchDox document QA harness that teaches which parts of the harness
should truly be generic.

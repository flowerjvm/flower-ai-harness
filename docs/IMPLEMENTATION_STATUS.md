# Implementation status

Last reviewed: 2026-07-23.

This document is the maintained status summary. It distinguishes code that
exists from plans and historical design intent.

## Version and repository state

- Parent/reactor version: `0.1.2`.
- Latest repository release tag: `v0.1.2`.
- Java release: 21.
- Build system: Maven.
- Reactor modules: ten.
- CI verification command: `mvn -B -ntp clean verify`.
- CI operating systems: Ubuntu and Windows.

The repository contains 35 test classes across core, adapters, test support,
validators, starter, and samples.

Local verification on 2026-07-23:

- full clean reactor verification passed;
- 108 tests passed with zero failures, errors, or skips;
- agent CLI provider success, failure, timeout, cancellation, child-process,
  concurrency, environment, and refine-retry tests passed on Windows;
- Codex SDK `0.144.5` and Claude Agent SDK `0.3.211` package imports were
  verified without making a live account call;
- core forbidden-dependency and Checkstyle checks passed;
- `io.github.flowerjvm:flower-ai-harness-provider-agent-cli:0.1.1` and its
  transitive dependencies resolved successfully from a clean temporary Maven
  repository.

## Implemented

### Core lifecycle

- [x] Provider-neutral `AiModelRequest` and `AiModelResponse`.
- [x] Non-blocking `AiModelGateway` and `AiModelCall`.
- [x] Provider routing by `ModelId.provider()`.
- [x] Versioned role-based prompts.
- [x] Structured validation contract.
- [x] Retry/refine decisions.
- [x] Same-model retry policy.
- [x] Multi-model fallback plan and policy.
- [x] Generic findings, extraction, and sink seams.
- [x] Immutable harness specification.
- [x] Factory-built Flower lifecycle flow.
- [x] Per-run context and typed host attributes.

### Operational controls

- [x] Run statuses and snapshots.
- [x] No-op and in-memory run stores.
- [x] Per-run cancellation token.
- [x] Pre-submit budget policy.
- [x] Non-blocking resource governor and semaphore implementation.
- [x] Trace listener lifecycle callbacks.
- [x] Conservative snapshot recovery policy.
- [x] Recovered-flow construction.

### Validation and tests

- [x] Jackson POJO validator module.
- [x] Deterministic fake gateway.
- [x] Immediate, delayed, failing, and sequence fake programs.
- [x] Deterministic test clock.
- [x] Test assertions and JUnit helper.
- [x] End-to-end text review sample.
- [x] Happy, refine-retry, exhausted retry, transport failure, and model
  fallback sample scenarios.

### Production integration modules

- [x] Spring AI gateway adapter.
- [x] Thin Spring Boot auto-configuration.
- [x] Vendor-neutral agent CLI/subprocess provider.
- [x] Isolated per-call agent workspaces and versioned file contract.
- [x] Agent process timeout, cancellation, process-tree termination, and
  shutdown.
- [x] Subscription-oriented environment filtering with API-key denylist.
- [x] Codex SDK and Claude Agent SDK reference runners.
- [x] OpenAI-compatible `/chat/completions` provider.
- [x] Official OpenAI Java SDK provider.
- [x] Official Anthropic Java SDK provider.

### Build and release infrastructure

- [x] Java 21 Maven reactor.
- [x] Core forbidden-dependency enforcement.
- [x] Checkstyle-based forbidden import checks.
- [x] Ubuntu and Windows GitHub Actions CI matrix.
- [x] Node syntax validation for Codex and Claude runner examples.
- [x] Maven Central release workflow.
- [x] Source, Javadoc, signing, and Central publication profile.
- [x] `v0.1.0` release tag.
- [x] `v0.1.1` release tag with the agent CLI provider.
- [x] `v0.1.2` compatibility release aligned with Flower `0.1.1`.

## Active work

There is no active implementation plan in this repository. The completed
agent CLI plan is archived in
[`archive/AGENT_CLI_PROVIDER_PLAN.md`](archive/AGENT_CLI_PROVIDER_PLAN.md).

## Not implemented

These capabilities are deliberately absent unless an active plan says
otherwise:

- [ ] Token streaming.
- [ ] Harness-managed tool/function-call loops.
- [ ] Multi-agent handoff.
- [ ] Database-backed run store.
- [ ] Durable active-call reattachment.
- [ ] Exactly-once recovery.
- [ ] Startup scanner or provider-call reconciliation.
- [ ] Distributed rate limiting.
- [ ] Token/cost estimation framework.
- [ ] JSON Schema validator module.
- [ ] Prompt registry or prompt-loading backend.
- [ ] OpenTelemetry exporter module.
- [ ] RAG, embedding, or vector-store abstractions.
- [ ] Hosted dashboard or governance control plane.

## Known limitations

### Recovery can duplicate work

Snapshots store the provider call ID but not a live call handle. Generic
recovery retries the current request and can duplicate provider cost or
side effects.

### Cancellation is best effort

Core propagates cancellation through `AiModelCall.cancel()`. The actual ability
to stop work depends on each provider implementation and its underlying SDK,
HTTP client, or process.

### Callback boundaries are synchronous

`FindingSink` and `TraceListener` run on lifecycle threads and must return
quickly. Slow persistence and network work should be handed off.

### Real account proof is manual

Normal tests intentionally do not use a logged-in Codex or Claude account.
The repository includes reference runners, but each operator must perform an
opt-in local contract proof and confirm the selected account, billing route,
permissions, and vendor terms.

### APIs are pre-1.0

Public APIs may change between minor releases. All modules currently share one
reactor version.

## External validation

ArchDox and other host repositories may consume the harness, but their current
integration status is not asserted by this repository's test suite. Treat
external repository documentation and builds as separate sources.

## Next recommended sequence

1. Run one opt-in local Codex SDK contract proof.
2. Run one opt-in local Claude Agent SDK contract proof.
3. Connect validated harness output to a governed `flower-action-runtime`
   `ActionProposal` boundary.
4. Decide retention and orphan-reconciliation policy after operational use.

## Updating this document

Update this file whenever:

- a module is added or removed;
- a major capability is implemented;
- a plan becomes active, completed, or abandoned;
- the release line changes;
- a known limitation is removed or materially altered;
- CI or platform support changes.

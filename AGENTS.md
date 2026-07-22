# Agent guide

This file is the starting point for AI agents and new contributors working in
this repository.

## Read this first

Read the maintained documents in this order:

1. [`README.md`](README.md) — short public-facing introduction.
2. [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md) — project concept,
   scope, and terminology.
3. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — current runtime,
   dependency, lifecycle, and recovery architecture.
4. [`docs/MODULES.md`](docs/MODULES.md) — module catalog and dependency graph.
5. [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) — what is
   implemented, active, and deliberately deferred.
6. [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — build, test, and change
   workflow.

Active plans are under [`docs/plans/`](docs/plans/). Historical design records
are under [`docs/archive/`](docs/archive/) and are not current specifications.

## Source-of-truth order

When sources disagree, use this order:

1. Java sources, POM files, and tests.
2. Maintained documents under `docs/`.
3. Active plans under `docs/plans/`.
4. Archived documents under `docs/archive/`.

Do not implement behavior only because it appears in an archived API sketch or
phase checklist.

## Current repository snapshot

- Java: 21.
- Build: Maven multi-module reactor.
- Development version: `0.1.3-SNAPSHOT`.
- Latest repository release tag: `v0.1.2`.
- Current modules: ten, listed in [`docs/MODULES.md`](docs/MODULES.md).
- The vendor-neutral CLI/subprocess agent provider is implemented and
  documented in
  [`docs/AGENT_CLI_PROVIDER.md`](docs/AGENT_CLI_PROVIDER.md).

Update [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) if this
snapshot changes materially.

## Architectural invariants

Preserve these rules unless an explicit architecture change is requested:

1. `flower-ai-harness-core` depends only on Flower and the JDK at compile time.
2. Core contains no Spring, Jackson, vendor SDK, or host-application types.
3. Flower worker ticks do not block on model, network, filesystem, process, or
   database latency.
4. `AiModelGateway.submit()` returns promptly and long-running work stays
   behind `AiModelCall`.
5. `AiModelCall.poll()` is cheap and non-blocking.
6. Internal lifecycle steps remain package-private; hosts use
   `AiHarnessFlowFactory`.
7. Validation, refine policy, provider transport, and business finding mapping
   remain separate concerns.
8. Adding a provider should not require a core change.
9. `FindingSink` and `TraceListener` callbacks must return quickly.
10. Generic recovery is at-least-once and does not pretend to restore a
    process-local provider call handle.

## Working method

Before changing code:

- inspect the relevant module POM, package, and tests;
- check `git status --short` and preserve unrelated user changes;
- identify whether the change belongs in core, an adapter module, or the host
  application;
- read the active plan if one exists.

When changing behavior:

- add or update tests in the same module;
- prefer end-to-end harness tests through `FakeAiModelGateway` for lifecycle
  behavior;
- keep provider contract tests deterministic and offline;
- update the maintained documentation in the same change.

When adding a module:

- add it to the parent `<modules>` list;
- add its version to parent dependency management;
- keep the shared reactor version;
- document it in `README.md`, `docs/MODULES.md`, and
  `docs/IMPLEMENTATION_STATUS.md`;
- ensure the release profile includes or intentionally excludes it.

## Verification commands

Run the full reactor:

```bash
mvn -B -ntp clean verify
```

Run one module and its dependencies:

```bash
mvn -B -ntp -pl flower-ai-harness-core -am test
```

Run the sample module:

```bash
mvn -B -ntp -pl flower-ai-harness-samples -am test
```

Release instructions live in [`docs/RELEASING.md`](docs/RELEASING.md).

## Documentation maintenance

The maintained docs describe current facts, not aspirations:

- put current behavior in `docs/ARCHITECTURE.md`;
- put module inventory in `docs/MODULES.md`;
- put completed and pending work in `docs/IMPLEMENTATION_STATUS.md`;
- put a concrete, not-yet-implemented change in `docs/plans/`;
- move superseded plans to `docs/archive/`.

Avoid creating another top-level design document. Extend the existing
maintained document or add a scoped plan under `docs/plans/`.

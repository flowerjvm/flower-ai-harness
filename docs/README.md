# Documentation guide

The documentation is organized by purpose so that a new contributor can move
from project concept to implementation details without reconstructing history
from scattered design notes.

## Recommended reading paths

For a first visit:

1. [`PROJECT_OVERVIEW.md`](PROJECT_OVERVIEW.md)
2. [`ARCHITECTURE.md`](ARCHITECTURE.md)
3. [`MODULES.md`](MODULES.md)
4. [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md)

Before making a code change:

1. [`../AGENTS.md`](../AGENTS.md)
2. [`DEVELOPMENT.md`](DEVELOPMENT.md)
3. The relevant module section in [`MODULES.md`](MODULES.md)
4. The relevant active plan under [`plans/`](plans/)

Before publishing:

1. [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md)
2. [`RELEASING.md`](RELEASING.md)

## Maintained documents

| Document | Purpose |
| --- | --- |
| [`PROJECT_OVERVIEW.md`](PROJECT_OVERVIEW.md) | Concept, scope, users, terminology, and project boundaries. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Current lifecycle, async model, state, recovery, and extension architecture. |
| [`MODULES.md`](MODULES.md) | Current Maven modules, public surfaces, dependencies, and selection guidance. |
| [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) | Current release line, implemented capabilities, limitations, and next work. |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Build, test, module, provider, and documentation workflows. |
| [`RELEASING.md`](RELEASING.md) | Maven Central release procedure and version rules. |
| [`AGENT_CLI_PROVIDER.md`](AGENT_CLI_PROVIDER.md) | Agent subprocess contract, security, and operations. |

## Active plans

Active plans describe approved or proposed work that is not yet the current
implementation. There are currently no active implementation plans.

When a plan is completed, update the maintained architecture and status
documents, then move the plan to `archive/` or convert it into a concise
decision record.

## Archived documents

[`archive/`](archive/) contains the initial v0 architecture, API sketches,
phase plans, and strategy notes. They explain design history but may describe
features in future tense that are already implemented, superseded, or still
deferred.

Archived documents are never the primary implementation specification.

## Authority and maintenance

The Java sources, POM files, and tests are authoritative. Maintained documents
should summarize those facts and provide navigation, rationale, and operational
guidance.

If code and a maintained document disagree:

1. verify the code with tests;
2. correct the maintained document in the same change;
3. preserve useful historical rationale in `archive/` rather than copying it
   into several active documents.

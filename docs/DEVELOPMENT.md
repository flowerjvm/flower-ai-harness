# Development guide

## Prerequisites

- JDK 21.
- Maven 3.9 or newer.
- A local checkout of this repository.
- Network access only when Maven dependencies are not already cached.

`flower-core` is resolved through the version declared in the parent POM.

## Build and test

Run the complete reactor:

```bash
mvn -B -ntp clean verify
```

Run one module and all required upstream modules:

```bash
mvn -B -ntp -pl flower-ai-harness-core -am test
```

Examples:

```bash
mvn -B -ntp -pl flower-ai-harness-test -am test
mvn -B -ntp -pl flower-ai-harness-provider-anthropic -am test
mvn -B -ntp -pl flower-ai-harness-provider-agent-cli -am test
mvn -B -ntp -pl flower-ai-harness-samples -am test
```

Install snapshots locally:

```bash
mvn -B -ntp install
```

## Repository layout

```text
AGENTS.md
README.md
pom.xml
docs/
  maintained project documentation
  plans/
  archive/
examples/
  agent-cli-runners/
config/checkstyle/
flower-ai-harness-core/
flower-ai-harness-validator-jackson/
flower-ai-harness-test/
flower-ai-harness-spring-ai/
flower-ai-harness-provider-*/
flower-ai-harness-spring-boot-starter/
flower-ai-harness-samples/
```

See [`MODULES.md`](MODULES.md) for module responsibilities.

## Change placement

Put a change in core only when all of these are true:

- the concept is provider-neutral;
- it applies to most harnesses;
- it is part of the outer lifecycle rather than a transport implementation;
- it can be tested without a vendor SDK or host-domain type;
- omitting it would force multiple adapters or applications to duplicate the
  same lifecycle behavior.

Put a change in an adapter module when it concerns:

- SDK request or response mapping;
- HTTP transport;
- provider-specific options;
- Spring integration;
- process execution;
- provider authentication or environment behavior.

Put a change in the host application when it concerns:

- domain inputs and outputs;
- prompt content;
- business validation;
- persistence and UI;
- authorization, tenant, or organization scope;
- business findings and workflow orchestration.

## Core rules

Do not:

- add Spring, Jackson, vendor SDK, or host dependencies to core;
- make lifecycle step classes public;
- block in a Flower `Step`;
- perform synchronous model, network, filesystem, process, or database waits
  from `onEnter` or `onTick`;
- put provider-specific option fields directly on core request records;
- turn snapshot recovery into a second workflow engine.

Use:

- `ProviderOptions` for adapter-specific request options;
- `AiModelCall` for asynchronous work;
- `RunOverrides` for per-run model, options, timeout, cancellation, and
  attributes;
- `FindingSink` as a quick host publication seam;
- `TraceListener` for passive observation only.

## Adding or changing a provider

Before implementation:

1. Confirm that the existing `AiModelGateway` SPI is sufficient.
2. Define the provider prefix used by `ModelId`.
3. List supported `ProviderOptions`.
4. Decide timeout and cancellation semantics.
5. Decide which metadata is safe and useful in `providerTrace`.
6. Define executor or client ownership and shutdown behavior.

Implementation requirements:

- `submit()` must return promptly;
- `poll()` must be non-blocking;
- `result()` must reject non-ready calls;
- `error()` must expose the meaningful root failure;
- `cancel()` must propagate best-effort cancellation;
- response raw text must remain available for validators;
- tests must not require live credentials or real network calls.

Documentation requirements:

- add the module to the root README and `MODULES.md`;
- update `IMPLEMENTATION_STATUS.md`;
- document options in the provider package or module README;
- add an active plan first if the change has significant contract or lifecycle
  implications.

## Adding a Maven module

1. Create the module directory and child `pom.xml`.
2. Add the module to the parent `<modules>` list.
3. Add its artifact to parent `<dependencyManagement>`.
4. Keep the parent version and shared plugin configuration.
5. Add JUnit and AssertJ test dependencies as needed.
6. Add `package-info.java` for package-level intent.
7. Add deterministic tests.
8. Decide whether it is deployed or excluded like the samples module.
9. Run `mvn -B -ntp clean verify`.
10. Update documentation.

## Test strategy

Prefer lifecycle tests that execute the real factory and Flower flow when
testing:

- retry/refine;
- model fallback;
- cancellation;
- budget or resource behavior;
- state persistence;
- finding emission.

Use unit or contract tests when testing:

- SDK/HTTP mapping;
- option parsing;
- routing;
- result and error conversion;
- configuration validation.

Provider tests should use fakes, proxies, local fixtures, or subprocess test
runners. Normal CI must not depend on:

- API keys;
- logged-in user accounts;
- vendor sandboxes;
- external service availability.

Agent CLI provider tests launch a test-only Java subprocess from the current
JDK. Keep those tests portable across Windows and Linux, and verify descendants
are terminated rather than checking only the direct parent.

## Non-blocking review checklist

For any code reachable from a Flower step, ask:

- Can this wait on a socket, process, filesystem, database, lock, or future?
- Can a callback supplied by a host perform slow work?
- Is a resource permit always released?
- Does cancellation stop or detach underlying work?
- Can timeout leave work running?
- Does a retry duplicate side effects?

Move long-running work behind an asynchronous gateway or a host-managed queue.

## Documentation workflow

Use the document according to the information type:

| Information | Location |
| --- | --- |
| Public introduction | `README.md` |
| Agent/contributor entry instructions | `AGENTS.md` |
| Current concept and boundary | `docs/PROJECT_OVERVIEW.md` |
| Current runtime design | `docs/ARCHITECTURE.md` |
| Current module inventory | `docs/MODULES.md` |
| Current completed and pending work | `docs/IMPLEMENTATION_STATUS.md` |
| Concrete future implementation | `docs/plans/` |
| Superseded design or plan | `docs/archive/` |

Do not create a new root-level strategy or architecture file.

## Release

All deployable modules release together with the parent reactor version.
Follow [`RELEASING.md`](RELEASING.md).

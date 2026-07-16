# Agent CLI provider plan

Status: implemented in `0.1.1-SNAPSHOT`; archived as the original execution
plan.

Last reviewed: 2026-07-16.

## Goal

Add a vendor-neutral Maven module:

```text
flower-ai-harness-provider-agent-cli
```

The module will execute an external CLI/subprocess agent runner and expose one
run through the existing `AiModelGateway` and `AiModelCall` contracts.

Target runners include wrappers around:

- Claude Agent SDK or Claude Code login state;
- Codex SDK or `codex login` state;
- any other runner that follows the documented file/process contract.

The module name and public API must not contain a specific agent vendor.

## Why this belongs in the harness

An agent SDK owns the inner loop:

- tool execution;
- turn management;
- context management;
- model interaction;
- agent-specific transcript.

Flower AI Harness owns the outer business lifecycle:

- prompt/input preparation;
- final output validation;
- retry/refine/fallback policy;
- execution budget;
- timeout and cancellation;
- run state and observation;
- deterministic testing.

Without a shared adapter, every host application would duplicate process
lifecycle, validation handoff, timeout, and cancellation behavior.

## Go/No-Go decision

Decision: **Go with the existing core SPI.**

No new core gateway abstraction is currently required.

Evidence:

- `AiModelGateway.submit(AiModelRequest)` returns an asynchronous handle;
- `AiModelCall` already supports `poll`, `result`, `error`, and `cancel`;
- `AwaitResponseStep` already performs non-blocking polling;
- cancellation is already propagated to `AiModelCall.cancel()`;
- call failures and validation failures already feed `RefineDecisionStep`.

An agent execution still has the same outer shape:

```text
request -> asynchronous execution -> final output or failure
```

Revisit the SPI only if implementation proves that progress, call
reattachment, or a different terminal-result type cannot be represented
without distorting `AiModelCall`.

## Scope

The module owns:

- runner request/result/progress contract;
- per-call workspace creation;
- command argument template expansion;
- asynchronous process lifecycle;
- stdout and stderr draining;
- progress event parsing;
- timeout enforcement;
- process-tree cancellation;
- environment-variable policy;
- output envelope parsing;
- mapping into `AiModelResponse` and provider exceptions;
- active-process shutdown and optional orphan cleanup.

The module does not own:

- Claude or Codex runner implementation;
- an agent tool loop;
- business output schema;
- prompt content;
- domain persistence;
- user-account sharing or service authorization policy;
- validation or refine policy.

## Proposed module dependencies

```text
flower-ai-harness-provider-agent-cli
  -> flower-ai-harness-core
  -> jackson-databind
```

Jackson is acceptable in the provider module and must not be added to core.

## Proposed public surface

Keep the first public surface small:

```text
AgentCliModelGateway
AgentCliGatewayConfig
AgentCliEnvironmentPolicy
AgentCliOptions
AgentCliProgressEvent
AgentCliProgressListener
AgentCliExecutionException
AgentCliTimeoutException
AgentCliProtocolException
```

Likely internal types:

```text
AgentCliModelCall
AgentCliProcessRunner
AgentCliWorkspace
AgentCliRequestWriter
AgentCliResultReader
AgentCliProgressParser
ProcessTreeTerminator
OrphanProcessCleaner
```

`AgentCliModelGateway` may implement `AutoCloseable` so an application can
terminate active calls and owned executors during shutdown.

## Configuration shape

Illustrative API:

```java
AgentCliGatewayConfig config = AgentCliGatewayConfig.builder()
    .command(List.of(
        "node",
        "runner/dist/main.js",
        "--request", "{requestFile}",
        "--result", "{resultFile}",
        "--model", "{model}"
    ))
    .processWorkingDirectory(projectDirectory)
    .runWorkspaceRoot(agentRunDirectory)
    .requestFileName("run-request.json")
    .resultFileName("run-result.json")
    .maxExecutionTime(Duration.ofMinutes(15))
    .killGracePeriod(Duration.ofSeconds(5))
    .environmentPolicy(AgentCliEnvironmentPolicy.subscriptionSafeDefaults())
    .progressListener(event -> audit(event))
    .cleanupOrphansOnStart(true)
    .build();

try (AgentCliModelGateway gateway = new AgentCliModelGateway(config)) {
    // Wire gateway into RoutingAiModelGateway or AiHarnessFlowFactory.
}
```

`AiModelRequest.timeout()` remains the per-request timeout. Configuration
provides a hard maximum:

```text
effective timeout = min(request timeout, configured maximum)
```

Do not define two unrelated timeout values with ambiguous precedence.

## Model routing

Recommended model IDs:

```text
agent-cli:claude
agent-cli:codex
agent-cli:custom-runner
```

`ModelId.provider()` selects the gateway through `RoutingAiModelGateway`.
`ModelId.name()` becomes the runner model/backend identifier and is available
through the `{model}` command placeholder and request file.

If a host needs more runner-specific values, use documented
`AgentCliOptions` keys rather than adding fields to core.

## Command execution rules

- Configuration accepts `List<String>`, never a shell command string.
- Each configured list element becomes one `ProcessBuilder` argument.
- Placeholder expansion occurs within one argument and never invokes a shell.
- Supported placeholders should be explicit and minimal:
  - `{requestFile}`;
  - `{resultFile}`;
  - `{runDirectory}`;
  - `{workingDirectory}`;
  - `{callId}`;
  - `{model}`.
- Unknown placeholders fail configuration or submission.
- Request-derived values must not be concatenated into a shell command.
- stdin is closed after process start so an unexpected interactive prompt
  cannot wait forever.

## Per-call workspace

A fixed shared `run-request.json` or `findings.json` is unsafe under
concurrency. Every call receives an isolated directory:

```text
runWorkspaceRoot/
  agent-cli-call-<id>/
    run-request.json
    run-result.json
    stdout.log
    stderr.log
    process.json
```

`processWorkingDirectory` and `runWorkspaceRoot` are different concepts:

- process working directory: the project or repository the agent operates on;
- run workspace: harness protocol files and diagnostics.

The module must:

- normalize workspace paths;
- reject request/result filenames that escape the call directory;
- create files with restrictive permissions where the platform permits;
- define cleanup/retention behavior;
- preserve failed-run diagnostics long enough for investigation;
- avoid deleting arbitrary user directories.

## Runner request contract v1

The request file is UTF-8 JSON:

```json
{
  "contractVersion": "1",
  "callId": "agent-cli-call-123",
  "model": {
    "provider": "agent-cli",
    "name": "claude"
  },
  "timeoutMillis": 900000,
  "prompt": {
    "version": {
      "id": "site-review",
      "version": "1.2.0"
    },
    "messages": [
      {
        "role": "SYSTEM",
        "content": "Return JSON only."
      },
      {
        "role": "USER",
        "content": "Review the workspace."
      }
    ]
  },
  "options": {
    "runnerMode": "read-only"
  },
  "paths": {
    "runDirectory": "...",
    "resultFile": "..."
  }
}
```

Rules:

- `contractVersion` is mandatory.
- Core prompt roles are preserved.
- Only documented agent CLI options are serialized.
- Option values must be JSON-safe; unsupported object values fail before
  process start.
- Secrets should not be written to the request file.
- Absolute paths are provided only where the runner needs them.

## Runner result contract v1

The result file is authoritative. Business-specific names such as
`findings.json` should not be built into the provider contract.

Success:

```json
{
  "contractVersion": "1",
  "status": "succeeded",
  "output": {
    "mediaType": "application/json",
    "text": "{\"findings\":[]}"
  },
  "metadata": {
    "backend": "claude",
    "sessionId": "session-123",
    "transcriptPath": "transcript.jsonl"
  }
}
```

Runner-reported failure:

```json
{
  "contractVersion": "1",
  "status": "failed",
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "Agent login is unavailable.",
    "retryable": false
  },
  "metadata": {
    "backend": "claude"
  }
}
```

Mapping:

- `output.text` becomes `AiModelResponse.rawText`;
- model ID remains the requested `ModelId`;
- latency and selected metadata become response metadata;
- small safe strings can enter `providerTrace`;
- large transcripts remain files and only their paths are referenced.

Terminal precedence:

1. harness cancellation produces `CANCELLED`;
2. timeout produces `FAILED` with `AgentCliTimeoutException`;
3. non-zero process exit produces `FAILED`;
4. zero exit with missing or malformed result produces
   `AgentCliProtocolException`;
5. valid `status: failed` produces structured execution failure;
6. valid `status: succeeded` produces `READY`.

## Progress contract

stdout is drained continuously and stored in `stdout.log`.

Runner progress lines should be UTF-8 JSON Lines:

```json
{"type":"started","timestamp":"2026-07-16T12:00:00Z"}
{"type":"message","level":"info","message":"Scanning repository"}
{"type":"tool","name":"read_file","state":"started"}
{"type":"tool","name":"read_file","state":"completed"}
{"type":"artifact","path":"transcript.jsonl"}
```

Progress is observational:

- the result file, not stdout, determines success output;
- malformed progress lines are retained as diagnostics and do not by
  themselves invalidate an otherwise valid result;
- listener exceptions are ignored;
- slow listeners must not block stdout draining;
- the call may expose the latest bounded set of progress messages internally.

Listener dispatch should use a separate executor or bounded asynchronous handoff
so a host callback cannot deadlock the child process by filling stdout buffers.

## stderr and diagnostics

stderr is always drained concurrently and stored in `stderr.log`.

Failures should include:

- exit code;
- timeout or cancellation classification;
- bounded stderr tail;
- result protocol error, if any;
- run directory;
- safe command identity without secrets;
- process start/end timestamps.

Do not retain unbounded stdout/stderr in memory.

## Environment policy

The default goal is to prefer subscription login state and avoid accidental API
key billing.

The default policy should:

- clear the inherited process environment;
- pass through a documented minimal OS/runtime allowlist such as `PATH`,
  `HOME`, `USERPROFILE`, `APPDATA`, `LOCALAPPDATA`, `TEMP`, `TMP`, and
  `SystemRoot` where present;
- explicitly deny `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, and other configured
  sensitive keys;
- allow explicit additional variables;
- record which variable names, not values, were passed;
- never log secret values.

This is a best-effort safety boundary, not proof of which billing path a vendor
runner selected. The README must state that operators are responsible for
runner authentication and terms.

## Asynchronous process lifecycle

Process work must not run on a Flower worker tick.

Recommended Java 21 implementation:

- use virtual-thread-backed execution for process wait and stream draining;
- return `AgentCliModelCall` immediately after process start;
- maintain an atomic call state;
- have `poll()` read state only;
- parse the result after process termination;
- transition exactly once to ready, failed, or cancelled.

The gateway should track active calls for shutdown and diagnostics.

## Timeout and cancellation

On timeout or `cancel()`:

1. atomically mark termination requested;
2. close process stdin;
3. request graceful termination of descendants and parent;
4. wait only for the configured grace period outside Flower ticks;
5. forcibly terminate remaining descendants and parent;
6. complete the call as timeout or cancelled;
7. retain bounded diagnostics.

Process-tree strategy:

- use `ProcessHandle.descendants()` as the portable first path;
- on Windows, use a safe numeric-PID `taskkill /PID <pid> /T /F` fallback when
  needed;
- on Unix, terminate descendants and parent and require runners not to
  daemonize or detach from the process tree;
- test child-process termination on both Windows and Linux.

Do not build shell command strings for termination.

## Shutdown and orphan handling

Normal shutdown:

- `AgentCliModelGateway.close()` cancels active processes;
- owned executors are closed;
- externally supplied executors are not closed.

Crash recovery:

- write `process.json` containing PID, call ID, command identity, and process
  start instant;
- optional startup cleanup may inspect unfinished workspaces;
- kill an orphan only when PID and start instant still match, reducing PID
  reuse risk;
- never kill a process based only on a stale PID;
- document that generic harness snapshot recovery still resubmits rather than
  reattaches to the old `AiModelCall`.

## Retry and side-effect safety

Agent runs may modify files or external systems. A validation failure followed
by automatic retry can repeat those side effects.

Recommended v1 posture:

- optimize for read-only analysis;
- support an explicit read-only runner mode;
- use an isolated worktree or sandbox for modification tasks;
- validate output before applying changes to the primary workspace;
- do not automatically resume a prior agent session;
- let the host refine policy decide whether another execution is safe.

This limitation must be prominent in module documentation.

## Testing strategy

Use a test-only Java runner launched with the current JDK. It is more portable
than assuming Node or Python is installed in CI.

Required scenarios:

| Scenario | Expected result |
| --- | --- |
| Immediate valid success | `READY`, raw output mapped correctly |
| Delayed success | repeated non-blocking `PENDING`, then `READY` |
| Progress events | listener receives ordered bounded events |
| Malformed progress line | diagnostics retained, valid result still succeeds |
| Missing result file | protocol failure |
| Malformed result JSON | protocol failure |
| Runner-reported failure | structured execution failure |
| Non-zero exit | execution failure with exit code and stderr tail |
| Hang | timeout and process-tree termination |
| User cancellation | `CANCELLED` and process-tree termination |
| Child process | parent and child are both terminated |
| Concurrent calls | isolated request/result files |
| Validation failure then retry | two separate call workspaces, second succeeds |
| Environment filtering | denied API key variables absent |
| Gateway close | all active calls terminated |
| Orphan cleanup | only matching PID/start-instant process considered |

Test layers:

1. provider unit tests for configuration, files, process state, and parsing;
2. full harness tests for validation/refine/cancel behavior;
3. Windows and Ubuntu CI matrix for process handling;
4. opt-in manual contract proof with one real subscription-authenticated
   runner.

Normal CI must not require a logged-in Claude or Codex account.

## Implementation phases

### Phase 1: contract and skeleton

- add this module to the parent reactor and dependency management;
- add package documentation;
- write the finalized runner contract under `docs/`;
- define config, options, progress, and exception types;
- test configuration and path validation.

### Phase 2: process execution

- create per-call workspace;
- write request JSON;
- start the process without a shell;
- drain stdout and stderr;
- wait asynchronously;
- parse terminal result;
- implement call state and metadata.

### Phase 3: control and safety

- implement effective timeout;
- implement cancellation and process-tree kill;
- add environment policy;
- implement gateway shutdown;
- add bounded logs and diagnostics;
- add optional orphan cleanup.

### Phase 4: harness integration tests

- run full Flower lifecycle through the provider;
- cover validation failure and refine retry;
- cover cancellation and timeout from the harness;
- cover concurrency and resource-governor interaction.

### Phase 5: cross-platform and real-runner proof

- add Windows CI;
- verify Linux and Windows child-process termination;
- implement one example runner outside the deployable Java artifact;
- run one subscription-authenticated Claude Code or Codex execution;
- record setup and limitations without checking in credentials.

### Phase 6: documentation and release

- update root README;
- update `MODULES.md` and `IMPLEMENTATION_STATUS.md`;
- add module usage and security documentation;
- run full reactor verification;
- release with the shared reactor version.

## Acceptance criteria

The work is complete when:

1. success, failure, timeout, cancellation, validation-refine retry, and
   concurrency tests pass;
2. child process trees are terminated on Windows and Linux;
3. a third-party runner can be implemented from the contract document alone;
4. one real subscription-authenticated agent run succeeds;
5. no API keys or credentials are checked into the repository or logged;
6. core remains unchanged unless implementation evidence justifies a separate
   reviewed architecture change;
7. the module is documented and released with the reactor.

## Open decisions to resolve before coding

- exact environment allowlist per operating system;
- default workspace retention and cleanup age;
- whether event listener execution is internally owned or supplied by the
  host;
- whether orphan cleanup is enabled by default;
- exact supported `AgentCliOptions`;
- whether the example runner lives in this repository or a separate examples
  repository;
- which real runner is used for the first contract proof.

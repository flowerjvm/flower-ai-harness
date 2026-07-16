# Agent CLI provider

## Purpose

`flower-ai-harness-provider-agent-cli` adapts a long-running external agent
process to the existing `AiModelGateway` and `AiModelCall` SPI.

The Java module owns the outer execution lifecycle:

```text
Flower harness
  prompt, timeout, polling, validation, retry/refine, cancellation
      |
      v
AgentCliModelGateway
  per-call workspace, subprocess, logs, process tree, result mapping
      |
      v
runner
  Codex SDK, Claude Agent SDK, installed CLI, or custom agent loop
```

The runner owns its internal turns, tools, transcript, and vendor
authentication. No core SPI change is required.

## Public API

- `AgentCliModelGateway`;
- `AgentCliGatewayConfig`;
- `AgentCliEnvironmentPolicy`;
- `AgentCliOptions`;
- `AgentCliProgressEvent`;
- `AgentCliProgressListener`;
- `AgentCliExecutionException`;
- `AgentCliTimeoutException`;
- `AgentCliProtocolException`.

## Per-call workspace

Each submission creates:

```text
runWorkspaceRoot/
  agent-cli-call-<uuid>/
    run-request.json
    run-result.json
    stdout.log
    stderr.log
    process.json
```

`processWorkingDirectory` is the repository or project the agent operates on.
`runWorkspaceRoot` stores protocol files and diagnostics. They are deliberately
separate.

Workspaces are retained. The host decides archival and deletion policy.

## Request contract v1

The provider writes UTF-8 JSON:

```json
{
  "contractVersion": "1",
  "callId": "agent-cli-call-...",
  "model": {
    "provider": "agent-cli",
    "name": "codex"
  },
  "timeoutMillis": 900000,
  "prompt": {
    "version": {
      "id": "maintenance-review",
      "version": "1.0.0"
    },
    "messages": [
      {
        "role": "SYSTEM",
        "content": "Return JSON only."
      },
      {
        "role": "USER",
        "content": "Inspect the workspace."
      }
    ]
  },
  "options": {
    "agentCli.runnerMode": "read-only"
  },
  "paths": {
    "workingDirectory": "/workspace/project",
    "runDirectory": "/workspace/runs/agent-cli-call-...",
    "resultFile": "/workspace/runs/agent-cli-call-.../run-result.json"
  }
}
```

All `ProviderOptions` values must be JSON-safe scalars, maps, iterables, or
arrays. Unsupported Java objects fail the call before process start. Do not put
credentials in `ProviderOptions`; request files are retained.

Conventional option keys:

| Key | Meaning |
| --- | --- |
| `agentCli.runnerMode` | Runner-defined safety mode, normally `read-only` first. |
| `agentCli.sdkModel` | Optional vendor SDK model override. |
| `agentCli.sandbox` | Runner-defined sandbox selection. |
| `agentCli.permissionMode` | Runner-defined permission mode. |
| `agentCli.maxTurns` | Optional agent turn limit. |
| `agentCli.allowedTools` | Optional runner tool allowlist. |

## Result contract v1

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
    "backend": "codex",
    "sessionId": "session-123",
    "inputTokens": 100,
    "outputTokens": 30,
    "finishReason": "completed",
    "transcriptPath": "transcript.jsonl"
  }
}
```

Failure:

```json
{
  "contractVersion": "1",
  "status": "failed",
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "The runner is not logged in.",
    "retryable": false
  },
  "metadata": {
    "backend": "claude"
  }
}
```

`output.text` maps to `AiModelResponse.rawText`. Small whitelisted metadata
maps to `providerTrace`; large transcripts stay in files.

Terminal precedence:

1. harness cancellation -> `CANCELLED`;
2. timeout -> `AgentCliTimeoutException`;
3. non-zero exit -> `AgentCliExecutionException`;
4. missing/malformed result -> `AgentCliProtocolException`;
5. result `status: failed` -> structured `AgentCliExecutionException`;
6. result `status: succeeded` -> `READY`.

The runner should write a temporary result and atomically rename it into place.

## Progress and diagnostics

stdout can contain JSON Lines:

```json
{"type":"started","timestamp":"2026-07-16T12:00:00Z"}
{"type":"tool","name":"read_file","state":"completed"}
```

Valid object lines with a non-blank `type` become
`AgentCliProgressEvent`. Listener dispatch uses a bounded queue; slow or
failing listeners cannot block process output draining. Malformed lines remain
in `stdout.log` and do not invalidate a valid result.

stderr is written to `stderr.log`; failures expose only a configured bounded
tail in memory.

`process.json` records PID, start time, executable identity, and environment
variable names. It never records environment values or the full argument list.
Request, result, stdout, and stderr files can still contain sensitive business
content, so protect and expire the workspace as operational data.

## Environment and authentication

`AgentCliEnvironmentPolicy.subscriptionSafeDefaults()`:

- clears the inherited environment;
- passes a minimal OS, home, executable-path, and config-directory allowlist;
- passes `CODEX_HOME`, `CLAUDE_CONFIG_DIR`, and non-secret Anthropic profile
  selectors when present;
- denies common API-key variables such as `OPENAI_API_KEY` and
  `ANTHROPIC_API_KEY`, plus bearer/OAuth token variables;
- allows hosts to add variable names or explicit values deliberately.

This reduces accidental API-key selection but cannot prove which account or
billing route vendor software uses. Operators must verify runner status,
vendor terms, account ownership, and unattended-automation allowances.

Subscription credentials must remain local to the operator. Do not route a
user's login through a shared service unless the vendor explicitly permits
that deployment model.

## Timeout, cancellation, and shutdown

The provider waits off the Flower worker on virtual threads. On timeout,
`AiModelCall.cancel()`, or gateway close, it:

1. snapshots descendants;
2. requests graceful descendant and parent termination;
3. waits up to `killGracePeriod`;
4. forcibly terminates remaining processes;
5. uses numeric-PID `taskkill /T /F` as a Windows fallback.

Runners must not detach or daemonize children outside the process tree.

## Retry and side effects

Harness refine/retry creates a new process and a new workspace. It does not
resume the prior agent session. This is at-least-once behavior and can repeat
file or external-system side effects.

Recommended maintenance-automation flow:

```text
isolated worktree/sandbox
  -> agent analysis or proposed changes
  -> harness validates structured result
  -> flower-action-runtime receives an ActionProposal
  -> policy/approval/idempotency/audit
  -> governed execution
```

Start read-only. For mutations, use isolated worktrees and keep actual
application of changes behind `flower-action-runtime` governance rather than
granting the runner unrestricted production authority.

## Routing Codex and Claude

Either use one dispatcher runner:

```text
agent-cli:codex
agent-cli:claude
```

or configure two gateway instances under separate provider prefixes:

```text
codex-agent:default
claude-agent:default
```

The Java provider remains identical. See
[`../examples/agent-cli-runners/`](../examples/agent-cli-runners/) for SDK
wrapper examples.

## Tests

Normal CI uses a Java subprocess fixture and requires no account or network.
Coverage includes success, delayed polling, progress, protocol errors,
runner-reported failure, non-zero exit, timeout, cancellation, child-process
termination, environment filtering, concurrent workspace isolation, gateway
shutdown, and full harness validation/refine retry.

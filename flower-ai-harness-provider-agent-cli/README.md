# flower-ai-harness-provider-agent-cli

Vendor-neutral `AiModelGateway` adapter for external agent processes.

The module does not depend on Codex or Claude libraries. A separately deployed
runner can wrap Codex SDK, Claude Agent SDK, an installed CLI, or another agent
implementation as long as it follows the v1 request/result contract.

## Dependency

```xml
<dependency>
  <groupId>io.github.flowerjvm</groupId>
  <artifactId>flower-ai-harness-provider-agent-cli</artifactId>
  <version>${flower-ai-harness.version}</version>
</dependency>
```

## Basic configuration

```java
AgentCliGatewayConfig config = AgentCliGatewayConfig.builder()
    .command(List.of(
        "node",
        "/opt/flower-runners/agent-runner.mjs",
        "--request", "{requestFile}",
        "--result", "{resultFile}",
        "--backend", "{model}"))
    .processWorkingDirectory(repository)
    .runWorkspaceRoot(repository.resolve(".flower-ai-harness/agent-runs"))
    .maxExecutionTime(Duration.ofMinutes(15))
    .killGracePeriod(Duration.ofSeconds(5))
    .environmentPolicy(AgentCliEnvironmentPolicy.subscriptionSafeDefaults())
    .progressListener(event -> audit(event))
    .build();

try (AgentCliModelGateway gateway = new AgentCliModelGateway(config)) {
    // ModelId.parse("agent-cli:codex") or "agent-cli:claude"
}
```

Supported command placeholders:

- `{requestFile}`;
- `{resultFile}`;
- `{runDirectory}`;
- `{workingDirectory}`;
- `{callId}`;
- `{model}`.

Commands are argument lists and never shell strings.

## Runtime behavior

- `submit()` schedules filesystem/process work on Java 21 virtual threads.
- Every call gets an isolated workspace.
- stdout and stderr are drained continuously to bounded diagnostics.
- stdout JSON Lines are delivered asynchronously as progress events.
- the result file is authoritative for final output.
- effective timeout is the smaller of request timeout and configured maximum.
- timeout, cancellation, and gateway shutdown terminate the process tree.
- failed call workspaces are retained for diagnostics.

See [`../docs/AGENT_CLI_PROVIDER.md`](../docs/AGENT_CLI_PROVIDER.md) for the
complete contract, security model, and operational limitations.

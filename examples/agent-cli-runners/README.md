# Agent CLI runner examples

These Node.js examples implement the
[`flower-ai-harness-provider-agent-cli`](../../flower-ai-harness-provider-agent-cli/README.md)
v1 file/process contract.

They are reference runners, not part of the Maven artifact:

- `codex-sdk-runner.mjs` wraps `@openai/codex-sdk`;
- `claude-agent-sdk-runner.mjs` wraps
  `@anthropic-ai/claude-agent-sdk`.

Install and syntax-check:

```bash
npm install
npm run check
```

Configure the Java gateway with one runner:

```java
AgentCliGatewayConfig config = AgentCliGatewayConfig.builder()
    .command(List.of(
        "node",
        "examples/agent-cli-runners/codex-sdk-runner.mjs",
        "--request", "{requestFile}",
        "--result", "{resultFile}"))
    .processWorkingDirectory(repository)
    .runWorkspaceRoot(runDirectory)
    .build();
```

Use the Claude runner path for Claude Agent SDK. A host can also create two
`AgentCliModelGateway` instances and register them under separate
`RoutingAiModelGateway` provider prefixes.

The default Java environment policy removes `OPENAI_API_KEY` and
`ANTHROPIC_API_KEY` while preserving the user-home/config locations normally
used by installed tools. Authentication behavior is ultimately selected by
the runner and vendor software. Confirm the active account and billing route
before unattended execution.

For modification tasks, do not use the examples unchanged in a primary
working tree. Start with read-only/plan mode, then add an isolated worktree,
sandbox, explicit tool permissions, and a host-side approval boundary.

Official references:

- [Codex TypeScript SDK source](https://github.com/openai/codex/tree/main/sdk/typescript);
- [Claude Agent SDK TypeScript reference](https://platform.claude.com/docs/en/agent-sdk/typescript);
- [Claude Code authentication](https://code.claude.com/docs/en/authentication);
- [Claude authentication and credential-use terms](https://code.claude.com/docs/en/legal-and-compliance).

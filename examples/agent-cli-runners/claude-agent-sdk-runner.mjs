import { query } from "@anthropic-ai/claude-agent-sdk";

import {
  emitProgress,
  option,
  parseRunnerArguments,
  readRequest,
  renderPrompt,
  writeFailure,
  writeSuccess
} from "./lib/contract.mjs";

const { requestFile, resultFile } = parseRunnerArguments(process.argv.slice(2));

try {
  const request = await readRequest(requestFile);
  const runnerMode = option(request, "agentCli.runnerMode", "read-only");
  const permissionMode = runnerMode === "read-only"
    ? "plan"
    : option(request, "agentCli.permissionMode", "default");
  const options = {
    cwd: request.paths.workingDirectory,
    permissionMode,
    settingSources: ["user", "project", "local"]
  };

  const model = option(request, "agentCli.sdkModel");
  const maxTurns = option(request, "agentCli.maxTurns");
  const allowedTools = option(request, "agentCli.allowedTools");
  if (typeof model === "string" && model.length > 0) {
    options.model = model;
  }
  if (Number.isInteger(maxTurns) && maxTurns > 0) {
    options.maxTurns = maxTurns;
  }
  if (Array.isArray(allowedTools)) {
    options.allowedTools = allowedTools;
  }

  emitProgress("started", { backend: "claude", permissionMode });
  let finalResult;
  for await (const message of query({
    prompt: renderPrompt(request),
    options
  })) {
    emitProgress("message", {
      backend: "claude",
      messageType: message.type,
      subtype: message.subtype
    });
    if (message.type === "result") {
      finalResult = message;
    }
  }

  if (!finalResult) {
    throw new Error("Claude Agent SDK completed without a result message");
  }
  if (finalResult.subtype !== "success") {
    await writeFailure(
      resultFile,
      `CLAUDE_${finalResult.subtype.toUpperCase()}`,
      `Claude Agent SDK ended with ${finalResult.subtype}`,
      finalResult.subtype === "error_during_execution",
      {
        backend: "claude",
        sessionId: finalResult.session_id
      }
    );
  } else {
    await writeSuccess(resultFile, finalResult.result, {
      backend: "claude",
      sessionId: finalResult.session_id,
      finishReason: finalResult.stop_reason ?? "completed",
      inputTokens: finalResult.usage?.input_tokens,
      outputTokens: finalResult.usage?.output_tokens
    });
  }
  emitProgress("completed", { backend: "claude" });
} catch (error) {
  await writeFailure(resultFile, "CLAUDE_RUNNER_ERROR", error, true, {
    backend: "claude"
  });
}

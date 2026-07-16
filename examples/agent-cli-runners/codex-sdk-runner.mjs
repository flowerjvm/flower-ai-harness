import { Codex } from "@openai/codex-sdk";

import {
  emitProgress,
  parseRunnerArguments,
  readRequest,
  renderPrompt,
  writeFailure,
  writeSuccess
} from "./lib/contract.mjs";

const { requestFile, resultFile } = parseRunnerArguments(process.argv.slice(2));

try {
  const request = await readRequest(requestFile);
  emitProgress("started", { backend: "codex" });

  const codex = new Codex();
  const thread = codex.startThread();
  const result = await thread.run(renderPrompt(request));

  await writeSuccess(resultFile, result.finalResponse, {
    backend: "codex",
    finishReason: "completed"
  });
  emitProgress("completed", { backend: "codex" });
} catch (error) {
  await writeFailure(resultFile, "CODEX_RUNNER_ERROR", error, true, {
    backend: "codex"
  });
}

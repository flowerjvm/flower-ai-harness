import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

export function parseRunnerArguments(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const name = argv[index];
    const value = argv[index + 1];
    if (!name?.startsWith("--") || value === undefined) {
      throw new Error(`Invalid runner argument near ${name ?? "<end>"}`);
    }
    values.set(name, value);
  }
  const requestFile = values.get("--request");
  const resultFile = values.get("--result");
  if (!requestFile || !resultFile) {
    throw new Error("--request and --result are required");
  }
  return { requestFile, resultFile };
}

export async function readRequest(requestFile) {
  const request = JSON.parse(await readFile(requestFile, "utf8"));
  if (request.contractVersion !== "1") {
    throw new Error(`Unsupported contract version: ${request.contractVersion}`);
  }
  return request;
}

export function renderPrompt(request) {
  return request.prompt.messages
    .map((message) => `[${message.role}]\n${message.content}`)
    .join("\n\n");
}

export function option(request, name, fallback = undefined) {
  return request.options?.[name] ?? fallback;
}

export function emitProgress(type, data = {}) {
  process.stdout.write(`${JSON.stringify({
    type,
    timestamp: new Date().toISOString(),
    ...data
  })}\n`);
}

export async function writeSuccess(resultFile, text, metadata = {}) {
  await writeEnvelope(resultFile, {
    contractVersion: "1",
    status: "succeeded",
    output: {
      mediaType: "text/plain",
      text: text ?? ""
    },
    metadata
  });
}

export async function writeFailure(
  resultFile,
  code,
  error,
  retryable = false,
  metadata = {}
) {
  await writeEnvelope(resultFile, {
    contractVersion: "1",
    status: "failed",
    error: {
      code,
      message: error instanceof Error ? error.message : String(error),
      retryable
    },
    metadata
  });
}

async function writeEnvelope(resultFile, envelope) {
  await mkdir(dirname(resultFile), { recursive: true });
  const temporary = `${resultFile}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(envelope, null, 2)}\n`, {
    encoding: "utf8",
    mode: 0o600
  });
  await rename(temporary, resultFile);
}

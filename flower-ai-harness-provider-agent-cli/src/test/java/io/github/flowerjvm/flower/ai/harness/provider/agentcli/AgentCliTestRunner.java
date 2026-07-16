package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentCliTestRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentCliTestRunner() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> arguments = arguments(args);
        if (arguments.containsKey("--child")) {
            sleepForever();
            return;
        }
        Path requestFile = Path.of(arguments.get("--request"));
        Path resultFile = Path.of(arguments.get("--result"));
        String scenario = arguments.get("--scenario");
        JsonNode request = OBJECT_MAPPER.readTree(requestFile.toFile());
        switch (scenario) {
            case "success", "concurrent" -> writeSuccess(request, resultFile);
            case "empty" -> writeTextResult(resultFile, "");
            case "delay" -> {
                Thread.sleep(250L);
                writeSuccess(request, resultFile);
            }
            case "progress" -> {
                System.out.println("""
                        {"type":"started","timestamp":"%s"}
                        """.formatted(Instant.now()).trim());
                System.out.println("this is retained but ignored as progress");
                System.out.println("""
                        {"type":"tool","name":"read_file","state":"completed"}
                        """.trim());
                writeSuccess(request, resultFile);
            }
            case "retry" -> {
                boolean refined = false;
                for (JsonNode message : request.path("prompt").path("messages")) {
                    if (message.path("content").asText().contains("Validation errors")) {
                        refined = true;
                    }
                }
                if (refined) {
                    writeSuccess(request, resultFile);
                } else {
                    writeTextResult(resultFile, "not-valid");
                }
            }
            case "runner-failure" -> writeFailure(resultFile);
            case "exit-error" -> {
                System.err.println("synthetic runner failure");
                System.exit(7);
            }
            case "missing-result" -> {
            }
            case "malformed-result" ->
                    Files.writeString(resultFile, "{not-json", StandardCharsets.UTF_8);
            case "hang" -> sleepForever();
            case "child-hang" -> childHang(request);
            case "environment" -> writeEnvironment(resultFile);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    private static void writeSuccess(JsonNode request, Path resultFile) throws Exception {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("contractVersion", "1");
        root.put("status", "succeeded");
        ObjectNode output = root.putObject("output");
        output.put("mediaType", "application/json");
        output.put("text", """
                {"ok":true}
                """.trim());
        ObjectNode metadata = root.putObject("metadata");
        metadata.put("backend", request.path("model").path("name").asText());
        metadata.put("sessionId", request.path("callId").asText());
        metadata.put("inputTokens", 11);
        metadata.put("outputTokens", 7);
        OBJECT_MAPPER.writeValue(resultFile.toFile(), root);
    }

    private static void writeFailure(Path resultFile) throws Exception {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("contractVersion", "1");
        root.put("status", "failed");
        ObjectNode error = root.putObject("error");
        error.put("code", "AUTH_REQUIRED");
        error.put("message", "Synthetic login is unavailable");
        error.put("retryable", false);
        OBJECT_MAPPER.writeValue(resultFile.toFile(), root);
    }

    private static void writeTextResult(Path resultFile, String text) throws Exception {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("contractVersion", "1");
        root.put("status", "succeeded");
        ObjectNode output = root.putObject("output");
        output.put("mediaType", "text/plain");
        output.put("text", text);
        OBJECT_MAPPER.writeValue(resultFile.toFile(), root);
    }

    private static void writeEnvironment(Path resultFile) throws Exception {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("contractVersion", "1");
        root.put("status", "succeeded");
        ObjectNode output = root.putObject("output");
        output.put("mediaType", "application/json");
        output.put("text", OBJECT_MAPPER.writeValueAsString(Map.of(
                "allowed", System.getenv("AGENT_CLI_TEST_ALLOWED"),
                "deniedPresent", System.getenv().containsKey("AGENT_CLI_TEST_DENIED"))));
        OBJECT_MAPPER.writeValue(resultFile.toFile(), root);
    }

    private static void childHang(JsonNode request) throws Exception {
        Path javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java");
        Process child = new ProcessBuilder(
                javaExecutable.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                AgentCliTestRunner.class.getName(),
                "--child",
                "true")
                .start();
        child.getOutputStream().close();
        Path runDirectory = Path.of(request.path("paths").path("runDirectory").asText());
        Files.writeString(
                runDirectory.resolve("child.pid"),
                Long.toString(child.pid()),
                StandardCharsets.UTF_8);
        sleepForever();
    }

    private static void sleepForever() throws InterruptedException {
        while (true) {
            Thread.sleep(1000L);
        }
    }

    private static Map<String, String> arguments(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            parsed.put(args[index], args[index + 1]);
        }
        return parsed;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

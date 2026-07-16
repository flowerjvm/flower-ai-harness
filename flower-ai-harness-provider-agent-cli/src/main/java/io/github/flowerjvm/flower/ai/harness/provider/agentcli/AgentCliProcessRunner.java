package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AgentCliProcessRunner {

    private static final String CONTRACT_VERSION = "1";
    private static final Pattern COMMAND_PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> TRACE_METADATA_KEYS = Set.of(
            "backend",
            "provider",
            "model",
            "sessionId",
            "transcriptPath");

    private final AgentCliGatewayConfig config;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Consumer<AgentCliProgressEvent> progressConsumer;

    AgentCliProcessRunner(
            AgentCliGatewayConfig config,
            ObjectMapper objectMapper,
            ExecutorService executor,
            Consumer<AgentCliProgressEvent> progressConsumer
    ) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.progressConsumer = progressConsumer;
    }

    void execute(AiModelRequest request, AgentCliModelCall call) {
        long startedAtNanos = System.nanoTime();
        AgentCliWorkspace workspace;
        try {
            workspace = AgentCliWorkspace.create(config, call.callId());
            if (!call.isPending()) {
                return;
            }
            Duration timeout = effectiveTimeout(request.timeout(), config.maxExecutionTime());
            writeRequest(workspace, request, call.callId(), timeout);
            if (!call.isPending()) {
                return;
            }
            runProcess(workspace, request, call, timeout, startedAtNanos);
        } catch (Exception ex) {
            if (call.isPending()) {
                call.completeFailed(ex instanceof GatewayException
                        ? ex
                        : new GatewayException("Failed to prepare Agent CLI call " + call.callId(), ex));
            }
        }
    }

    private void runProcess(
            AgentCliWorkspace workspace,
            AiModelRequest request,
            AgentCliModelCall call,
            Duration timeout,
            long startedAtNanos
    ) throws IOException, InterruptedException {
        List<String> command = expandCommand(workspace, request, call.callId());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(config.processWorkingDirectory().toFile());
        Map<String, String> environment = config.environmentPolicy().resolve(System.getenv());
        processBuilder.environment().clear();
        processBuilder.environment().putAll(environment);
        Process process = processBuilder.start();
        call.attachCancellationAction(() -> ProcessTreeTerminator.terminate(process, config.killGracePeriod()));
        try {
            process.getOutputStream().close();
            writeProcessMetadata(workspace, call.callId(), process, command.getFirst(), environment.keySet());
            BoundedTextTail stderrTail = new BoundedTextTail(config.maxStderrTailCharacters());
            Future<?> stdoutFuture = executor.submit(() ->
                    drainStdout(process.getInputStream(), workspace.stdoutLog(), call.callId()));
            Future<?> stderrFuture = executor.submit(() ->
                    drainStderr(process.getErrorStream(), workspace.stderrLog(), stderrTail));

            boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                ProcessTreeTerminator.terminate(process, config.killGracePeriod());
                awaitDrain(stdoutFuture);
                awaitDrain(stderrFuture);
                if (call.isPending()) {
                    call.completeFailed(new AgentCliTimeoutException(
                            call.callId(),
                            workspace.runDirectory(),
                            timeout,
                            stderrTail.toString()));
                }
                return;
            }

            awaitDrain(stdoutFuture);
            awaitDrain(stderrFuture);
            if (!call.isPending()) {
                return;
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                call.completeFailed(new AgentCliExecutionException(
                        "Agent runner exited with code " + exitCode,
                        call.callId(),
                        workspace.runDirectory(),
                        OptionalInt.of(exitCode),
                        Optional.of("PROCESS_EXIT"),
                        true,
                        stderrTail.toString()));
                return;
            }
            parseResult(
                    workspace,
                    request,
                    call,
                    exitCode,
                    stderrTail.toString(),
                    Duration.ofNanos(System.nanoTime() - startedAtNanos));
        } catch (IOException | InterruptedException | RuntimeException ex) {
            ProcessTreeTerminator.terminate(process, config.killGracePeriod());
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw ex;
        }
    }

    private void writeRequest(
            AgentCliWorkspace workspace,
            AiModelRequest request,
            String callId,
            Duration timeout
    ) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractVersion", CONTRACT_VERSION);
        root.put("callId", callId);
        ObjectNode model = root.putObject("model");
        model.put("provider", request.modelId().provider());
        model.put("name", request.modelId().name());
        root.put("timeoutMillis", timeout.toMillis());
        ObjectNode prompt = root.putObject("prompt");
        ObjectNode version = prompt.putObject("version");
        version.put("id", request.prompt().version().id());
        version.put("version", request.prompt().version().version());
        ArrayNode messages = prompt.putArray("messages");
        for (RenderedPrompt.Message message : request.prompt().messages()) {
            ObjectNode item = messages.addObject();
            item.put("role", message.role().name());
            item.put("content", message.content());
        }
        ObjectNode options = root.putObject("options");
        request.options().asMap().forEach((key, value) -> options.set(key, toJsonSafeValue(key, value)));
        ObjectNode paths = root.putObject("paths");
        paths.put("workingDirectory", config.processWorkingDirectory().toString());
        paths.put("runDirectory", workspace.runDirectory().toString());
        paths.put("resultFile", workspace.resultFile().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(workspace.requestFile().toFile(), root);
        AgentCliWorkspace.setOwnerOnly(workspace.requestFile(), false);
    }

    private JsonNode toJsonSafeValue(String key, Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>) {
            return objectMapper.valueToTree(value);
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode object = objectMapper.createObjectNode();
            map.forEach((mapKey, mapValue) -> object.set(
                    String.valueOf(mapKey),
                    toJsonSafeValue(key + "." + mapKey, mapValue)));
            return object;
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayNode array = objectMapper.createArrayNode();
            for (Object item : iterable) {
                array.add(toJsonSafeValue(key, item));
            }
            return array;
        }
        if (value.getClass().isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                array.add(toJsonSafeValue(key, java.lang.reflect.Array.get(value, index)));
            }
            return array;
        }
        throw new GatewayException("Agent CLI option is not JSON-safe: " + key);
    }

    private void writeProcessMetadata(
            AgentCliWorkspace workspace,
            String callId,
            Process process,
            String executable,
            Set<String> environmentNames
    ) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractVersion", CONTRACT_VERSION);
        root.put("callId", callId);
        root.put("pid", process.pid());
        root.put("startedAt", Instant.now().toString());
        root.put("executable", executable);
        ArrayNode names = root.putArray("environmentNames");
        environmentNames.stream().sorted().forEach(names::add);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(workspace.processFile().toFile(), root);
        AgentCliWorkspace.setOwnerOnly(workspace.processFile(), false);
    }

    private List<String> expandCommand(
            AgentCliWorkspace workspace,
            AiModelRequest request,
            String callId
    ) {
        Map<String, String> replacements = Map.of(
                "requestFile", workspace.requestFile().toString(),
                "resultFile", workspace.resultFile().toString(),
                "runDirectory", workspace.runDirectory().toString(),
                "workingDirectory", config.processWorkingDirectory().toString(),
                "callId", callId,
                "model", request.modelId().name());
        List<String> expanded = new ArrayList<>(config.command().size());
        for (String argument : config.command()) {
            Matcher matcher = COMMAND_PLACEHOLDER.matcher(argument);
            StringBuilder value = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(
                        value,
                        Matcher.quoteReplacement(replacements.get(matcher.group(1))));
            }
            matcher.appendTail(value);
            expanded.add(value.toString());
        }
        return List.copyOf(expanded);
    }

    private void drainStdout(InputStream input, Path logFile, String callId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(
                     logFile,
                     StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            AgentCliWorkspace.setOwnerOnly(logFile, false);
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
                parseProgress(callId, line).ifPresent(progressConsumer);
            }
        } catch (IOException ignored) {
            // Process completion and result envelope remain authoritative.
        }
    }

    private void drainStderr(InputStream input, Path logFile, BoundedTextTail tail) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(
                     logFile,
                     StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            AgentCliWorkspace.setOwnerOnly(logFile, false);
            String line;
            while ((line = reader.readLine()) != null) {
                tail.append(line);
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ignored) {
            // The bounded tail collected before an I/O failure is still useful.
        }
    }

    private Optional<AgentCliProgressEvent> parseProgress(String callId, String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            String type = node.path("type").asText("");
            if (!node.isObject() || type.isBlank()) {
                return Optional.empty();
            }
            Optional<Instant> timestamp = parseTimestamp(node.path("timestamp").asText(null));
            Map<String, Object> data = objectMapper.convertValue(
                    node,
                    new TypeReference<Map<String, Object>>() {
                    });
            return Optional.of(new AgentCliProgressEvent(callId, type, timestamp, data, line));
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Instant> parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private void parseResult(
            AgentCliWorkspace workspace,
            AiModelRequest request,
            AgentCliModelCall call,
            int exitCode,
            String stderrTail,
            Duration latency
    ) {
        if (!Files.isRegularFile(workspace.resultFile())) {
            call.completeFailed(protocolFailure(
                    "Agent runner exited successfully without a result file",
                    null,
                    call,
                    workspace,
                    exitCode,
                    stderrTail));
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(workspace.resultFile().toFile());
            requireText(root, "contractVersion", CONTRACT_VERSION);
            String status = requireText(root, "status", null);
            if ("failed".equals(status)) {
                JsonNode error = root.path("error");
                String code = requireText(error, "code", null);
                String message = requireText(error, "message", null);
                boolean retryable = error.path("retryable").asBoolean(false);
                call.completeFailed(new AgentCliExecutionException(
                        "Agent runner reported " + code + ": " + message,
                        call.callId(),
                        workspace.runDirectory(),
                        OptionalInt.of(exitCode),
                        Optional.of(code),
                        retryable,
                        stderrTail));
                return;
            }
            if (!"succeeded".equals(status)) {
                throw new IllegalArgumentException("Unsupported result status: " + status);
            }
            JsonNode output = root.path("output");
            String text = requireString(output, "text");
            String mediaType = output.path("mediaType").asText("text/plain");
            JsonNode metadata = root.path("metadata");
            call.completeReady(new AiModelResponse(
                    text,
                    request.modelId(),
                    new AiModelResponse.ResponseMetadata(
                            optionalInt(metadata.path("inputTokens")),
                            optionalInt(metadata.path("outputTokens")),
                            Optional.of(latency),
                            Optional.of(metadata.path("finishReason").asText("succeeded")),
                            providerTrace(workspace, call.callId(), mediaType, metadata))));
        } catch (Exception ex) {
            call.completeFailed(protocolFailure(
                    "Agent runner produced an invalid result envelope",
                    ex,
                    call,
                    workspace,
                    exitCode,
                    stderrTail));
        }
    }

    private AgentCliProtocolException protocolFailure(
            String message,
            Throwable cause,
            AgentCliModelCall call,
            AgentCliWorkspace workspace,
            int exitCode,
            String stderrTail
    ) {
        return new AgentCliProtocolException(
                message,
                cause,
                call.callId(),
                workspace.runDirectory(),
                OptionalInt.of(exitCode),
                stderrTail);
    }

    private Map<String, String> providerTrace(
            AgentCliWorkspace workspace,
            String callId,
            String mediaType,
            JsonNode metadata
    ) {
        Map<String, String> trace = new LinkedHashMap<>();
        trace.put("callId", callId);
        trace.put("runDirectory", workspace.runDirectory().toString());
        trace.put("mediaType", mediaType);
        if (metadata.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = metadata.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (TRACE_METADATA_KEYS.contains(field.getKey()) && field.getValue().isValueNode()) {
                    String value = field.getValue().asText();
                    if (!value.isBlank() && value.length() <= 1024) {
                        trace.put("runner." + field.getKey(), value);
                    }
                }
            }
        }
        return Map.copyOf(trace);
    }

    private static String requireText(JsonNode parent, String fieldName, String expected) {
        String value = parent.path(fieldName).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be a non-blank string");
        }
        if (expected != null && !expected.equals(value)) {
            throw new IllegalArgumentException(
                    fieldName + " must be " + expected + " but was " + value);
        }
        return value;
    }

    private static String requireString(JsonNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return value.textValue();
    }

    private static Optional<Integer> optionalInt(JsonNode value) {
        if (value != null && value.canConvertToInt()) {
            return Optional.of(value.asInt());
        }
        return Optional.empty();
    }

    private static Duration effectiveTimeout(Duration requested, Duration maximum) {
        return requested.compareTo(maximum) <= 0 ? requested : maximum;
    }

    private static void awaitDrain(Future<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            future.cancel(true);
        }
    }
}

package io.github.parkkevinsb.flower.ai.harness.provider.openaicompatible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.gateway.GatewayException;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link AiModelGateway} for OpenAI-compatible {@code /chat/completions}
 * endpoints.
 */
public final class OpenAiCompatibleModelGateway implements AiModelGateway {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final OpenAiCompatibleGatewayConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleModelGateway(OpenAiCompatibleGatewayConfig config) {
        this(config, HttpClient.newHttpClient(), new ObjectMapper());
    }

    public OpenAiCompatibleModelGateway(
            OpenAiCompatibleGatewayConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String callId = "openai-compatible-call-" + UUID.randomUUID();
        long startedAt = System.nanoTime();
        CompletableFuture<HttpResponse<String>> transportFuture;
        CompletableFuture<AiModelResponse> resultFuture;
        try {
            transportFuture = httpClient.sendAsync(toHttpRequest(request), HttpResponse.BodyHandlers.ofString());
            resultFuture = transportFuture.thenApply(response -> toAiModelResponse(request, response, startedAt));
        } catch (RuntimeException ex) {
            transportFuture = CompletableFuture.failedFuture(ex);
            resultFuture = CompletableFuture.failedFuture(ex);
        }
        return new OpenAiCompatibleModelCall(callId, transportFuture, resultFuture);
    }

    private HttpRequest toHttpRequest(AiModelRequest request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(config.chatCompletionsUri())
                    .timeout(request.timeout())
                    .header(CONTENT_TYPE, APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(toBody(request))));
            config.apiKey().ifPresent(apiKey -> builder.header("Authorization", "Bearer " + apiKey));
            return builder.build();
        } catch (Exception ex) {
            throw new GatewayException("Failed to create OpenAI-compatible request", ex);
        }
    }

    private ObjectNode toBody(AiModelRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.modelId().name());
        body.set("messages", toMessages(request.prompt()));
        putOption(body, request, OpenAiCompatibleOptions.TEMPERATURE, "temperature");
        putOption(body, request, OpenAiCompatibleOptions.MAX_TOKENS, "max_tokens");
        putOption(body, request, OpenAiCompatibleOptions.TOP_P, "top_p");
        putOption(body, request, OpenAiCompatibleOptions.FREQUENCY_PENALTY, "frequency_penalty");
        putOption(body, request, OpenAiCompatibleOptions.PRESENCE_PENALTY, "presence_penalty");
        putOption(body, request, OpenAiCompatibleOptions.STOP_SEQUENCES, "stop");
        putExtraBody(body, request);
        return body;
    }

    private ArrayNode toMessages(RenderedPrompt prompt) {
        ArrayNode messages = objectMapper.createArrayNode();
        for (RenderedPrompt.Message message : prompt.messages()) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("role", role(message.role()));
            item.put("content", message.content());
            messages.add(item);
        }
        return messages;
    }

    private void putOption(ObjectNode body, AiModelRequest request, String optionKey, String jsonKey) {
        request.options().get(optionKey).ifPresent(value -> body.set(jsonKey, objectMapper.valueToTree(value)));
    }

    private void putExtraBody(ObjectNode body, AiModelRequest request) {
        Object value = request.options().get(OpenAiCompatibleOptions.EXTRA_BODY).orElse(null);
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new GatewayException(OpenAiCompatibleOptions.EXTRA_BODY + " must be a map");
        }
        map.forEach((key, mapValue) -> body.set(String.valueOf(key), objectMapper.valueToTree(mapValue)));
    }

    private AiModelResponse toAiModelResponse(
            AiModelRequest request,
            HttpResponse<String> response,
            long startedAt
    ) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GatewayException(errorMessage(response));
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode choice = json.path("choices").path(0);
            String text = choice.path("message").path("content").asText("");
            JsonNode usage = json.path("usage");
            return new AiModelResponse(
                    text,
                    request.modelId(),
                    new AiModelResponse.ResponseMetadata(
                            optionalInt(usage.path("prompt_tokens")),
                            optionalInt(usage.path("completion_tokens")),
                            Optional.of(Duration.ofNanos(System.nanoTime() - startedAt)),
                            Optional.ofNullable(choice.path("finish_reason").asText(null)),
                            providerTrace(json)));
        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("Failed to parse OpenAI-compatible response", ex);
        }
    }

    private String errorMessage(HttpResponse<String> response) {
        String fallback = "OpenAI-compatible provider returned HTTP " + response.statusCode();
        try {
            JsonNode json = objectMapper.readTree(response.body());
            String message = json.path("error").path("message").asText("");
            if (!message.isBlank()) {
                return fallback + ": " + message;
            }
        } catch (Exception ignored) {
            // Keep the status-only fallback when provider error bodies are not JSON.
        }
        return fallback;
    }

    private Map<String, String> providerTrace(JsonNode json) {
        Map<String, String> trace = new LinkedHashMap<>();
        put(trace, "providerResponseId", json.path("id").asText(null));
        put(trace, "providerModel", json.path("model").asText(null));
        return trace;
    }

    private Optional<Integer> optionalInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToInt()) {
            return Optional.empty();
        }
        return Optional.of(node.asInt());
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String role(RenderedPrompt.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }
}

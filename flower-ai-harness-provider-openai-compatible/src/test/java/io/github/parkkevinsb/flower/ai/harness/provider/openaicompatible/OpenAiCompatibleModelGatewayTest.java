package io.github.parkkevinsb.flower.ai.harness.provider.openaicompatible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.parkkevinsb.flower.ai.harness.gateway.GatewayException;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.model.ProviderOptions;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleModelGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsChatCompletionRequestAndMapsResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, """
                    {
                      "id": "chatcmpl-test",
                      "model": "served-model",
                      "choices": [
                        {
                          "message": { "role": "assistant", "content": "{\\"ok\\":true}" },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 12,
                        "completion_tokens": 5
                      }
                    }
                    """);
        });
        OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway(
                OpenAiCompatibleGatewayConfig.builder(serverBaseUri()).apiKey("test-key").build());
        ProviderOptions options = ProviderOptions.empty()
                .with(OpenAiCompatibleOptions.TEMPERATURE, 0.2)
                .with(OpenAiCompatibleOptions.MAX_TOKENS, 128)
                .with(OpenAiCompatibleOptions.STOP_SEQUENCES, List.of("END"));

        AiModelCall call = gateway.submit(request(options));

        assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
        assertThat(call.result().rawText()).isEqualTo("{\"ok\":true}");
        assertThat(call.result().metadata().inputTokens()).contains(12);
        assertThat(call.result().metadata().outputTokens()).contains(5);
        assertThat(call.result().metadata().finishReason()).contains("stop");
        assertThat(call.result().metadata().providerTrace())
                .containsEntry("providerResponseId", "chatcmpl-test")
                .containsEntry("providerModel", "served-model");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");

        JsonNode json = objectMapper.readTree(requestBody.get());
        assertThat(json.path("model").asText()).isEqualTo("gpt-test");
        assertThat(json.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(json.path("max_tokens").asInt()).isEqualTo(128);
        assertThat(json.path("stop").path(0).asText()).isEqualTo("END");
        assertThat(json.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(json.path("messages").path(1).path("role").asText()).isEqualTo("user");
    }

    @Test
    void appendsChatCompletionsWhenBaseUrlDoesNotIncludePath() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            writeJson(exchange, 200, successBody("ok"));
        });
        OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway(
                OpenAiCompatibleGatewayConfig.builder(serverBaseUri()).build());

        AiModelCall call = gateway.submit(request(ProviderOptions.empty()));

        assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
        assertThat(path.get()).isEqualTo("/chat/completions");
    }

    @Test
    void exposesProviderHttpErrorAsGatewayException() throws Exception {
        startServer(exchange -> writeJson(exchange, 429, """
                { "error": { "message": "rate limited" } }
                """));
        OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway(
                OpenAiCompatibleGatewayConfig.builder(serverBaseUri()).build());

        AiModelCall call = gateway.submit(request(ProviderOptions.empty()));

        assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.FAILED);
        assertThat(call.error())
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageContaining("rate limited");
    }

    private void startServer(ThrowingHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private URI serverBaseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private AiModelRequest request(ProviderOptions options) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, "Return JSON only"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Review text")),
                new PromptVersion("openai-compatible-test", "1.0.0"));
        return new AiModelRequest(
                new ModelId("openai-compatible", "gpt-test"),
                prompt,
                options,
                Duration.ofSeconds(2));
    }

    private AiModelCallStatus awaitTerminal(AiModelCall call) {
        for (int i = 0; i < 100; i++) {
            AiModelCallStatus status = call.poll();
            if (status != AiModelCallStatus.PENDING) {
                return status;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for provider call", ex);
            }
        }
        throw new AssertionError("provider call did not reach a terminal state");
    }

    private static String successBody(String text) {
        return """
                {
                  "id": "chatcmpl-test",
                  "model": "served-model",
                  "choices": [
                    {
                      "message": { "role": "assistant", "content": "%s" },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 1,
                    "completion_tokens": 1
                  }
                }
                """.formatted(text);
    }

    private static void writeJson(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }
}

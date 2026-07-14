package io.github.flowerjvm.flower.ai.harness.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class AnthropicModelGatewayTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void sendsMessageRequestAndMapsResponse() {
        AtomicReference<MessageCreateParams> capturedParams = new AtomicReference<>();
        AtomicReference<RequestOptions> capturedRequestOptions = new AtomicReference<>();
        AnthropicClient client = fakeClient(capturedParams, capturedRequestOptions, successMessage(), null);
        AnthropicModelGateway gateway = new AnthropicModelGateway(client, DIRECT_EXECUTOR, 512);
        ProviderOptions options = ProviderOptions.empty()
                .with(AnthropicOptions.MAX_TOKENS, 256)
                .with(AnthropicOptions.TEMPERATURE, 0.2)
                .with(AnthropicOptions.TOP_P, 0.8)
                .with(AnthropicOptions.TOP_K, 20)
                .with(AnthropicOptions.STOP_SEQUENCES, List.of("END"));

        AiModelCall call = gateway.submit(request(options));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.READY);
        assertThat(call.result().rawText()).isEqualTo("{\"ok\":true}");
        assertThat(call.result().metadata().inputTokens()).contains(12);
        assertThat(call.result().metadata().outputTokens()).contains(5);
        assertThat(call.result().metadata().finishReason()).contains("end_turn");
        assertThat(call.result().metadata().providerTrace())
                .containsEntry("providerResponseId", "msg-test")
                .containsEntry("providerModel", "claude-test-served");

        MessageCreateParams params = capturedParams.get();
        assertThat(params.model().asString()).isEqualTo("claude-test");
        assertThat(params.maxTokens()).isEqualTo(256);
        assertThat(params.messages()).hasSize(2);
        assertThat(params.system()).isPresent();
        assertThat(params.temperature()).contains(0.2);
        assertThat(params.topP()).contains(0.8);
        assertThat(params.topK()).contains(20L);
        assertThat(params.stopSequences()).contains(List.of("END"));
        assertThat(capturedRequestOptions.get().getTimeout()).isNotNull();
    }

    @Test
    void usesConfiguredDefaultMaxTokensWhenRequestDoesNotOverride() {
        AtomicReference<MessageCreateParams> capturedParams = new AtomicReference<>();
        AnthropicClient client = fakeClient(capturedParams, new AtomicReference<>(), successMessage(), null);
        AnthropicModelGateway gateway = new AnthropicModelGateway(client, DIRECT_EXECUTOR, 777);

        AiModelCall call = gateway.submit(request(ProviderOptions.empty()));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.READY);
        assertThat(capturedParams.get().maxTokens()).isEqualTo(777);
    }

    @Test
    void exposesSdkFailureAsGatewayException() {
        AnthropicClient client = fakeClient(
                new AtomicReference<>(),
                new AtomicReference<>(),
                null,
                new IllegalStateException("provider unavailable"));
        AnthropicModelGateway gateway = new AnthropicModelGateway(client, DIRECT_EXECUTOR, 512);

        AiModelCall call = gateway.submit(request(ProviderOptions.empty()));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.FAILED);
        assertThat(call.error())
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("Anthropic SDK call failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static AiModelRequest request(ProviderOptions options) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, "Return JSON only"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Review text"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.ASSISTANT, "Acknowledged")),
                new PromptVersion("anthropic-test", "1.0.0"));
        return new AiModelRequest(
                new ModelId("anthropic", "claude-test"),
                prompt,
                options,
                Duration.ofSeconds(2));
    }

    private static Message successMessage() {
        Usage usage = Usage.builder()
                .cacheCreation(Optional.empty())
                .cacheCreationInputTokens(Optional.empty())
                .cacheReadInputTokens(Optional.empty())
                .inferenceGeo(Optional.empty())
                .inputTokens(12)
                .outputTokens(5)
                .outputTokensDetails(Optional.empty())
                .serverToolUse(Optional.empty())
                .serviceTier(Optional.empty())
                .build();
        TextBlock text = TextBlock.builder()
                .citations(Optional.empty())
                .text("{\"ok\":true}")
                .type(JsonValue.from("text"))
                .build();
        return Message.builder()
                .id("msg-test")
                .container(Optional.empty())
                .addContent(text)
                .model("claude-test-served")
                .role(JsonValue.from("assistant"))
                .stopDetails(Optional.empty())
                .stopReason(StopReason.END_TURN)
                .stopSequence(Optional.empty())
                .type(JsonValue.from("message"))
                .usage(usage)
                .build();
    }

    private static AnthropicClient fakeClient(
            AtomicReference<MessageCreateParams> capturedParams,
            AtomicReference<RequestOptions> capturedRequestOptions,
            Message response,
            RuntimeException error
    ) {
        MessageService messages = proxy(MessageService.class, (proxy, method, args) -> {
            if (method.getName().equals("create")
                    && args != null
                    && args.length == 2
                    && args[0] instanceof MessageCreateParams params
                    && args[1] instanceof RequestOptions requestOptions) {
                capturedParams.set(params);
                capturedRequestOptions.set(requestOptions);
                if (error != null) {
                    throw error;
                }
                return response;
            }
            return defaultValue(proxy, method, args);
        });
        return proxy(AnthropicClient.class, (proxy, method, args) -> {
            if (method.getName().equals("messages")) {
                return messages;
            }
            return defaultValue(proxy, method, args);
        });
    }

    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "Fake" + method.getDeclaringClass().getSimpleName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "close" -> null;
            case "withOptions" -> proxy;
            case "withRawResponse" -> throw new UnsupportedOperationException(method.toString());
            case "async" -> throw new UnsupportedOperationException(method.toString());
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}

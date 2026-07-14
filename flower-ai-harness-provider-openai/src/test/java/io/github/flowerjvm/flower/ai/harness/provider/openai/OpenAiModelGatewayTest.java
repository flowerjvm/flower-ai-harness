package io.github.flowerjvm.flower.ai.harness.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
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
class OpenAiModelGatewayTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void sendsChatCompletionRequestAndMapsResponse() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        AtomicReference<RequestOptions> capturedRequestOptions = new AtomicReference<>();
        OpenAIClient client = fakeClient(capturedParams, capturedRequestOptions, successCompletion(), null);
        OpenAiModelGateway gateway = new OpenAiModelGateway(client, DIRECT_EXECUTOR);
        ProviderOptions options = ProviderOptions.empty()
                .with(OpenAiOptions.TEMPERATURE, 0.2)
                .with(OpenAiOptions.MAX_TOKENS, 128)
                .with(OpenAiOptions.TOP_P, 0.8)
                .with(OpenAiOptions.STOP_SEQUENCES, List.of("END"));

        AiModelCall call = gateway.submit(request(options));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.READY);
        assertThat(call.result().rawText()).isEqualTo("{\"ok\":true}");
        assertThat(call.result().metadata().inputTokens()).contains(12);
        assertThat(call.result().metadata().outputTokens()).contains(5);
        assertThat(call.result().metadata().finishReason()).contains("stop");
        assertThat(call.result().metadata().providerTrace())
                .containsEntry("providerResponseId", "chatcmpl-test")
                .containsEntry("providerModel", "gpt-served");

        ChatCompletionCreateParams params = capturedParams.get();
        assertThat(params.model().asString()).isEqualTo("gpt-test");
        assertThat(params.messages()).hasSize(3);
        assertThat(params.temperature()).contains(0.2);
        assertThat(params.maxTokens()).contains(128L);
        assertThat(params.topP()).contains(0.8);
        assertThat(params.stop()).isPresent();
        assertThat(capturedRequestOptions.get().getTimeout()).isNotNull();
    }

    @Test
    void exposesSdkFailureAsGatewayException() {
        OpenAIClient client = fakeClient(
                new AtomicReference<>(),
                new AtomicReference<>(),
                null,
                new IllegalStateException("provider unavailable"));
        OpenAiModelGateway gateway = new OpenAiModelGateway(client, DIRECT_EXECUTOR);

        AiModelCall call = gateway.submit(request(ProviderOptions.empty()));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.FAILED);
        assertThat(call.error())
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("OpenAI SDK call failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static AiModelRequest request(ProviderOptions options) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, "Return JSON only"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Review text"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.ASSISTANT, "Acknowledged")),
                new PromptVersion("openai-test", "1.0.0"));
        return new AiModelRequest(
                new ModelId("openai", "gpt-test"),
                prompt,
                options,
                Duration.ofSeconds(2));
    }

    private static ChatCompletion successCompletion() {
        CompletionUsage usage = CompletionUsage.builder()
                .promptTokens(12)
                .completionTokens(5)
                .totalTokens(17)
                .build();
        ChatCompletionMessage message = ChatCompletionMessage.builder()
                .role(JsonValue.from("assistant"))
                .content("{\"ok\":true}")
                .refusal(Optional.empty())
                .build();
        ChatCompletion.Choice choice = ChatCompletion.Choice.builder()
                .index(0)
                .message(message)
                .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                .logprobs(Optional.empty())
                .build();
        return ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(1)
                .model("gpt-served")
                .object_(JsonValue.from("chat.completion"))
                .addChoice(choice)
                .usage(usage)
                .build();
    }

    private static OpenAIClient fakeClient(
            AtomicReference<ChatCompletionCreateParams> capturedParams,
            AtomicReference<RequestOptions> capturedRequestOptions,
            ChatCompletion response,
            RuntimeException error
    ) {
        ChatCompletionService completions = proxy(ChatCompletionService.class, (proxy, method, args) -> {
            if (method.getName().equals("create")
                    && args != null
                    && args.length == 2
                    && args[0] instanceof ChatCompletionCreateParams params
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
        ChatService chat = proxy(ChatService.class, (proxy, method, args) -> {
            if (method.getName().equals("completions")) {
                return completions;
            }
            return defaultValue(proxy, method, args);
        });
        return proxy(OpenAIClient.class, (proxy, method, args) -> {
            if (method.getName().equals("chat")) {
                return chat;
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

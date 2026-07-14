package io.github.flowerjvm.flower.ai.harness.springai;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiModelGatewayTest {

    private static final ModelId MODEL = ModelId.parse("spring-ai:test-model");

    @Test
    void returnsFutureBackedCallWithoutBlockingWorkerThread() throws Exception {
        BlockingChatModel model = new BlockingChatModel("done");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SpringAiModelGateway gateway = SpringAiModelGateway.fixed(ChatClient.create(model), executor);

            AiModelCall call = gateway.submit(request(MODEL, ProviderOptions.empty()));

            assertThat(model.awaitStarted()).isTrue();
            assertThat(call.poll()).isEqualTo(AiModelCallStatus.PENDING);

            model.release();
            assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
            assertThat(call.result().rawText()).isEqualTo("done");
            assertThat(call.result().metadata().finishReason()).contains("stop");
            assertThat(call.result().metadata().inputTokens()).contains(11);
            assertThat(call.result().metadata().outputTokens()).contains(7);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mapsRenderedPromptAndProviderOptionsToSpringAiPrompt() {
        CapturingChatModel model = new CapturingChatModel("ok");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SpringAiModelGateway gateway = SpringAiModelGateway.fixed(ChatClient.create(model), executor);
            ProviderOptions options = ProviderOptions.empty()
                    .with("model", "gpt-test")
                    .with("temperature", 0.2)
                    .with("maxTokens", 128)
                    .with("topP", 0.9)
                    .with("stopSequences", List.of("END"));

            AiModelCall call = gateway.submit(request(MODEL, options));

            assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
            Prompt prompt = model.lastPrompt();
            assertThat(prompt.getInstructions())
                    .extracting(message -> message.getMessageType())
                    .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
            ChatOptions chatOptions = prompt.getOptions();
            assertThat(chatOptions.getModel()).isEqualTo("gpt-test");
            assertThat(chatOptions.getTemperature()).isEqualTo(0.2);
            assertThat(chatOptions.getMaxTokens()).isEqualTo(128);
            assertThat(chatOptions.getTopP()).isEqualTo(0.9);
            assertThat(chatOptions.getStopSequences()).containsExactly("END");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reportsFailedStatusWhenSpringAiThrows() {
        RuntimeException error = new RuntimeException("provider down");
        CapturingChatModel model = new CapturingChatModel(error);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SpringAiModelGateway gateway = SpringAiModelGateway.fixed(ChatClient.create(model), executor);

            AiModelCall call = gateway.submit(request(MODEL, ProviderOptions.empty()));

            assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.FAILED);
            assertThat(call.error()).isSameAs(error);
            assertThatThrownBy(call::result)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not ready");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void routesRequestsByModelId() {
        ModelId primary = ModelId.parse("spring-ai:primary");
        ModelId fallback = ModelId.parse("spring-ai:fallback");
        CapturingChatModel primaryModel = new CapturingChatModel("primary");
        CapturingChatModel fallbackModel = new CapturingChatModel("fallback");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            SpringAiModelGateway gateway = SpringAiModelGateway.routing(
                    Map.of(
                            primary, ChatClient.create(primaryModel),
                            fallback, ChatClient.create(fallbackModel)),
                    executor);

            AiModelCall call = gateway.submit(request(fallback, ProviderOptions.empty()));

            assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
            assertThat(call.result().rawText()).isEqualTo("fallback");
            assertThat(primaryModel.wasCalled()).isFalse();
            assertThat(fallbackModel.wasCalled()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private static AiModelRequest request(ModelId modelId, ProviderOptions options) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, "Return JSON only"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Review text"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.ASSISTANT, "Acknowledged")),
                new PromptVersion("spring-ai-test", "1.0.0"));
        return new AiModelRequest(modelId, prompt, options, Duration.ofSeconds(2));
    }

    private static AiModelCallStatus awaitTerminal(AiModelCall call) {
        for (int i = 0; i < 100; i++) {
            AiModelCallStatus status = call.poll();
            if (status != AiModelCallStatus.PENDING) {
                return status;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for fake Spring AI call", e);
            }
        }
        throw new AssertionError("call did not reach a terminal state");
    }

    private static class CapturingChatModel implements ChatModel {

        private final String text;
        private final RuntimeException error;
        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

        CapturingChatModel(String text) {
            this.text = text;
            this.error = null;
        }

        CapturingChatModel(RuntimeException error) {
            this.text = null;
            this.error = error;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            if (error != null) {
                throw error;
            }
            return response(text);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

        Prompt lastPrompt() {
            return lastPrompt.get();
        }

        boolean wasCalled() {
            return lastPrompt.get() != null;
        }
    }

    private static final class BlockingChatModel extends CapturingChatModel {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingChatModel(String text) {
            super(text);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            started.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("test did not release blocking chat model");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting", e);
            }
            return super.call(prompt);
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }
    }

    private static ChatResponse response(String text) {
        Generation generation = new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        return new ChatResponse(
                List.of(generation),
                ChatResponseMetadata.builder()
                        .id("response-1")
                        .model("spring-model")
                        .usage(new TestUsage())
                        .build());
    }

    private static final class TestUsage implements Usage {

        @Override
        public Integer getPromptTokens() {
            return 11;
        }

        @Override
        public Integer getCompletionTokens() {
            return 7;
        }

        @Override
        public Object getNativeUsage() {
            return "test";
        }
    }
}

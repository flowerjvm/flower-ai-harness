package io.github.flowerjvm.flower.ai.harness.springboot;

import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.springai.SpringAiModelGateway;
import io.github.flowerjvm.flower.ai.harness.springai.SpringAiModelResolver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerAiHarnessSpringAiAutoConfigurationTest {

    private static final ModelId MODEL = ModelId.parse("spring-ai:test-model");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlowerAiHarnessSpringAiAutoConfiguration.class));

    @Test
    void createsGatewayForSingleChatClient() {
        contextRunner
                .withBean(ChatClient.class, () -> ChatClient.create(new StaticChatModel("ok")))
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringAiModelResolver.class);
                    assertThat(context).hasSingleBean(SpringAiModelGateway.class);
                    assertThat(context).hasSingleBean(AiModelGateway.class);

                    AiModelCall call = context.getBean(AiModelGateway.class).submit(request(MODEL));

                    assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
                    assertThat(call.result().rawText()).isEqualTo("ok");
                });
    }

    @Test
    void backsOffWhenGatewayAlreadyExists() {
        AiModelGateway customGateway = request -> {
            throw new UnsupportedOperationException("custom");
        };

        contextRunner
                .withBean(AiModelGateway.class, () -> customGateway)
                .withBean(ChatClient.class, () -> ChatClient.create(new StaticChatModel("ignored")))
                .run(context -> {
                    assertThat(context).hasSingleBean(AiModelGateway.class);
                    assertThat(context).doesNotHaveBean(SpringAiModelGateway.class);
                    assertThat(context.getBean(AiModelGateway.class)).isSameAs(customGateway);
                });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
                .withPropertyValues("flower.ai.harness.spring-ai.enabled=false")
                .withBean(ChatClient.class, () -> ChatClient.create(new StaticChatModel("ignored")))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpringAiModelResolver.class);
                    assertThat(context).doesNotHaveBean(AiModelGateway.class);
                    assertThat(context)
                            .doesNotHaveBean(FlowerAiHarnessSpringAiAutoConfiguration.MODEL_EXECUTOR_BEAN_NAME);
                });
    }

    @Test
    void requiresExplicitResolverWhenMultipleChatClientsExist() {
        contextRunner
                .withBean("primaryChatClient", ChatClient.class, () -> ChatClient.create(new StaticChatModel("one")))
                .withBean("secondaryChatClient", ChatClient.class, () -> ChatClient.create(new StaticChatModel("two")))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpringAiModelResolver.class);
                    assertThat(context).doesNotHaveBean(AiModelGateway.class);
                });
    }

    @Test
    void usesUserResolverWhenProvided() {
        contextRunner
                .withBean(SpringAiModelResolver.class,
                        () -> modelId -> ChatClient.create(new StaticChatModel(modelId.asString())))
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringAiModelGateway.class);

                    AiModelCall call = context.getBean(AiModelGateway.class).submit(request(MODEL));

                    assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
                    assertThat(call.result().rawText()).isEqualTo(MODEL.asString());
                });
    }

    @Test
    void appliesExecutorThreadNamePrefixProperty() {
        AtomicReference<String> threadName = new AtomicReference<>();

        contextRunner
                .withPropertyValues("flower.ai.harness.spring-ai.executor.thread-name-prefix=test-harness-")
                .withBean(SpringAiModelResolver.class,
                        () -> modelId -> ChatClient.create(new ThreadCapturingChatModel(threadName)))
                .run(context -> {
                    AiModelCall call = context.getBean(AiModelGateway.class).submit(request(MODEL));

                    assertThat(awaitTerminal(call)).isEqualTo(AiModelCallStatus.READY);
                    assertThat(threadName.get()).startsWith("test-harness-");
                });
    }

    private static AiModelRequest request(ModelId modelId) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Review this text")),
                new PromptVersion("boot-starter-test", "1.0.0"));
        return new AiModelRequest(modelId, prompt, ProviderOptions.empty(), Duration.ofSeconds(2));
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
                throw new AssertionError("interrupted while waiting for Spring Boot starter test call", e);
            }
        }
        throw new AssertionError("call did not reach a terminal state");
    }

    private static class StaticChatModel implements ChatModel {

        private final String text;

        StaticChatModel(String text) {
            this.text = text;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response(text);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }

    private static final class ThreadCapturingChatModel extends StaticChatModel {

        private final AtomicReference<String> threadName;

        ThreadCapturingChatModel(AtomicReference<String> threadName) {
            super("ok");
            this.threadName = threadName;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            threadName.set(Thread.currentThread().getName());
            return super.call(prompt);
        }
    }

    private static ChatResponse response(String text) {
        Generation generation = new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        return new ChatResponse(
                List.of(generation),
                ChatResponseMetadata.builder()
                        .id("boot-response-1")
                        .model("boot-test-model")
                        .build());
    }
}

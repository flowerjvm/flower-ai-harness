package io.github.parkkevinsb.flower.ai.harness.springai;

import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AiModelGateway} backed by Spring AI's {@link ChatClient}.
 *
 * <p>The request timeout bounds the returned {@link CompletableFuture}. It does
 * not guarantee that the underlying provider HTTP request is interrupted
 * immediately. Production applications should still configure provider/client
 * timeouts in Spring AI and the underlying HTTP client.
 */
public final class SpringAiModelGateway implements AiModelGateway {

    private final SpringAiModelResolver resolver;
    private final Executor executor;
    private final AtomicLong sequence = new AtomicLong();

    public SpringAiModelGateway(SpringAiModelResolver resolver, Executor executor) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public static SpringAiModelGateway fixed(ChatClient client, Executor executor) {
        return new SpringAiModelGateway(SpringAiModelResolver.fixed(client), executor);
    }

    public static SpringAiModelGateway routing(Map<ModelId, ChatClient> clients, Executor executor) {
        return new SpringAiModelGateway(SpringAiModelResolver.byModelId(clients), executor);
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ChatClient client = resolver.resolve(request.modelId());
        String callId = "spring-ai-call-" + sequence.incrementAndGet();
        CompletableFuture<AiModelResponse> future = CompletableFuture
                .supplyAsync(() -> callSpringAi(client, request), executor)
                .orTimeout(request.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return new FutureBackedAiModelCall(callId, future);
    }

    private static AiModelResponse callSpringAi(ChatClient client, AiModelRequest request) {
        long startedAtNanos = System.nanoTime();
        ChatResponse response = client.prompt(SpringAiPromptMapper.toPrompt(request)).call().chatResponse();
        Duration latency = Duration.ofNanos(System.nanoTime() - startedAtNanos);
        return SpringAiResponseMapper.toAiModelResponse(request.modelId(), response, latency);
    }
}

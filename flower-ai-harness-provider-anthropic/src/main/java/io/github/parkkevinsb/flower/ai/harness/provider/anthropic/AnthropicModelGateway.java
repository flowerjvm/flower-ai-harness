package io.github.parkkevinsb.flower.ai.harness.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.gateway.GatewayException;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * {@link AiModelGateway} backed by the official Anthropic Java SDK.
 *
 * <p>Anthropic's Messages API requires {@code max_tokens}. If a request does
 * not provide {@link AnthropicOptions#MAX_TOKENS}, this gateway uses the
 * configured default from {@link AnthropicGatewayConfig}.
 */
@SuppressWarnings("deprecation")
public final class AnthropicModelGateway implements AiModelGateway {

    private final AnthropicClient client;
    private final Executor executor;
    private final long defaultMaxTokens;
    private final AtomicLong sequence = new AtomicLong();

    public AnthropicModelGateway(AnthropicGatewayConfig config, Executor executor) {
        this(
                Objects.requireNonNull(config, "config must not be null").createClient(),
                executor,
                config.defaultMaxTokens());
    }

    public AnthropicModelGateway(AnthropicClient client, Executor executor, long defaultMaxTokens) {
        if (defaultMaxTokens <= 0) {
            throw new IllegalArgumentException("defaultMaxTokens must be greater than 0");
        }
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.defaultMaxTokens = defaultMaxTokens;
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String callId = "anthropic-call-" + sequence.incrementAndGet();
        CompletableFuture<AiModelResponse> future = CompletableFuture
                .supplyAsync(() -> callAnthropic(request), executor)
                .orTimeout(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        return new AnthropicModelCall(callId, future);
    }

    private AiModelResponse callAnthropic(AiModelRequest request) {
        long startedAtNanos = System.nanoTime();
        try {
            Message message = client.messages().create(toParams(request), requestOptions(request));
            Duration latency = Duration.ofNanos(System.nanoTime() - startedAtNanos);
            return toAiModelResponse(request, message, latency);
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException("Anthropic SDK call failed", ex);
        }
    }

    private MessageCreateParams toParams(AiModelRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(request.modelId().name())
                .maxTokens(maxTokens(request));
        List<String> systemMessages = new ArrayList<>();
        int conversationMessageCount = 0;
        for (RenderedPrompt.Message message : request.prompt().messages()) {
            switch (message.role()) {
                case SYSTEM -> systemMessages.add(message.content());
                case USER -> {
                    builder.addUserMessage(message.content());
                    conversationMessageCount++;
                }
                case ASSISTANT -> {
                    builder.addAssistantMessage(message.content());
                    conversationMessageCount++;
                }
            }
        }
        if (!systemMessages.isEmpty()) {
            builder.system(String.join("\n\n", systemMessages));
        }
        if (conversationMessageCount == 0) {
            throw new GatewayException("Anthropic request requires at least one user or assistant message");
        }
        putDouble(builder, request, AnthropicOptions.TEMPERATURE, AnthropicDoubleOptionTarget.TEMPERATURE);
        putDouble(builder, request, AnthropicOptions.TOP_P, AnthropicDoubleOptionTarget.TOP_P);
        putLong(builder, request, AnthropicOptions.TOP_K, AnthropicLongOptionTarget.TOP_K);
        putString(builder, request, AnthropicOptions.USER_PROFILE_ID, builder::userProfileId);
        putString(builder, request, AnthropicOptions.CONTAINER, builder::container);
        putStopSequences(builder, request);
        return builder.build();
    }

    private long maxTokens(AiModelRequest request) {
        return request.options()
                .get(AnthropicOptions.MAX_TOKENS)
                .map(value -> asLong(AnthropicOptions.MAX_TOKENS, value))
                .orElse(defaultMaxTokens);
    }

    private static RequestOptions requestOptions(AiModelRequest request) {
        return RequestOptions.builder()
                .timeout(request.timeout())
                .build();
    }

    private static void putDouble(
            MessageCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            AnthropicDoubleOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(builder, asDouble(key, value)));
    }

    private static void putLong(
            MessageCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            AnthropicLongOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(builder, asLong(key, value)));
    }

    private static void putString(
            MessageCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            AnthropicStringOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(asString(key, value)));
    }

    private static void putStopSequences(MessageCreateParams.Builder builder, AiModelRequest request) {
        request.options().get(AnthropicOptions.STOP_SEQUENCES).ifPresent(value -> {
            if (value instanceof String text) {
                builder.addStopSequence(text);
                return;
            }
            if (value instanceof Iterable<?> values) {
                List<String> stopSequences = new ArrayList<>();
                values.forEach(item -> stopSequences.add(asString(AnthropicOptions.STOP_SEQUENCES, item)));
                builder.stopSequences(stopSequences);
                return;
            }
            throw new GatewayException(AnthropicOptions.STOP_SEQUENCES + " must be a string or iterable of strings");
        });
    }

    private static AiModelResponse toAiModelResponse(
            AiModelRequest request,
            Message message,
            Duration latency
    ) {
        String rawText = message.content().stream()
                .filter(ContentBlock::isText)
                .map(ContentBlock::asText)
                .map(TextBlock::text)
                .collect(Collectors.joining());
        Usage usage = message.usage();
        return new AiModelResponse(
                rawText,
                request.modelId(),
                new AiModelResponse.ResponseMetadata(
                        optionalInt(usage.inputTokens()),
                        optionalInt(usage.outputTokens()),
                        Optional.of(latency),
                        message.stopReason().map(StopReason::asString),
                        providerTrace(message)));
    }

    private static Map<String, String> providerTrace(Message message) {
        Map<String, String> trace = new LinkedHashMap<>();
        put(trace, "providerResponseId", message.id());
        put(trace, "providerModel", message.model().asString());
        message.stopSequence().ifPresent(value -> put(trace, "stopSequence", value));
        return trace;
    }

    private static Optional<Integer> optionalInt(long value) {
        try {
            return Optional.of(Math.toIntExact(value));
        } catch (ArithmeticException ex) {
            return Optional.empty();
        }
    }

    private static double asDouble(String key, Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new GatewayException(key + " must be a number");
    }

    private static long asLong(String key, Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new GatewayException(key + " must be a number");
    }

    private static String asString(String key, Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new GatewayException(key + " must be a string");
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    @FunctionalInterface
    private interface AnthropicDoubleOptionTarget {
        void apply(MessageCreateParams.Builder builder, double value);

        AnthropicDoubleOptionTarget TEMPERATURE = MessageCreateParams.Builder::temperature;
        AnthropicDoubleOptionTarget TOP_P = MessageCreateParams.Builder::topP;
    }

    @FunctionalInterface
    private interface AnthropicLongOptionTarget {
        void apply(MessageCreateParams.Builder builder, long value);

        AnthropicLongOptionTarget TOP_K = MessageCreateParams.Builder::topK;
    }

    @FunctionalInterface
    private interface AnthropicStringOptionTarget {
        void apply(String value);
    }
}

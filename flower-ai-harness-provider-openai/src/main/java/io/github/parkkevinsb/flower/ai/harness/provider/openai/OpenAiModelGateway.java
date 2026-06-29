package io.github.parkkevinsb.flower.ai.harness.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
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

/**
 * {@link AiModelGateway} backed by the official OpenAI Java SDK.
 *
 * <p>The OpenAI SDK call is executed on the supplied {@link Executor}; Flower
 * workers should only poll the returned {@link AiModelCall}. The request
 * timeout is passed to the SDK and also bounds the returned future.
 */
@SuppressWarnings("deprecation")
public final class OpenAiModelGateway implements AiModelGateway {

    private final OpenAIClient client;
    private final Executor executor;
    private final AtomicLong sequence = new AtomicLong();

    public OpenAiModelGateway(OpenAiGatewayConfig config, Executor executor) {
        this(Objects.requireNonNull(config, "config must not be null").createClient(), executor);
    }

    public OpenAiModelGateway(OpenAIClient client, Executor executor) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String callId = "openai-call-" + sequence.incrementAndGet();
        CompletableFuture<AiModelResponse> future = CompletableFuture
                .supplyAsync(() -> callOpenAi(request), executor)
                .orTimeout(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        return new OpenAiModelCall(callId, future);
    }

    private AiModelResponse callOpenAi(AiModelRequest request) {
        long startedAtNanos = System.nanoTime();
        try {
            ChatCompletion completion = client.chat()
                    .completions()
                    .create(toParams(request), requestOptions(request));
            Duration latency = Duration.ofNanos(System.nanoTime() - startedAtNanos);
            return toAiModelResponse(request, completion, latency);
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException("OpenAI SDK call failed", ex);
        }
    }

    private ChatCompletionCreateParams toParams(AiModelRequest request) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.modelId().name());
        for (RenderedPrompt.Message message : request.prompt().messages()) {
            addMessage(builder, message);
        }
        putDouble(builder, request, OpenAiOptions.TEMPERATURE, OpenAiOptionTarget.TEMPERATURE);
        putDouble(builder, request, OpenAiOptions.TOP_P, OpenAiOptionTarget.TOP_P);
        putDouble(builder, request, OpenAiOptions.FREQUENCY_PENALTY, OpenAiOptionTarget.FREQUENCY_PENALTY);
        putDouble(builder, request, OpenAiOptions.PRESENCE_PENALTY, OpenAiOptionTarget.PRESENCE_PENALTY);
        putLong(builder, request, OpenAiOptions.MAX_TOKENS, OpenAiLongOptionTarget.MAX_TOKENS);
        putLong(builder, request, OpenAiOptions.MAX_COMPLETION_TOKENS, OpenAiLongOptionTarget.MAX_COMPLETION_TOKENS);
        putLong(builder, request, OpenAiOptions.SEED, OpenAiLongOptionTarget.SEED);
        putString(builder, request, OpenAiOptions.USER, builder::user);
        putBoolean(builder, request, OpenAiOptions.STORE, builder::store);
        putStopSequences(builder, request);
        return builder.build();
    }

    private static RequestOptions requestOptions(AiModelRequest request) {
        return RequestOptions.builder()
                .timeout(request.timeout())
                .build();
    }

    private static void addMessage(ChatCompletionCreateParams.Builder builder, RenderedPrompt.Message message) {
        switch (message.role()) {
            case SYSTEM -> builder.addSystemMessage(message.content());
            case USER -> builder.addUserMessage(message.content());
            case ASSISTANT -> builder.addAssistantMessage(message.content());
        }
    }

    private static void putDouble(
            ChatCompletionCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            OpenAiOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(builder, asDouble(key, value)));
    }

    private static void putLong(
            ChatCompletionCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            OpenAiLongOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(builder, asLong(key, value)));
    }

    private static void putString(
            ChatCompletionCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            StringOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(asString(key, value)));
    }

    private static void putBoolean(
            ChatCompletionCreateParams.Builder builder,
            AiModelRequest request,
            String key,
            BooleanOptionTarget target
    ) {
        request.options().get(key).ifPresent(value -> target.apply(asBoolean(key, value)));
    }

    private static void putStopSequences(ChatCompletionCreateParams.Builder builder, AiModelRequest request) {
        request.options().get(OpenAiOptions.STOP_SEQUENCES).ifPresent(value -> {
            if (value instanceof String text) {
                builder.stop(text);
                return;
            }
            if (value instanceof Iterable<?> values) {
                List<String> stopSequences = new ArrayList<>();
                values.forEach(item -> stopSequences.add(asString(OpenAiOptions.STOP_SEQUENCES, item)));
                builder.stopOfStrings(stopSequences);
                return;
            }
            throw new GatewayException(OpenAiOptions.STOP_SEQUENCES + " must be a string or iterable of strings");
        });
    }

    private static AiModelResponse toAiModelResponse(
            AiModelRequest request,
            ChatCompletion completion,
            Duration latency
    ) {
        Optional<ChatCompletion.Choice> maybeChoice = completion.choices().stream().findFirst();
        String rawText = maybeChoice
                .map(ChatCompletion.Choice::message)
                .flatMap(ChatCompletionMessage::content)
                .orElse("");
        Optional<String> finishReason = maybeChoice
                .map(ChatCompletion.Choice::finishReason)
                .map(ChatCompletion.Choice.FinishReason::asString);
        Optional<CompletionUsage> usage = completion.usage();
        return new AiModelResponse(
                rawText,
                request.modelId(),
                new AiModelResponse.ResponseMetadata(
                        usage.map(CompletionUsage::promptTokens).flatMap(OpenAiModelGateway::optionalInt),
                        usage.map(CompletionUsage::completionTokens).flatMap(OpenAiModelGateway::optionalInt),
                        Optional.of(latency),
                        finishReason,
                        providerTrace(completion)));
    }

    private static Map<String, String> providerTrace(ChatCompletion completion) {
        Map<String, String> trace = new LinkedHashMap<>();
        put(trace, "providerResponseId", completion.id());
        put(trace, "providerModel", completion.model());
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

    private static boolean asBoolean(String key, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new GatewayException(key + " must be a boolean");
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    @FunctionalInterface
    private interface OpenAiOptionTarget {
        void apply(ChatCompletionCreateParams.Builder builder, double value);

        OpenAiOptionTarget TEMPERATURE = ChatCompletionCreateParams.Builder::temperature;
        OpenAiOptionTarget TOP_P = ChatCompletionCreateParams.Builder::topP;
        OpenAiOptionTarget FREQUENCY_PENALTY = ChatCompletionCreateParams.Builder::frequencyPenalty;
        OpenAiOptionTarget PRESENCE_PENALTY = ChatCompletionCreateParams.Builder::presencePenalty;
    }

    @FunctionalInterface
    private interface OpenAiLongOptionTarget {
        void apply(ChatCompletionCreateParams.Builder builder, long value);

        OpenAiLongOptionTarget MAX_TOKENS = ChatCompletionCreateParams.Builder::maxTokens;
        OpenAiLongOptionTarget MAX_COMPLETION_TOKENS = ChatCompletionCreateParams.Builder::maxCompletionTokens;
        OpenAiLongOptionTarget SEED = ChatCompletionCreateParams.Builder::seed;
    }

    @FunctionalInterface
    private interface StringOptionTarget {
        void apply(String value);
    }

    @FunctionalInterface
    private interface BooleanOptionTarget {
        void apply(boolean value);
    }
}

package io.github.flowerjvm.flower.ai.harness.springai;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class SpringAiPromptMapper {

    static final String CHAT_OPTIONS_KEY = "springAi.chatOptions";
    static final String MODEL_KEY = "model";
    static final String TEMPERATURE_KEY = "temperature";
    static final String MAX_TOKENS_KEY = "maxTokens";
    static final String TOP_P_KEY = "topP";
    static final String TOP_K_KEY = "topK";
    static final String FREQUENCY_PENALTY_KEY = "frequencyPenalty";
    static final String PRESENCE_PENALTY_KEY = "presencePenalty";
    static final String STOP_SEQUENCES_KEY = "stopSequences";

    private SpringAiPromptMapper() {
    }

    static Prompt toPrompt(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<Message> messages = request.prompt().messages().stream()
                .map(SpringAiPromptMapper::toMessage)
                .toList();
        ChatOptions options = toChatOptions(request.options());
        return options == null ? new Prompt(messages) : new Prompt(messages, options);
    }

    private static Message toMessage(RenderedPrompt.Message message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ChatOptions toChatOptions(ProviderOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        Object direct = options.get(CHAT_OPTIONS_KEY).orElse(null);
        if (direct instanceof ChatOptions chatOptions) {
            return chatOptions;
        }
        if (options.asMap().isEmpty()) {
            return null;
        }

        ChatOptions.Builder builder = ChatOptions.builder();
        boolean used = false;
        used |= setString(options, MODEL_KEY, builder::model);
        used |= setDouble(options, TEMPERATURE_KEY, builder::temperature);
        used |= setInteger(options, MAX_TOKENS_KEY, builder::maxTokens);
        used |= setDouble(options, TOP_P_KEY, builder::topP);
        used |= setInteger(options, TOP_K_KEY, builder::topK);
        used |= setDouble(options, FREQUENCY_PENALTY_KEY, builder::frequencyPenalty);
        used |= setDouble(options, PRESENCE_PENALTY_KEY, builder::presencePenalty);
        used |= setStopSequences(options, builder);
        return used ? builder.build() : null;
    }

    private static boolean setString(ProviderOptions options, String key, java.util.function.Consumer<String> setter) {
        Object value = options.get(key).orElse(null);
        if (value == null) {
            return false;
        }
        setter.accept(value.toString());
        return true;
    }

    private static boolean setDouble(ProviderOptions options, String key, java.util.function.Consumer<Double> setter) {
        Object value = options.get(key).orElse(null);
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            setter.accept(number.doubleValue());
            return true;
        }
        setter.accept(Double.valueOf(value.toString()));
        return true;
    }

    private static boolean setInteger(ProviderOptions options, String key, java.util.function.Consumer<Integer> setter) {
        Object value = options.get(key).orElse(null);
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            setter.accept(number.intValue());
            return true;
        }
        setter.accept(Integer.valueOf(value.toString()));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean setStopSequences(ProviderOptions options, ChatOptions.Builder builder) {
        Object value = options.get(STOP_SEQUENCES_KEY).orElse(null);
        if (value == null) {
            return false;
        }
        if (value instanceof List<?> list) {
            builder.stopSequences(list.stream().map(Object::toString).toList());
            return true;
        }
        if (value instanceof String[] array) {
            builder.stopSequences(List.of(array));
            return true;
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                values.add(item.toString());
            }
            builder.stopSequences(values);
            return true;
        }
        builder.stopSequences(List.of(value.toString()));
        return true;
    }
}

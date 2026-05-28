package io.github.parkkevinsb.flower.ai.harness.springai;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse.ResponseMetadata;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class SpringAiResponseMapper {

    private SpringAiResponseMapper() {
    }

    static AiModelResponse toAiModelResponse(ModelId modelId, ChatResponse response, Duration latency) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("Spring AI returned an empty chat response");
        }
        Generation result = response.getResult();
        String rawText = result.getOutput() == null ? "" : result.getOutput().getText();
        return new AiModelResponse(rawText, modelId, metadata(response, result, latency));
    }

    private static ResponseMetadata metadata(ChatResponse response, Generation result, Duration latency) {
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        Map<String, String> providerTrace = new LinkedHashMap<>();
        if (metadata != null) {
            put(providerTrace, "springAi.responseId", metadata.getId());
            put(providerTrace, "springAi.model", metadata.getModel());
        }
        return new ResponseMetadata(
                usage == null ? Optional.empty() : Optional.ofNullable(usage.getPromptTokens()),
                usage == null ? Optional.empty() : Optional.ofNullable(usage.getCompletionTokens()),
                Optional.ofNullable(latency),
                Optional.ofNullable(result.getMetadata().getFinishReason()),
                providerTrace);
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}

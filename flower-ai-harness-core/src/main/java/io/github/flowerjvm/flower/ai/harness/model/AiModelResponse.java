package io.github.flowerjvm.flower.ai.harness.model;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provider-neutral response preserving the model's raw text output.
 */
public record AiModelResponse(
        String rawText,
        ModelId modelId,
        ResponseMetadata metadata
) {

    public AiModelResponse {
        Objects.requireNonNull(rawText, "rawText must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        metadata = metadata == null ? ResponseMetadata.empty() : metadata;
    }

    public record ResponseMetadata(
            Optional<Integer> inputTokens,
            Optional<Integer> outputTokens,
            Optional<Duration> latency,
            Optional<String> finishReason,
            Map<String, String> providerTrace
    ) {

        private static final ResponseMetadata EMPTY = new ResponseMetadata(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of());

        public ResponseMetadata {
            inputTokens = inputTokens == null ? Optional.empty() : inputTokens;
            outputTokens = outputTokens == null ? Optional.empty() : outputTokens;
            latency = latency == null ? Optional.empty() : latency;
            finishReason = finishReason == null ? Optional.empty() : finishReason;
            providerTrace = providerTrace == null ? Map.of() : Map.copyOf(providerTrace);
        }

        public static ResponseMetadata empty() {
            return EMPTY;
        }
    }
}

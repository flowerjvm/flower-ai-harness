package io.github.flowerjvm.flower.ai.harness.model;

import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;

import java.time.Duration;
import java.util.Objects;

/**
 * Provider-neutral unit of work submitted to an AI model gateway.
 */
public record AiModelRequest(
        ModelId modelId,
        RenderedPrompt prompt,
        ProviderOptions options,
        Duration timeout
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public AiModelRequest {
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        options = options == null ? ProviderOptions.empty() : options;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public AiModelRequest withModelId(ModelId next) {
        return new AiModelRequest(next, prompt, options, timeout);
    }

    public AiModelRequest withPrompt(RenderedPrompt next) {
        return new AiModelRequest(modelId, next, options, timeout);
    }

    public AiModelRequest withOptions(ProviderOptions next) {
        return new AiModelRequest(modelId, prompt, next, timeout);
    }
}

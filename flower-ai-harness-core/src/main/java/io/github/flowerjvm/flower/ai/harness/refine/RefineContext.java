package io.github.flowerjvm.flower.ai.harness.refine;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.util.Objects;

/**
 * Inputs available to a retry/refine policy for one decision.
 */
public record RefineContext(
        AiHarnessRunContext run,
        AiModelRequest lastRequest,
        AiModelResponse lastResponse,
        ValidationResult<?> lastValidation,
        Throwable callError,
        int attempt,
        int maxAttempts
) {

    public RefineContext {
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(lastRequest, "lastRequest must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    public boolean hasCallError() {
        return callError != null;
    }

    public boolean hasInvalidValidation() {
        return lastValidation instanceof ValidationResult.Invalid<?>;
    }

    public boolean hasValidValidation() {
        return lastValidation instanceof ValidationResult.Valid<?>;
    }
}

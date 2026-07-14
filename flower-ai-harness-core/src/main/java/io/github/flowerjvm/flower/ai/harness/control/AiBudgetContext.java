package io.github.flowerjvm.flower.ai.harness.control;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.Objects;

/**
 * Inputs available to a budget policy before a provider call is submitted.
 */
public record AiBudgetContext(
        AiHarnessRunContext runContext,
        AiModelRequest request,
        int nextAttempt
) {

    public AiBudgetContext {
        Objects.requireNonNull(runContext, "runContext must not be null");
        Objects.requireNonNull(request, "request must not be null");
        if (nextAttempt < 1) {
            throw new IllegalArgumentException("nextAttempt must be at least 1");
        }
    }
}

package io.github.parkkevinsb.flower.ai.harness.spi;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;

import java.util.List;

/**
 * Passive observation hook for harness lifecycle events.
 */
public interface TraceListener {

    default void onRunStarted(AiHarnessRunContext ctx) {
    }

    default void onRequestSubmitted(AiHarnessRunContext ctx, AiModelRequest request, String callId) {
    }

    default void onResponseReceived(AiHarnessRunContext ctx, AiModelResponse response) {
    }

    default void onCallFailed(AiHarnessRunContext ctx, Throwable error) {
    }

    default void onValidationCompleted(AiHarnessRunContext ctx, ValidationResult<?> result) {
    }

    default void onRefineTriggered(AiHarnessRunContext ctx, AiModelRequest nextRequest) {
    }

    default void onRunCompleted(AiHarnessRunContext ctx, List<AiFinding> findings) {
    }

    default void onRunFailed(AiHarnessRunContext ctx, String reason) {
    }
}

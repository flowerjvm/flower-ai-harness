package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.spi.TraceListener;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.util.List;
import java.util.function.Consumer;

final class TraceEvents {

    private TraceEvents() {
    }

    static void runStarted(List<TraceListener> listeners, AiHarnessRunContext ctx) {
        fire(listeners, l -> l.onRunStarted(ctx));
    }

    static void requestSubmitted(
            List<TraceListener> listeners,
            AiHarnessRunContext ctx,
            AiModelRequest request,
            String callId
    ) {
        fire(listeners, l -> l.onRequestSubmitted(ctx, request, callId));
    }

    static void responseReceived(
            List<TraceListener> listeners,
            AiHarnessRunContext ctx,
            AiModelResponse response
    ) {
        fire(listeners, l -> l.onResponseReceived(ctx, response));
    }

    static void callFailed(List<TraceListener> listeners, AiHarnessRunContext ctx, Throwable error) {
        fire(listeners, l -> l.onCallFailed(ctx, error));
    }

    static void validationCompleted(
            List<TraceListener> listeners,
            AiHarnessRunContext ctx,
            ValidationResult<?> result
    ) {
        fire(listeners, l -> l.onValidationCompleted(ctx, result));
    }

    static void refineTriggered(
            List<TraceListener> listeners,
            AiHarnessRunContext ctx,
            AiModelRequest nextRequest
    ) {
        fire(listeners, l -> l.onRefineTriggered(ctx, nextRequest));
    }

    static void runCompleted(List<TraceListener> listeners, AiHarnessRunContext ctx, List<AiFinding> findings) {
        fire(listeners, l -> l.onRunCompleted(ctx, findings));
    }

    static void runCancelled(List<TraceListener> listeners, AiHarnessRunContext ctx, String reason) {
        fire(listeners, l -> l.onRunCancelled(ctx, reason));
    }

    static void runFailed(List<TraceListener> listeners, AiHarnessRunContext ctx, String reason) {
        fire(listeners, l -> l.onRunFailed(ctx, reason));
    }

    private static void fire(List<TraceListener> listeners, Consumer<TraceListener> event) {
        for (TraceListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException ignored) {
                // Trace hooks are passive and must not change workflow outcome.
            }
        }
    }
}

package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.gateway.GatewayException;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

import java.util.List;
import java.util.Objects;

final class AwaitResponseStep extends Step {

    private final AiModelGateway gateway;
    private final AiHarnessRunContext context;
    private final List<TraceListener> listeners;

    private boolean submitted;

    AwaitResponseStep(AiModelGateway gateway, AiHarnessRunContext context, List<TraceListener> listeners) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.listeners = List.copyOf(listeners);
    }

    @Override
    protected void onEnter(StepContext ctx) {
        try {
            AiModelCall call = gateway.submit(context.currentRequest());
            context.beginModelCall(call);
            submitted = true;
            TraceEvents.requestSubmitted(listeners, context, context.currentRequest(), call.callId());
        } catch (RuntimeException e) {
            submitted = false;
            context.recordSubmissionFailure(e);
            TraceEvents.callFailed(listeners, context, e);
        }
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (!submitted) {
            return StepResult.done();
        }

        AiModelCall call = context.currentCall()
                .orElseThrow(() -> new IllegalStateException("model call was not recorded"));
        try {
            AiModelCallStatus status = call.poll();
            if (status == null) {
                return failCall(new GatewayException("Model call returned null status: " + call.callId()));
            }
            return switch (status) {
                case PENDING -> StepResult.stay();
                case READY -> receiveResponse(call);
                case FAILED -> failCall(callError(call));
                case CANCELLED -> failCall(new GatewayException("Model call was cancelled: " + call.callId()));
            };
        } catch (RuntimeException e) {
            return failCall(e);
        }
    }

    private StepResult receiveResponse(AiModelCall call) {
        AiModelResponse response = call.result();
        context.recordResponse(response);
        TraceEvents.responseReceived(listeners, context, response);
        return StepResult.done();
    }

    private StepResult failCall(Throwable error) {
        context.recordCallFailure(error);
        TraceEvents.callFailed(listeners, context, error);
        return StepResult.done();
    }

    private static Throwable callError(AiModelCall call) {
        Throwable error = call.error();
        return error == null ? new GatewayException("Model call failed: " + call.callId()) : error;
    }
}

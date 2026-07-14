package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.control.AiBudgetContext;
import io.github.flowerjvm.flower.ai.harness.control.AiBudgetDecision;
import io.github.flowerjvm.flower.ai.harness.control.AiHarnessCancelledException;
import io.github.flowerjvm.flower.ai.harness.control.AiResourcePermit;
import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;

import java.util.Objects;
import java.util.Optional;

final class AwaitResponseStep extends Step {

    private final AiModelGateway gateway;
    private final AiHarnessRunContext context;
    private final AiHarnessSpec<?, ?> spec;
    private final AiHarnessClock clock;

    private boolean submitted;
    private AiResourcePermit permit = AiResourcePermit.noop();

    AwaitResponseStep(
            AiModelGateway gateway,
            AiHarnessRunContext context,
            AiHarnessSpec<?, ?> spec,
            AiHarnessClock clock
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected void onEnter(StepContext ctx) {
        submitted = false;
        releasePermit();
        context.markStatus(AiHarnessRunStatus.QUEUED);
        RunStatePersister.save(spec.runStore(), context, clock);
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        Optional<String> cancellation = context.cancellationToken().cancellationReason();
        if (cancellation.isPresent()) {
            return cancelRun(cancellation.get());
        }

        if (!submitted) {
            return trySubmit();
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
                case CANCELLED -> cancelRun("Model call was cancelled: " + call.callId());
            };
        } catch (RuntimeException e) {
            return failCall(e);
        }
    }

    @Override
    protected void onExit(StepContext ctx) {
        releasePermit();
    }

    private StepResult trySubmit() {
        AiModelRequest request = context.currentRequest();
        AiBudgetDecision budget = spec.budgetPolicy().evaluate(
                new AiBudgetContext(context, request, context.attempt() + 1));
        if (!budget.allowed()) {
            String reason = budget.rejectionReason().orElse("AI budget denied request");
            context.markFailed(reason);
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.runFailed(spec.traceListeners(), context, reason);
            return StepResult.fail(new IllegalStateException(reason));
        }

        Optional<AiResourcePermit> acquired = spec.resourceGovernor().tryAcquire(request, context);
        if (acquired.isEmpty()) {
            return StepResult.stay();
        }

        permit = acquired.get();
        try {
            AiModelCall call = gateway.submit(request);
            context.beginModelCall(call);
            context.markStatus(AiHarnessRunStatus.WAITING_PROVIDER);
            submitted = true;
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.requestSubmitted(spec.traceListeners(), context, request, call.callId());
            return StepResult.stay();
        } catch (RuntimeException e) {
            releasePermit();
            submitted = false;
            context.recordSubmissionFailure(e);
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.callFailed(spec.traceListeners(), context, e);
            return StepResult.done();
        }
    }

    private StepResult receiveResponse(AiModelCall call) {
        AiModelResponse response = call.result();
        context.recordResponse(response);
        releasePermit();
        RunStatePersister.save(spec.runStore(), context, clock);
        TraceEvents.responseReceived(spec.traceListeners(), context, response);
        return StepResult.done();
    }

    private StepResult failCall(Throwable error) {
        context.recordCallFailure(error);
        releasePermit();
        RunStatePersister.save(spec.runStore(), context, clock);
        TraceEvents.callFailed(spec.traceListeners(), context, error);
        return StepResult.done();
    }

    private StepResult cancelRun(String reason) {
        context.currentCall().ifPresent(AiModelCall::cancel);
        releasePermit();
        context.markCancelled(reason);
        RunStatePersister.save(spec.runStore(), context, clock);
        TraceEvents.runCancelled(spec.traceListeners(), context, reason);
        return StepResult.fail(new AiHarnessCancelledException(reason));
    }

    private void releasePermit() {
        permit.close();
        permit = AiResourcePermit.noop();
    }

    private static Throwable callError(AiModelCall call) {
        Throwable error = call.error();
        return error == null ? new GatewayException("Model call failed: " + call.callId()) : error;
    }
}

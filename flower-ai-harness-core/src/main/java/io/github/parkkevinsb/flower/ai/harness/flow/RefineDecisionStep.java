package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.refine.RefineContext;
import io.github.parkkevinsb.flower.ai.harness.refine.RefineDecision;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.AiHarnessClock;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

import java.util.Objects;

final class RefineDecisionStep extends Step {

    private final AiHarnessSpec<?, ?> spec;
    private final AiHarnessRunContext context;
    private final String retryStepId;
    private final AiHarnessClock clock;

    private StepResult result;

    RefineDecisionStep(
            AiHarnessSpec<?, ?> spec,
            AiHarnessRunContext context,
            String retryStepId,
            AiHarnessClock clock
    ) {
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.retryStepId = Objects.requireNonNull(retryStepId, "retryStepId must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected void onEnter(StepContext ctx) {
        try {
            context.markStatus(AiHarnessRunStatus.REFINING);
            RunStatePersister.save(spec.runStore(), context, clock);
            RefineDecision decision = spec.refinePolicy().decide(refineContext());
            if (decision == null) {
                fail("Refine policy returned null");
            } else if (decision instanceof RefineDecision.Continue) {
                result = StepResult.done();
            } else if (decision instanceof RefineDecision.Retry retry) {
                retry(retry.nextRequest());
            } else if (decision instanceof RefineDecision.Fail fail) {
                fail(fail.reason());
            } else {
                fail("Unsupported refine decision: " + decision.getClass().getName());
            }
        } catch (RuntimeException e) {
            result = StepResult.fail(e);
            context.markFailed(e.getMessage());
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.runFailed(spec.traceListeners(), context, e.getMessage());
        }
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return result;
    }

    private RefineContext refineContext() {
        AiModelRequest request = context.currentRequest();
        AiModelResponse response = context.latestResponse().orElse(null);
        ValidationResult<?> validation = context.latestValidation().orElse(null);
        Throwable callError = context.latestCallError().orElse(null);
        return new RefineContext(context, request, response, validation, callError, context.attempt(), maxAttempts());
    }

    private int maxAttempts() {
        if (spec.refinePolicy() instanceof MaxAttemptsRefinePolicy policy) {
            return policy.maxAttempts();
        }
        return Integer.MAX_VALUE;
    }

    private void retry(AiModelRequest nextRequest) {
        context.setCurrentRequest(nextRequest);
        context.markStatus(AiHarnessRunStatus.QUEUED);
        RunStatePersister.save(spec.runStore(), context, clock);
        TraceEvents.refineTriggered(spec.traceListeners(), context, nextRequest);
        result = StepResult.goTo(retryStepId);
    }

    private void fail(String reason) {
        context.markFailed(reason);
        RunStatePersister.save(spec.runStore(), context, clock);
        TraceEvents.runFailed(spec.traceListeners(), context, reason);
        result = StepResult.fail(new IllegalStateException(reason));
    }
}

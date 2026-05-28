package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

import java.util.Objects;

final class ValidateResponseStep<T> extends Step {

    private final AiHarnessSpec<?, T> spec;
    private final AiHarnessRunContext context;

    private Throwable failure;

    ValidateResponseStep(AiHarnessSpec<?, T> spec, AiHarnessRunContext context) {
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    @Override
    protected void onEnter(StepContext ctx) {
        if (context.latestCallError().isPresent()) {
            return;
        }

        context.markStatus(AiHarnessRunStatus.VALIDATING);
        RunStatePersister.save(spec.runStore(), context);
        AiModelResponse response = context.latestResponse().orElse(null);
        if (response == null) {
            failure = new IllegalStateException("No model response is available for validation");
            context.markFailed(failure.getMessage());
            RunStatePersister.save(spec.runStore(), context);
            TraceEvents.runFailed(spec.traceListeners(), context, failure.getMessage());
            return;
        }

        try {
            ValidationResult<T> result = spec.validator().validate(response);
            context.recordValidation(result);
            RunStatePersister.save(spec.runStore(), context);
            TraceEvents.validationCompleted(spec.traceListeners(), context, result);
        } catch (RuntimeException e) {
            failure = e;
            context.markFailed(e.getMessage());
            RunStatePersister.save(spec.runStore(), context);
            TraceEvents.runFailed(spec.traceListeners(), context, e.getMessage());
        }
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return failure == null ? StepResult.done() : StepResult.fail(failure);
    }
}

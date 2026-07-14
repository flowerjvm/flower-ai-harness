package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;

import java.util.List;
import java.util.Objects;

final class EmitFindingsStep<T> extends Step {

    private final AiHarnessSpec<?, T> spec;
    private final AiHarnessRunContext context;
    private final AiHarnessClock clock;

    private Throwable failure;

    EmitFindingsStep(AiHarnessSpec<?, T> spec, AiHarnessRunContext context, AiHarnessClock clock) {
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected void onEnter(StepContext ctx) {
        try {
            context.markStatus(AiHarnessRunStatus.EMITTING_FINDINGS);
            RunStatePersister.save(spec.runStore(), context, clock);
            T value = validValue();
            List<AiFinding> findings = spec.findingExtractor().extract(value, context);
            context.recordFindings(findings);
            spec.findingSink().accept(context.latestFindings(), context);
            context.markSucceeded();
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.runCompleted(spec.traceListeners(), context, context.latestFindings());
        } catch (RuntimeException e) {
            failure = e;
            context.markFailed(e.getMessage());
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.runFailed(spec.traceListeners(), context, e.getMessage());
        }
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return failure == null ? StepResult.done() : StepResult.fail(failure);
    }

    @SuppressWarnings("unchecked")
    private T validValue() {
        ValidationResult<?> validation = context.latestValidation().orElse(null);
        if (validation instanceof ValidationResult.Valid<?> valid) {
            return (T) valid.value();
        }
        throw new IllegalStateException("Cannot emit findings without a valid structured result");
    }
}

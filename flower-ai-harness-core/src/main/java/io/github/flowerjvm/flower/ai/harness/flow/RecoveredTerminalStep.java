package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.control.AiHarnessCancelledException;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;

import java.util.Objects;

final class RecoveredTerminalStep extends Step {

    enum Outcome {
        FAIL_RECOVERABLE,
        CANCELLED
    }

    private final AiHarnessSpec<?, ?> spec;
    private final AiHarnessRunContext context;
    private final Outcome outcome;
    private final String reason;
    private final AiHarnessClock clock;

    RecoveredTerminalStep(
            AiHarnessSpec<?, ?> spec,
            AiHarnessRunContext context,
            Outcome outcome,
            String reason,
            AiHarnessClock clock
    ) {
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.reason = requireText(reason, "reason");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected void onEnter(StepContext ctx) {
        if (outcome == Outcome.CANCELLED) {
            context.markCancelled(reason);
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.runCancelled(spec.traceListeners(), context, reason);
        } else {
            context.markFailed(reason);
            RunStatePersister.save(spec.runStore(), context, clock);
            TraceEvents.runFailed(spec.traceListeners(), context, reason);
        }
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (outcome == Outcome.CANCELLED) {
            return StepResult.fail(new AiHarnessCancelledException(reason));
        }
        return StepResult.fail(new IllegalStateException(reason));
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}

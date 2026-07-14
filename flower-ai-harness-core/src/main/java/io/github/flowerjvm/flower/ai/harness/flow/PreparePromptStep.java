package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;

import java.time.Duration;
import java.util.Objects;

final class PreparePromptStep<I> extends Step {

    private final I input;
    private final AiHarnessSpec<I, ?> spec;
    private final AiHarnessRunContext context;
    private final ModelId modelId;
    private final ProviderOptions options;
    private final Duration timeout;
    private final AiHarnessClock clock;

    private Throwable failure;

    PreparePromptStep(
            I input,
            AiHarnessSpec<I, ?> spec,
            AiHarnessRunContext context,
            ModelId modelId,
            ProviderOptions options,
            Duration timeout,
            AiHarnessClock clock
    ) {
        this.input = Objects.requireNonNull(input, "input must not be null");
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.modelId = Objects.requireNonNull(modelId, "modelId must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    protected void onEnter(StepContext ctx) {
        context.markStatus(AiHarnessRunStatus.PREPARING_PROMPT);
        RunStatePersister.save(spec.runStore(), context, clock);
        TraceEvents.runStarted(spec.traceListeners(), context);
        try {
            RenderedPrompt prompt = spec.promptBuilder().build(input, context);
            context.setCurrentRequest(new AiModelRequest(modelId, prompt, options, timeout));
            context.markStatus(AiHarnessRunStatus.QUEUED);
            RunStatePersister.save(spec.runStore(), context, clock);
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
}

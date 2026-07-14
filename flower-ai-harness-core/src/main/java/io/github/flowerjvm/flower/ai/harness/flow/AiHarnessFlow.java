package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.core.flow.Flow;

import java.util.Objects;

/**
 * A runnable Flower flow plus its harness-level run context.
 */
public record AiHarnessFlow(Flow flow, AiHarnessRunContext context) {

    public AiHarnessFlow {
        Objects.requireNonNull(flow, "flow must not be null");
        Objects.requireNonNull(context, "context must not be null");
    }
}

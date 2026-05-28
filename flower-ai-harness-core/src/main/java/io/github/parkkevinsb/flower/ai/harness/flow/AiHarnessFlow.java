package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.core.flow.Flow;

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

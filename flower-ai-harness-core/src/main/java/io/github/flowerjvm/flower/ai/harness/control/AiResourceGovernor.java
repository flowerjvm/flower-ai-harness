package io.github.flowerjvm.flower.ai.harness.control;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.Optional;

/**
 * Non-blocking resource gate for provider calls.
 */
@FunctionalInterface
public interface AiResourceGovernor {

    AiResourceGovernor NONE = (request, context) -> Optional.of(AiResourcePermit.noop());

    Optional<AiResourcePermit> tryAcquire(AiModelRequest request, AiHarnessRunContext context);

    static AiResourceGovernor none() {
        return NONE;
    }
}

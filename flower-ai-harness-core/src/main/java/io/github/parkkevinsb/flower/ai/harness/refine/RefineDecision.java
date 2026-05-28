package io.github.parkkevinsb.flower.ai.harness.refine;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;

import java.util.Objects;

/**
 * Next lifecycle action selected by an {@link AiRefinePolicy}.
 */
public sealed interface RefineDecision permits RefineDecision.Continue, RefineDecision.Retry, RefineDecision.Fail {

    record Continue() implements RefineDecision {
    }

    record Retry(AiModelRequest nextRequest) implements RefineDecision {

        public Retry {
            Objects.requireNonNull(nextRequest, "nextRequest must not be null");
        }
    }

    record Fail(String reason) implements RefineDecision {

        public Fail {
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.trim().isEmpty()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }
}

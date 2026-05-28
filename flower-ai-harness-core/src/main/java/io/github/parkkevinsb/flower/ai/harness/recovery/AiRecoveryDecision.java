package io.github.parkkevinsb.flower.ai.harness.recovery;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;

import java.util.Objects;

/**
 * Recovery action selected from a persisted harness snapshot.
 */
public sealed interface AiRecoveryDecision permits
        AiRecoveryDecision.RetryCurrentRequest,
        AiRecoveryDecision.ContinueFromFlow,
        AiRecoveryDecision.FailRecoverable,
        AiRecoveryDecision.MarkCancelled {

    record RetryCurrentRequest(AiModelRequest request) implements AiRecoveryDecision {

        public RetryCurrentRequest {
            Objects.requireNonNull(request, "request must not be null");
        }
    }

    record ContinueFromFlow() implements AiRecoveryDecision {
    }

    record FailRecoverable(String reason) implements AiRecoveryDecision {

        public FailRecoverable {
            reason = requireReason(reason);
        }
    }

    record MarkCancelled(String reason) implements AiRecoveryDecision {

        public MarkCancelled {
            reason = requireReason(reason);
        }
    }

    private static String requireReason(String value) {
        Objects.requireNonNull(value, "reason must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return trimmed;
    }
}

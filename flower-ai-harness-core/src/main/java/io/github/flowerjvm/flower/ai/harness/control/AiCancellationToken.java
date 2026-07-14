package io.github.flowerjvm.flower.ai.harness.control;

import java.util.Optional;

/**
 * Per-run cancellation signal.
 */
public interface AiCancellationToken {

    AiCancellationToken NONE = () -> Optional.empty();

    Optional<String> cancellationReason();

    default boolean isCancellationRequested() {
        return cancellationReason().isPresent();
    }

    static AiCancellationToken none() {
        return NONE;
    }
}

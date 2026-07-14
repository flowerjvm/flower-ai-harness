package io.github.flowerjvm.flower.ai.harness.control;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable cancellation token controlled by the host application.
 */
public final class ManualAiCancellationToken implements AiCancellationToken {

    private final AtomicReference<String> reason = new AtomicReference<>();

    @Override
    public Optional<String> cancellationReason() {
        return Optional.ofNullable(reason.get());
    }

    public boolean cancel(String value) {
        Objects.requireNonNull(value, "value must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("cancellation reason must not be blank");
        }
        return reason.compareAndSet(null, trimmed);
    }
}

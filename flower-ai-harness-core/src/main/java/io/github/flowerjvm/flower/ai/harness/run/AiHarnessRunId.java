package io.github.flowerjvm.flower.ai.harness.run;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identity for one harness run.
 */
public record AiHarnessRunId(String value) {

    public AiHarnessRunId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.trim();
    }

    public static AiHarnessRunId random() {
        return new AiHarnessRunId(UUID.randomUUID().toString());
    }
}

package io.github.flowerjvm.flower.ai.harness.recovery;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;

import java.util.Objects;

/**
 * Inputs available to recovery policy when rebuilding a harness flow.
 */
public record AiRecoveryContext(
        AiHarnessRunSnapshot snapshot,
        AiHarnessSpec<?, ?> spec
) {

    public AiRecoveryContext {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
    }
}

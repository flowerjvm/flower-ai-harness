package io.github.flowerjvm.flower.ai.harness.spi;

import java.time.Instant;

/**
 * Small time abstraction for run metadata and deterministic tests.
 */
public interface AiHarnessClock {

    Instant now();

    static AiHarnessClock system() {
        return Instant::now;
    }
}

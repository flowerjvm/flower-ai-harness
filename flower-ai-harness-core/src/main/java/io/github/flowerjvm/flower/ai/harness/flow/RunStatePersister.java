package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStore;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;

import java.util.Objects;

final class RunStatePersister {

    private RunStatePersister() {
    }

    static void save(AiHarnessRunStore store, AiHarnessRunContext context, AiHarnessClock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        store.save(AiHarnessRunSnapshot.from(context, clock.now()));
    }
}

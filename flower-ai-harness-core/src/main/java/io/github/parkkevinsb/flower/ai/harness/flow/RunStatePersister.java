package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spi.AiHarnessClock;

import java.util.Objects;

final class RunStatePersister {

    private RunStatePersister() {
    }

    static void save(AiHarnessRunStore store, AiHarnessRunContext context, AiHarnessClock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        store.save(AiHarnessRunSnapshot.from(context, clock.now()));
    }
}

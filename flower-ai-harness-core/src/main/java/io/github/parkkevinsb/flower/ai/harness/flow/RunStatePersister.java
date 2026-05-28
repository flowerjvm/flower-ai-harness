package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;

import java.time.Instant;

final class RunStatePersister {

    private RunStatePersister() {
    }

    static void save(AiHarnessRunStore store, AiHarnessRunContext context) {
        store.save(AiHarnessRunSnapshot.from(context, Instant.now()));
    }
}

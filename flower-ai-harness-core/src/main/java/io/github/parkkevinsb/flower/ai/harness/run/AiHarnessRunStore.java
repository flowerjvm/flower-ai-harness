package io.github.parkkevinsb.flower.ai.harness.run;

import java.util.Optional;

/**
 * Storage boundary for AI harness run state.
 */
public interface AiHarnessRunStore {

    AiHarnessRunStore NOOP = new AiHarnessRunStore() {
        @Override
        public void save(AiHarnessRunSnapshot snapshot) {
        }

        @Override
        public Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId) {
            return Optional.empty();
        }
    };

    void save(AiHarnessRunSnapshot snapshot);

    Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId);

    static AiHarnessRunStore noop() {
        return NOOP;
    }
}

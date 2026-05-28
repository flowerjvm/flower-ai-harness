package io.github.parkkevinsb.flower.ai.harness.run;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory run store useful for tests and small demos.
 */
public final class InMemoryAiHarnessRunStore implements AiHarnessRunStore {

    private final Map<AiHarnessRunId, AiHarnessRunSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(AiHarnessRunSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        snapshots.put(snapshot.runId(), snapshot);
    }

    @Override
    public Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        return Optional.ofNullable(snapshots.get(runId));
    }

    public int size() {
        return snapshots.size();
    }
}

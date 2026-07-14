package io.github.flowerjvm.flower.ai.harness.test.time;

import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic harness clock for fake provider tests.
 */
public final class FixedAiHarnessClock implements AiHarnessClock {

    private Instant instant;
    private long tickCount;

    public FixedAiHarnessClock() {
        this(Instant.EPOCH);
    }

    public FixedAiHarnessClock(Instant instant) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
    }

    @Override
    public Instant now() {
        return instant;
    }

    public Instant instant() {
        return instant;
    }

    public long tickCount() {
        return tickCount;
    }

    public void tick() {
        tickBy(1);
    }

    public void tickBy(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        tickCount += ticks;
        instant = instant.plusMillis(ticks);
    }

    public void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        instant = instant.plus(duration);
    }
}

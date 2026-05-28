package io.github.parkkevinsb.flower.ai.harness.test.fake;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic response behavior for one fake model call.
 */
public sealed interface FakeResponseProgram permits
        FakeResponseProgram.ImmediateText,
        FakeResponseProgram.ImmediateError,
        FakeResponseProgram.DelayedText,
        FakeResponseProgram.DelayedError,
        FakeResponseProgram.Sequence {

    record ImmediateText(String text) implements FakeResponseProgram {

        public ImmediateText {
            Objects.requireNonNull(text, "text must not be null");
        }
    }

    record ImmediateError(Throwable error) implements FakeResponseProgram {

        public ImmediateError {
            Objects.requireNonNull(error, "error must not be null");
        }
    }

    record DelayedText(String text, int ticksUntilReady) implements FakeResponseProgram {

        public DelayedText {
            Objects.requireNonNull(text, "text must not be null");
            if (ticksUntilReady < 0) {
                throw new IllegalArgumentException("ticksUntilReady must not be negative");
            }
        }
    }

    record DelayedError(Throwable error, int ticksUntilFailed) implements FakeResponseProgram {

        public DelayedError {
            Objects.requireNonNull(error, "error must not be null");
            if (ticksUntilFailed < 0) {
                throw new IllegalArgumentException("ticksUntilFailed must not be negative");
            }
        }
    }

    record Sequence(List<FakeResponseProgram> steps) implements FakeResponseProgram {

        public Sequence {
            Objects.requireNonNull(steps, "steps must not be null");
            steps = List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("steps must not be empty");
            }
        }
    }
}

package io.github.flowerjvm.flower.ai.harness.control;

/**
 * Lease for a model-call resource slot.
 */
@FunctionalInterface
public interface AiResourcePermit extends AutoCloseable {

    AiResourcePermit NOOP = () -> {
    };

    @Override
    void close();

    static AiResourcePermit noop() {
        return NOOP;
    }
}

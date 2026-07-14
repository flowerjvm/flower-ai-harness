package io.github.flowerjvm.flower.ai.harness.control;

/**
 * Exception used to terminate a Flower flow when the harness run is cancelled.
 */
public final class AiHarnessCancelledException extends RuntimeException {

    public AiHarnessCancelledException(String message) {
        super(message);
    }
}

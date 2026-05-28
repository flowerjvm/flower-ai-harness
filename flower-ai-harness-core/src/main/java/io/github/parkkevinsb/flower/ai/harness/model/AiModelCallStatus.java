package io.github.parkkevinsb.flower.ai.harness.model;

/**
 * Non-blocking model call state observed by Flower steps.
 */
public enum AiModelCallStatus {
    PENDING,
    READY,
    FAILED,
    CANCELLED
}

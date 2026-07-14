package io.github.flowerjvm.flower.ai.harness.model;

/**
 * Non-blocking model call state observed by Flower steps.
 */
public enum AiModelCallStatus {
    PENDING,
    READY,
    FAILED,
    CANCELLED
}

package io.github.flowerjvm.flower.ai.harness.run;

/**
 * Domain-level status of an AI harness run.
 *
 * <p>This is intentionally separate from Flower's flow state. Flower owns
 * orchestration state; the harness owns AI-run state.
 */
public enum AiHarnessRunStatus {

    QUEUED,
    PREPARING_PROMPT,
    WAITING_PROVIDER,
    VALIDATING,
    REFINING,
    EMITTING_FINDINGS,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

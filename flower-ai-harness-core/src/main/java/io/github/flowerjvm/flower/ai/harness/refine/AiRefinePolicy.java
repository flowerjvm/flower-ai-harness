package io.github.flowerjvm.flower.ai.harness.refine;

/**
 * Decides whether a harness run should continue, retry/refine, or fail.
 */
public interface AiRefinePolicy {

    RefineDecision decide(RefineContext ctx);
}

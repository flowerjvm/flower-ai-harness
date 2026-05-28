package io.github.parkkevinsb.flower.ai.harness.control;

/**
 * Budget guard that caps the total number of provider submissions.
 */
public final class MaxAttemptsBudgetPolicy implements AiBudgetPolicy {

    private final int maxAttempts;

    public MaxAttemptsBudgetPolicy(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public AiBudgetDecision evaluate(AiBudgetContext context) {
        if (context.nextAttempt() > maxAttempts) {
            return AiBudgetDecision.reject("AI budget exhausted: maxAttempts=" + maxAttempts);
        }
        return AiBudgetDecision.allow();
    }
}

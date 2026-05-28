package io.github.parkkevinsb.flower.ai.harness.control;

/**
 * Policy that decides whether a harness run may spend another model call.
 */
@FunctionalInterface
public interface AiBudgetPolicy {

    AiBudgetPolicy NO_LIMIT = context -> AiBudgetDecision.allow();

    AiBudgetDecision evaluate(AiBudgetContext context);

    static AiBudgetPolicy noLimit() {
        return NO_LIMIT;
    }
}

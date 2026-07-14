package io.github.flowerjvm.flower.ai.harness.control;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of checking whether another provider call is allowed.
 */
public record AiBudgetDecision(
        boolean allowed,
        Optional<String> rejectionReason
) {

    public AiBudgetDecision {
        rejectionReason = rejectionReason == null ? Optional.empty() : rejectionReason;
        if (!allowed && rejectionReason.isEmpty()) {
            throw new IllegalArgumentException("rejectionReason is required when budget is denied");
        }
    }

    public static AiBudgetDecision allow() {
        return new AiBudgetDecision(true, Optional.empty());
    }

    public static AiBudgetDecision reject(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new AiBudgetDecision(false, Optional.of(trimmed));
    }
}

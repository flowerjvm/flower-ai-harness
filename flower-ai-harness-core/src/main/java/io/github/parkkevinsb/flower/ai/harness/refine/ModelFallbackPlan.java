package io.github.parkkevinsb.flower.ai.harness.refine;

import io.github.parkkevinsb.flower.ai.harness.model.ModelId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ordered attempt budget for retrying across one or more models.
 */
public final class ModelFallbackPlan {

    private final List<ModelAttemptBudget> budgets;
    private final List<ModelId> attemptModels;

    private ModelFallbackPlan(List<ModelAttemptBudget> budgets) {
        this.budgets = List.copyOf(budgets);
        List<ModelId> expanded = new ArrayList<>();
        for (ModelAttemptBudget budget : budgets) {
            for (int i = 0; i < budget.maxAttempts(); i++) {
                expanded.add(budget.modelId());
            }
        }
        this.attemptModels = List.copyOf(expanded);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ModelFallbackPlan single(ModelId modelId, int maxAttempts) {
        return builder().model(modelId, maxAttempts).build();
    }

    public List<ModelAttemptBudget> budgets() {
        return budgets;
    }

    public int maxAttempts() {
        return attemptModels.size();
    }

    public ModelId modelForAttempt(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
        if (attempt > attemptModels.size()) {
            throw new IllegalArgumentException("attempt exceeds plan maxAttempts: " + attempt);
        }
        return attemptModels.get(attempt - 1);
    }

    public record ModelAttemptBudget(ModelId modelId, int maxAttempts) {

        public ModelAttemptBudget {
            Objects.requireNonNull(modelId, "modelId must not be null");
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }
        }
    }

    public static final class Builder {

        private final List<ModelAttemptBudget> budgets = new ArrayList<>();

        private Builder() {
        }

        public Builder model(ModelId modelId, int maxAttempts) {
            budgets.add(new ModelAttemptBudget(modelId, maxAttempts));
            return this;
        }

        public ModelFallbackPlan build() {
            if (budgets.isEmpty()) {
                throw new IllegalStateException("ModelFallbackPlan requires at least one model");
            }
            return new ModelFallbackPlan(budgets);
        }
    }
}

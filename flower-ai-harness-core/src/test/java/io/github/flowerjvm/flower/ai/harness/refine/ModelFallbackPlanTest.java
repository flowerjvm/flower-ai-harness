package io.github.flowerjvm.flower.ai.harness.refine;

import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFallbackPlanTest {

    @Test
    void expandsModelBudgetsIntoAttemptOrder() {
        ModelId cheap = ModelId.parse("spring-ai:cheap");
        ModelId strong = ModelId.parse("spring-ai:strong");
        ModelFallbackPlan plan = ModelFallbackPlan.builder()
                .model(cheap, 2)
                .model(strong, 1)
                .build();

        assertThat(plan.maxAttempts()).isEqualTo(3);
        assertThat(plan.modelForAttempt(1)).isEqualTo(cheap);
        assertThat(plan.modelForAttempt(2)).isEqualTo(cheap);
        assertThat(plan.modelForAttempt(3)).isEqualTo(strong);
        assertThat(plan.budgets()).hasSize(2);
    }

    @Test
    void rejectsInvalidPlans() {
        ModelId model = ModelId.parse("spring-ai:model");

        assertThatThrownBy(() -> ModelFallbackPlan.builder().build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ModelFallbackPlan.builder().model(model, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelFallbackPlan.single(model, 1).modelForAttempt(2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

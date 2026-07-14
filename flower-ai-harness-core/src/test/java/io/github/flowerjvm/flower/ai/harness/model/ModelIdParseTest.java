package io.github.flowerjvm.flower.ai.harness.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelIdParseTest {

    @Test
    void parsesProviderAndName() {
        ModelId id = ModelId.parse("openai:gpt-4o");

        assertThat(id.provider()).isEqualTo("openai");
        assertThat(id.name()).isEqualTo("gpt-4o");
    }

    @Test
    void roundTripsAsString() {
        ModelId id = new ModelId("anthropic", "claude-sonnet");

        assertThat(ModelId.parse(id.asString())).isEqualTo(id);
    }

    @Test
    void trimsProviderAndName() {
        ModelId id = ModelId.parse(" openai : gpt-4o ");

        assertThat(id.provider()).isEqualTo("openai");
        assertThat(id.name()).isEqualTo("gpt-4o");
    }

    @Test
    void rejectsInvalidSpecs() {
        assertThatThrownBy(() -> ModelId.parse(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModelId.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelId.parse("openai"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelId.parse(":gpt-4o"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelId.parse("openai:"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

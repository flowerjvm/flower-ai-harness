package io.github.parkkevinsb.flower.ai.harness.spec;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.model.ProviderOptions;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.refine.RefineDecision;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiHarnessSpecBuilderTest {

    @Test
    void buildsImmutableSpecWithRequiredFields() {
        AiHarnessSpec<String, String> spec = validBuilder()
                .defaultOptions(ProviderOptions.empty().with("temperature", 0.1))
                .defaultTimeout(Duration.ofSeconds(3))
                .build();

        assertThat(spec.harnessId()).isEqualTo("text-review");
        assertThat(spec.defaultModelId()).isEqualTo(ModelId.parse("fake:model"));
        assertThat(spec.defaultTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(spec.defaultOptions().get("temperature")).contains(0.1);
        assertThat(spec.traceListeners()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> AiHarnessSpec.<String, String>builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("harnessId");
    }

    @Test
    void rejectsBlankHarnessId() {
        assertThatThrownBy(() -> validBuilder().harnessId(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("harnessId");
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> validBuilder().defaultTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTimeout");
    }

    private static AiHarnessSpec.Builder<String, String> validBuilder() {
        PromptVersion version = new PromptVersion("text-review", "1.0.0");
        return AiHarnessSpec.<String, String>builder()
                .harnessId("text-review")
                .defaultModelId(ModelId.parse("fake:model"))
                .promptVersion(version)
                .promptBuilder((input, ctx) -> new RenderedPrompt(
                        List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, input)),
                        version))
                .validator(response -> new ValidationResult.Valid<>(response.rawText()))
                .refinePolicy(ctx -> new RefineDecision.Continue())
                .findingExtractor((value, ctx) -> List.of(
                        AiFinding.of("TEXT", AiFindingSeverity.INFO, value)))
                .findingSink((findings, ctx) -> {
                });
    }
}

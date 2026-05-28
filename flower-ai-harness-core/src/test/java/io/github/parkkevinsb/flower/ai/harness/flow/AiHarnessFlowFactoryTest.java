package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.refine.RefineDecision;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.step.StepDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiHarnessFlowFactoryTest {

    private static final AiHarnessRunContext.AttributeKey<String> TENANT =
            AiHarnessRunContext.AttributeKey.of("tenant", String.class);

    @Test
    void createsFlowerFlowWithHarnessStepLayoutAndContext() {
        AiHarnessFlowFactory<String, Review> factory = new AiHarnessFlowFactory<>(
                unusedGateway(),
                spec(),
                () -> Instant.EPOCH);
        AiHarnessFlowFactory.RunOverrides overrides = AiHarnessFlowFactory.RunOverrides.builder()
                .modelId(ModelId.parse("fake:larger"))
                .timeout(Duration.ofSeconds(10))
                .attribute(TENANT, "acme")
                .build();

        AiHarnessFlow harnessFlow = factory.createFlow("read me", overrides);

        assertThat(harnessFlow.flow().flowId().flowType()).isEqualTo("text-review");
        assertThat(harnessFlow.flow().flowId().flowKey()).isEqualTo(harnessFlow.context().runId().value());
        assertThat(harnessFlow.flow().definitionVersion()).isEqualTo("text-review@1.0.0");
        assertThat(harnessFlow.context().startedAt()).isEqualTo(Instant.EPOCH);
        assertThat(harnessFlow.context().attribute(TENANT)).contains("acme");
        assertThat(harnessFlow.flow().steps())
                .extracting(StepDefinition::stepId)
                .containsExactly(
                        AiHarnessFlowFactory.PREPARE_PROMPT_STEP,
                        AiHarnessFlowFactory.AWAIT_RESPONSE_STEP,
                        AiHarnessFlowFactory.VALIDATE_RESPONSE_STEP,
                        AiHarnessFlowFactory.REFINE_DECISION_STEP,
                        AiHarnessFlowFactory.EMIT_FINDINGS_STEP);
        assertThat(harnessFlow.flow().steps())
                .allSatisfy(step -> assertThat(Modifier.isPublic(step.step().getClass().getModifiers()))
                        .isFalse());
    }

    private static AiHarnessSpec<String, Review> spec() {
        PromptVersion version = new PromptVersion("text-review", "1.0.0");
        return AiHarnessSpec.<String, Review>builder()
                .harnessId("text-review")
                .defaultModelId(ModelId.parse("fake:model"))
                .promptVersion(version)
                .promptBuilder((input, ctx) -> new RenderedPrompt(
                        List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, input)),
                        ctx.promptVersion()))
                .validator(response -> new ValidationResult.Valid<>(new Review(response.rawText())))
                .refinePolicy(ctx -> new RefineDecision.Continue())
                .findingExtractor((review, ctx) -> List.of(
                        AiFinding.of("REVIEW", AiFindingSeverity.INFO, review.summary())))
                .findingSink((findings, ctx) -> {
                })
                .build();
    }

    private static AiModelGateway unusedGateway() {
        return request -> {
            throw new AssertionError("gateway should not be called while only constructing the flow");
        };
    }

    private record Review(String summary) {
    }
}

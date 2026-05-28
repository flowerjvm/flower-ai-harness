package io.github.parkkevinsb.flower.ai.harness.refine;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationError;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelFallbackRefinePolicyTest {

    private static final ModelId CHEAP = ModelId.parse("spring-ai:cheap");
    private static final ModelId STRONG = ModelId.parse("spring-ai:strong");

    @Test
    void continuesWhenValidationIsValid() {
        ModelFallbackRefinePolicy policy = policy();
        RefineContext ctx = context(request(CHEAP), new ValidationResult.Valid<>("ok"), null, 1);

        assertThat(policy.decide(ctx)).isInstanceOf(RefineDecision.Continue.class);
    }

    @Test
    void retriesTransportFailureWithNextFallbackModel() {
        ModelFallbackRefinePolicy policy = policy();
        AiModelRequest request = request(CHEAP);
        RefineContext ctx = context(request, null, new RuntimeException("provider down"), 1);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Retry.class);
        AiModelRequest next = ((RefineDecision.Retry) decision).nextRequest();
        assertThat(next.modelId()).isEqualTo(STRONG);
        assertThat(next.prompt()).isSameAs(request.prompt());
    }

    @Test
    void retriesValidationFailureWithErrorsAndNextFallbackModel() {
        ModelFallbackRefinePolicy policy = policy();
        AiModelRequest request = request(CHEAP);
        RefineContext ctx = context(
                request,
                new ValidationResult.Invalid<>(List.of(
                        new ValidationError("$.issues", "MISSING_FIELD", "issues is required"))),
                null,
                1);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Retry.class);
        AiModelRequest next = ((RefineDecision.Retry) decision).nextRequest();
        assertThat(next.modelId()).isEqualTo(STRONG);
        assertThat(next.prompt().messages()).hasSize(2);
        assertThat(next.prompt().messages().get(1).content())
                .contains("MISSING_FIELD", "issues is required");
    }

    @Test
    void failsWhenPlanAttemptsAreExhausted() {
        ModelFallbackRefinePolicy policy = policy();
        RefineContext ctx = context(
                request(STRONG),
                new ValidationResult.Invalid<>(List.of(
                        new ValidationError("", "MALFORMED_JSON", "bad json"))),
                null,
                2);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Fail.class);
        assertThat(((RefineDecision.Fail) decision).reason())
                .contains("Validation failed after 2 attempts");
    }

    private static ModelFallbackRefinePolicy policy() {
        return new ModelFallbackRefinePolicy(ModelFallbackPlan.builder()
                .model(CHEAP, 1)
                .model(STRONG, 1)
                .build());
    }

    private static AiModelRequest request(ModelId modelId) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Return JSON")),
                new PromptVersion("test", "1.0.0"));
        return new AiModelRequest(modelId, prompt, null, Duration.ofSeconds(5));
    }

    private static RefineContext context(
            AiModelRequest request,
            ValidationResult<?> validation,
            Throwable error,
            int attempt
    ) {
        AiHarnessRunContext run = new AiHarnessRunContext(
                new AiHarnessRunId("run-" + attempt),
                "test-harness",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);
        return new RefineContext(run, request, null, validation, error, attempt, 2);
    }
}

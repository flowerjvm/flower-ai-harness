package io.github.flowerjvm.flower.ai.harness.refine;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunId;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationError;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaxAttemptsRefinePolicyTest {

    @Test
    void continuesWhenValidationIsValid() {
        MaxAttemptsRefinePolicy policy = new MaxAttemptsRefinePolicy(3);
        RefineContext ctx = context(
                request(),
                null,
                new ValidationResult.Valid<>("ok"),
                null,
                1,
                3);

        assertThat(policy.decide(ctx)).isInstanceOf(RefineDecision.Continue.class);
    }

    @Test
    void retriesInvalidResponseWithValidationErrorsAppendedToPrompt() {
        MaxAttemptsRefinePolicy policy = new MaxAttemptsRefinePolicy(3);
        AiModelRequest request = request();
        RefineContext ctx = context(
                request,
                null,
                new ValidationResult.Invalid<>(List.of(
                        new ValidationError("$.items", "MISSING_FIELD", "items is required"))),
                null,
                1,
                3);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Retry.class);
        AiModelRequest next = ((RefineDecision.Retry) decision).nextRequest();
        assertThat(next).isNotSameAs(request);
        assertThat(next.prompt().messages()).hasSize(2);
        RenderedPrompt.Message appended = next.prompt().messages().get(1);
        assertThat(appended.role()).isEqualTo(RenderedPrompt.Role.SYSTEM);
        assertThat(appended.content()).contains("MISSING_FIELD", "items is required");
    }

    @Test
    void failsInvalidResponseWhenAttemptsAreExhausted() {
        MaxAttemptsRefinePolicy policy = new MaxAttemptsRefinePolicy(2);
        RefineContext ctx = context(
                request(),
                null,
                new ValidationResult.Invalid<>(List.of(
                        new ValidationError("", "MALFORMED_JSON", "bad json"))),
                null,
                2,
                2);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Fail.class);
        assertThat(((RefineDecision.Fail) decision).reason()).contains("Validation failed");
    }

    @Test
    void retriesTransportFailureWithSameRequest() {
        MaxAttemptsRefinePolicy policy = new MaxAttemptsRefinePolicy(3);
        AiModelRequest request = request();
        RuntimeException error = new RuntimeException("timeout");
        RefineContext ctx = context(request, null, null, error, 1, 3);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Retry.class);
        assertThat(((RefineDecision.Retry) decision).nextRequest()).isSameAs(request);
    }

    @Test
    void failsTransportFailureWhenAttemptsAreExhausted() {
        MaxAttemptsRefinePolicy policy = new MaxAttemptsRefinePolicy(2);
        RuntimeException error = new RuntimeException("timeout");
        RefineContext ctx = context(request(), null, null, error, 2, 2);

        RefineDecision decision = policy.decide(ctx);

        assertThat(decision).isInstanceOf(RefineDecision.Fail.class);
        assertThat(((RefineDecision.Fail) decision).reason()).contains("timeout");
    }

    @Test
    void rejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new MaxAttemptsRefinePolicy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiModelRequest request() {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Return JSON")),
                new PromptVersion("test", "1.0.0"));
        return new AiModelRequest(ModelId.parse("fake:model"), prompt, null, Duration.ofSeconds(5));
    }

    private static RefineContext context(
            AiModelRequest request,
            io.github.flowerjvm.flower.ai.harness.model.AiModelResponse response,
            ValidationResult<?> validation,
            Throwable error,
            int attempt,
            int maxAttempts
    ) {
        AiHarnessRunContext run = new AiHarnessRunContext(
                new AiHarnessRunId("run-" + attempt),
                "test-harness",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);
        return new RefineContext(run, request, response, validation, error, attempt, maxAttempts);
    }
}

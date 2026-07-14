package io.github.flowerjvm.flower.ai.harness.refine;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationError;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simple retry/refine policy bounded by a maximum number of attempts.
 */
public final class MaxAttemptsRefinePolicy implements AiRefinePolicy {

    private final int maxAttempts;

    public MaxAttemptsRefinePolicy(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public RefineDecision decide(RefineContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (ctx.hasValidValidation()) {
            return new RefineDecision.Continue();
        }
        if (ctx.attempt() >= maxAttempts) {
            return new RefineDecision.Fail(failureReason(ctx));
        }
        if (ctx.hasCallError()) {
            return new RefineDecision.Retry(ctx.lastRequest());
        }
        if (ctx.lastValidation() instanceof ValidationResult.Invalid<?> invalid) {
            return new RefineDecision.Retry(withValidationErrors(ctx.lastRequest(), invalid.errors()));
        }
        return new RefineDecision.Fail("No successful validation result is available");
    }

    private static AiModelRequest withValidationErrors(AiModelRequest request, List<ValidationError> errors) {
        List<RenderedPrompt.Message> messages = new ArrayList<>(request.prompt().messages());
        messages.add(new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, formatValidationErrors(errors)));
        RenderedPrompt prompt = new RenderedPrompt(messages, request.prompt().version());
        return request.withPrompt(prompt);
    }

    private static String formatValidationErrors(List<ValidationError> errors) {
        StringBuilder builder = new StringBuilder();
        builder.append("The previous response did not match the required structure. ");
        builder.append("Return corrected output only.\nValidation errors:");
        for (ValidationError error : errors) {
            builder.append("\n- ");
            if (!error.path().isBlank()) {
                builder.append(error.path()).append(" ");
            }
            builder.append("[")
                    .append(error.code())
                    .append("] ")
                    .append(error.message());
        }
        return builder.toString();
    }

    private static String failureReason(RefineContext ctx) {
        if (ctx.hasCallError()) {
            return "Model call failed after " + ctx.attempt() + " attempts: " + ctx.callError().getMessage();
        }
        if (ctx.hasInvalidValidation()) {
            return "Validation failed after " + ctx.attempt() + " attempts";
        }
        return "Refine attempts exhausted after " + ctx.attempt() + " attempts";
    }
}

package io.github.flowerjvm.flower.ai.harness.refine;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationError;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Retry/refine policy that can switch models according to a fallback plan.
 */
public final class ModelFallbackRefinePolicy implements AiRefinePolicy {

    private final ModelFallbackPlan plan;

    public ModelFallbackRefinePolicy(ModelFallbackPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
    }

    public ModelFallbackPlan plan() {
        return plan;
    }

    @Override
    public RefineDecision decide(RefineContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (ctx.hasValidValidation()) {
            return new RefineDecision.Continue();
        }
        if (ctx.attempt() >= plan.maxAttempts()) {
            return new RefineDecision.Fail(failureReason(ctx));
        }
        AiModelRequest nextRequest = ctx.lastRequest().withModelId(plan.modelForAttempt(ctx.attempt() + 1));
        if (ctx.hasCallError()) {
            return new RefineDecision.Retry(nextRequest);
        }
        if (ctx.lastValidation() instanceof ValidationResult.Invalid<?> invalid) {
            return new RefineDecision.Retry(withValidationErrors(nextRequest, invalid.errors()));
        }
        return new RefineDecision.Fail("No successful validation result is available");
    }

    private AiModelRequest withValidationErrors(AiModelRequest request, List<ValidationError> errors) {
        List<RenderedPrompt.Message> messages = new ArrayList<>(request.prompt().messages());
        messages.add(new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, formatValidationErrors(errors)));
        RenderedPrompt prompt = new RenderedPrompt(messages, request.prompt().version());
        return request.withPrompt(prompt);
    }

    private String formatValidationErrors(List<ValidationError> errors) {
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

    private String failureReason(RefineContext ctx) {
        if (ctx.hasCallError()) {
            return "Model call failed after " + ctx.attempt() + " attempts across "
                    + plan.budgets().size() + " model(s): " + ctx.callError().getMessage();
        }
        if (ctx.hasInvalidValidation()) {
            return "Validation failed after " + ctx.attempt() + " attempts across "
                    + plan.budgets().size() + " model(s)";
        }
        return "Refine attempts exhausted after " + ctx.attempt() + " attempts across "
                + plan.budgets().size() + " model(s)";
    }
}

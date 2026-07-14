package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.prompt.PromptBuilder;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

/**
 * Builds the fixed prompt used by the sample text-review harness.
 */
public final class TextReviewPromptBuilder implements PromptBuilder<TextReviewInput> {

    @Override
    public RenderedPrompt build(TextReviewInput input, AiHarnessRunContext ctx) {
        return new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(
                                RenderedPrompt.Role.SYSTEM,
                                "Review the user text. Return JSON only in this shape: "
                                        + "{\"issues\":[{\"code\":\"...\",\"severity\":\"LOW|MEDIUM|HIGH\","
                                        + "\"message\":\"...\",\"quote\":\"...\"}]}"),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, input.text())),
                ctx.promptVersion());
    }
}

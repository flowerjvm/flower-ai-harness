package io.github.parkkevinsb.flower.ai.harness.prompt;

import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;

/**
 * Host-provided prompt renderer for one harness input type.
 */
public interface PromptBuilder<I> {

    RenderedPrompt build(I input, AiHarnessRunContext ctx);
}

package io.github.parkkevinsb.flower.ai.harness.run;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.model.ProviderOptions;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;

import java.time.Duration;
import java.util.List;

final class TestRunRequests {

    private TestRunRequests() {
    }

    static AiModelRequest request() {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Return JSON")),
                new PromptVersion("test", "1.0.0"));
        return new AiModelRequest(
                ModelId.parse("fake:model"),
                prompt,
                ProviderOptions.empty(),
                Duration.ofSeconds(5));
    }
}

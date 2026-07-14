package io.github.flowerjvm.flower.ai.harness.control;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;

import java.time.Duration;
import java.util.List;

final class TestRequests {

    private TestRequests() {
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

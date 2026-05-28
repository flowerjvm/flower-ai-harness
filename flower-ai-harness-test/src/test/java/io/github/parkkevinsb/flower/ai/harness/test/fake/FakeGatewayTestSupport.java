package io.github.parkkevinsb.flower.ai.harness.test.fake;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;

import java.time.Duration;
import java.util.List;

final class FakeGatewayTestSupport {

    static final ModelId MODEL = ModelId.parse("fake:model");

    private FakeGatewayTestSupport() {
    }

    static AiModelRequest request(String text) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, text)),
                new PromptVersion("fake-test", "1.0.0"));
        return new AiModelRequest(MODEL, prompt, null, Duration.ofSeconds(5));
    }
}

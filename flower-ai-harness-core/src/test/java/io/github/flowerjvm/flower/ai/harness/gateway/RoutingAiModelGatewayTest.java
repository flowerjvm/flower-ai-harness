package io.github.flowerjvm.flower.ai.harness.gateway;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingAiModelGatewayTest {

    @Test
    void dispatchesToMatchingProvider() {
        RecordingGateway openai = new RecordingGateway("openai-call");
        RecordingGateway anthropic = new RecordingGateway("anthropic-call");
        RoutingAiModelGateway router = new RoutingAiModelGateway(Map.of(
                "openai", openai,
                "anthropic", anthropic));

        AiModelRequest request = request("anthropic:claude-sonnet");
        AiModelCall call = router.submit(request);

        assertThat(call.callId()).isEqualTo("anthropic-call");
        assertThat(anthropic.lastRequest()).isSameAs(request);
        assertThat(openai.lastRequest()).isNull();
    }

    @Test
    void usesFallbackWhenProviderIsMissing() {
        RecordingGateway fallback = new RecordingGateway("fallback-call");
        RoutingAiModelGateway router = new RoutingAiModelGateway(Map.of())
                .withFallback(fallback);

        AiModelRequest request = request("local:llama");
        AiModelCall call = router.submit(request);

        assertThat(call.callId()).isEqualTo("fallback-call");
        assertThat(fallback.lastRequest()).isSameAs(request);
    }

    @Test
    void failsWhenProviderIsMissingAndNoFallbackExists() {
        RoutingAiModelGateway router = new RoutingAiModelGateway(Map.of());

        assertThatThrownBy(() -> router.submit(request("missing:model")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void copiesProviderMap() {
        RecordingGateway original = new RecordingGateway("original");
        Map<String, AiModelGateway> providers = new java.util.HashMap<>();
        providers.put("openai", original);
        RoutingAiModelGateway router = new RoutingAiModelGateway(providers);
        providers.put("openai", new RecordingGateway("changed"));

        assertThat(router.submit(request("openai:gpt-4o")).callId()).isEqualTo("original");
    }

    private static AiModelRequest request(String modelSpec) {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "hello")),
                new PromptVersion("test", "1.0.0"));
        return new AiModelRequest(ModelId.parse(modelSpec), prompt, null, Duration.ofSeconds(5));
    }

    private static final class RecordingGateway implements AiModelGateway {

        private final String callId;
        private AiModelRequest lastRequest;

        private RecordingGateway(String callId) {
            this.callId = callId;
        }

        @Override
        public AiModelCall submit(AiModelRequest request) {
            lastRequest = request;
            return new ReadyCall(callId);
        }

        private AiModelRequest lastRequest() {
            return lastRequest;
        }
    }

    private record ReadyCall(String callId) implements AiModelCall {

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.READY;
        }

        @Override
        public AiModelResponse result() {
            return new AiModelResponse("{}", ModelId.parse("test:model"), null);
        }

        @Override
        public Throwable error() {
            throw new IllegalStateException("call is not failed");
        }

        @Override
        public void cancel() {
        }
    }
}

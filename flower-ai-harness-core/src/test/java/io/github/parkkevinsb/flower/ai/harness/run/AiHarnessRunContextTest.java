package io.github.parkkevinsb.flower.ai.harness.run;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiHarnessRunContextTest {

    private static final AiHarnessRunContext.AttributeKey<String> TENANT =
            AiHarnessRunContext.AttributeKey.of("tenant", String.class);

    @Test
    void storesTypedAttributes() {
        AiHarnessRunContext ctx = context();

        ctx.putAttribute(TENANT, "acme");

        assertThat(ctx.attribute(TENANT)).contains("acme");
        assertThat(ctx.attributes()).containsEntry(TENANT, "acme");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsAttributeValueThatDoesNotMatchKeyType() {
        AiHarnessRunContext ctx = context();
        AiHarnessRunContext.AttributeKey raw = TENANT;

        assertThatThrownBy(() -> ctx.putAttribute(raw, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void recordsModelLifecycleState() {
        AiHarnessRunContext ctx = context();
        AiModelRequest request = request();
        AiModelCall call = new StaticCall("call-1");
        AiModelResponse response = response();
        List<AiFinding> findings = List.of(AiFinding.of("OK", AiFindingSeverity.INFO, "Looks good"));

        ctx.setCurrentRequest(request);
        ctx.beginModelCall(call);
        ctx.recordResponse(response);
        ctx.recordValidation(new ValidationResult.Valid<>("validated"));
        ctx.recordFindings(findings);

        assertThat(ctx.attempt()).isEqualTo(1);
        assertThat(ctx.currentRequest()).isSameAs(request);
        assertThat(ctx.currentCall()).contains(call);
        assertThat(ctx.latestResponse()).contains(response);
        assertThat(ctx.latestValidation()).containsInstanceOf(ValidationResult.Valid.class);
        assertThat(ctx.latestFindings()).containsExactlyElementsOf(findings);
    }

    private static AiHarnessRunContext context() {
        return new AiHarnessRunContext(
                new AiHarnessRunId("run-1"),
                "test-harness",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);
    }

    private static AiModelRequest request() {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Return JSON")),
                new PromptVersion("test", "1.0.0"));
        return new AiModelRequest(ModelId.parse("fake:model"), prompt, null, Duration.ofSeconds(5));
    }

    private static AiModelResponse response() {
        return new AiModelResponse("{\"ok\":true}", ModelId.parse("fake:model"), null);
    }

    private record StaticCall(String callId) implements AiModelCall {

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.READY;
        }

        @Override
        public AiModelResponse result() {
            return response();
        }

        @Override
        public Throwable error() {
            return null;
        }

        @Override
        public void cancel() {
        }
    }
}

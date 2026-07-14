package io.github.flowerjvm.flower.ai.harness.recovery;

import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunId;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.refine.RefineDecision;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiRecoveryPolicyTest {

    private static final PromptVersion VERSION = new PromptVersion("test", "1.0.0");

    @Test
    void conservativePolicyRetriesWhenCurrentRequestExists() {
        AiModelRequest request = request();

        AiRecoveryDecision decision = AiRecoveryPolicy.conservative()
                .decide(new AiRecoveryContext(snapshot(AiHarnessRunStatus.WAITING_PROVIDER, Optional.of(request)), spec()));

        assertThat(decision).isInstanceOf(AiRecoveryDecision.RetryCurrentRequest.class);
        assertThat(((AiRecoveryDecision.RetryCurrentRequest) decision).request()).isEqualTo(request);
    }

    @Test
    void conservativePolicyMarksCancelledSnapshotsCancelled() {
        AiRecoveryDecision decision = AiRecoveryPolicy.conservative()
                .decide(new AiRecoveryContext(snapshot(AiHarnessRunStatus.CANCELLED, Optional.of(request())), spec()));

        assertThat(decision).isInstanceOf(AiRecoveryDecision.MarkCancelled.class);
        assertThat(((AiRecoveryDecision.MarkCancelled) decision).reason())
                .isEqualTo("Recovered run was already cancelled");
    }

    @Test
    void conservativePolicyFailsRecoverablyWhenNoRequestCanBeRetried() {
        AiRecoveryDecision decision = AiRecoveryPolicy.conservative()
                .decide(new AiRecoveryContext(snapshot(AiHarnessRunStatus.WAITING_PROVIDER, Optional.empty()), spec()));

        assertThat(decision).isInstanceOf(AiRecoveryDecision.FailRecoverable.class);
        assertThat(((AiRecoveryDecision.FailRecoverable) decision).reason())
                .contains("no current request");
    }

    private static AiHarnessRunSnapshot snapshot(
            AiHarnessRunStatus status,
            Optional<AiModelRequest> request
    ) {
        return new AiHarnessRunSnapshot(
                new AiHarnessRunId("run-1"),
                "test",
                VERSION,
                status,
                1,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                request,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AiHarnessSpec<String, String> spec() {
        return AiHarnessSpec.<String, String>builder()
                .harnessId("test")
                .defaultModelId(ModelId.parse("fake:model"))
                .promptVersion(VERSION)
                .promptBuilder((input, ctx) -> request().prompt())
                .validator(response -> new ValidationResult.Valid<>(response.rawText()))
                .refinePolicy(ctx -> new RefineDecision.Continue())
                .findingExtractor((value, ctx) -> List.of())
                .findingSink((findings, ctx) -> {
                })
                .build();
    }

    private static AiModelRequest request() {
        RenderedPrompt prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Return JSON")),
                VERSION);
        return new AiModelRequest(
                ModelId.parse("fake:model"),
                prompt,
                ProviderOptions.empty(),
                Duration.ofSeconds(5));
    }
}

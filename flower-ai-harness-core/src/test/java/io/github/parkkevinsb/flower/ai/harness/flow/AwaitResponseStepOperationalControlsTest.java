package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.control.AiBudgetDecision;
import io.github.parkkevinsb.flower.ai.harness.control.ManualAiCancellationToken;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.refine.RefineDecision;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.InMemoryAiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.AiHarnessClock;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.step.StepResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AwaitResponseStepOperationalControlsTest {

    private static final AiHarnessClock CLOCK = () -> Instant.parse("2026-05-28T00:00:00Z");

    @Test
    void cancellationCancelsPendingProviderCallAndPersistsCancelledStatus() {
        ManualAiCancellationToken token = new ManualAiCancellationToken();
        AiHarnessRunContext context = context(token);
        context.setCurrentRequest(TestFlowRequests.request());
        InMemoryAiHarnessRunStore store = new InMemoryAiHarnessRunStore();
        PendingCall call = new PendingCall("call-1");
        AiHarnessSpec<String, String> spec = specBuilder(store)
                .build();
        AwaitResponseStep step = new AwaitResponseStep(request -> call, context, spec, CLOCK);

        step.onEnter(null);
        assertThat(step.onTick(null).type()).isEqualTo(StepResult.Type.STAY);
        token.cancel("user cancelled");
        StepResult result = step.onTick(null);

        assertThat(result.type()).isEqualTo(StepResult.Type.FAIL);
        assertThat(context.status()).isEqualTo(AiHarnessRunStatus.CANCELLED);
        assertThat(context.terminalReason()).contains("user cancelled");
        assertThat(call.isCancelled()).isTrue();
        assertThat(store.find(context.runId()).orElseThrow().status())
                .isEqualTo(AiHarnessRunStatus.CANCELLED);
        assertThat(store.find(context.runId()).orElseThrow().capturedAt())
                .isEqualTo(CLOCK.now());
    }

    @Test
    void budgetRejectionFailsBeforeProviderSubmission() {
        AiHarnessRunContext context = context(new ManualAiCancellationToken());
        context.setCurrentRequest(TestFlowRequests.request());
        AtomicInteger submissions = new AtomicInteger();
        AiHarnessSpec<String, String> spec = specBuilder(new InMemoryAiHarnessRunStore())
                .budgetPolicy(budget -> AiBudgetDecision.reject("budget exhausted"))
                .build();
        AwaitResponseStep step = new AwaitResponseStep(request -> {
            submissions.incrementAndGet();
            return new PendingCall("call-1");
        }, context, spec, CLOCK);

        step.onEnter(null);
        StepResult result = step.onTick(null);

        assertThat(result.type()).isEqualTo(StepResult.Type.FAIL);
        assertThat(context.status()).isEqualTo(AiHarnessRunStatus.FAILED);
        assertThat(context.terminalReason()).contains("budget exhausted");
        assertThat(submissions).hasValue(0);
    }

    @Test
    void unavailableResourceSlotStaysWithoutBlockingOrSubmitting() {
        AiHarnessRunContext context = context(new ManualAiCancellationToken());
        context.setCurrentRequest(TestFlowRequests.request());
        AtomicInteger submissions = new AtomicInteger();
        AiHarnessSpec<String, String> spec = specBuilder(new InMemoryAiHarnessRunStore())
                .resourceGovernor((request, runContext) -> Optional.empty())
                .build();
        AwaitResponseStep step = new AwaitResponseStep(request -> {
            submissions.incrementAndGet();
            return new PendingCall("call-1");
        }, context, spec, CLOCK);

        step.onEnter(null);
        StepResult result = step.onTick(null);

        assertThat(result.type()).isEqualTo(StepResult.Type.STAY);
        assertThat(context.status()).isEqualTo(AiHarnessRunStatus.QUEUED);
        assertThat(context.attempt()).isZero();
        assertThat(submissions).hasValue(0);
    }

    private static AiHarnessRunContext context(ManualAiCancellationToken token) {
        return new AiHarnessRunContext(
                new AiHarnessRunId("run-1"),
                "test",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH,
                token);
    }

    private static AiHarnessSpec.Builder<String, String> specBuilder(InMemoryAiHarnessRunStore store) {
        PromptVersion version = new PromptVersion("test", "1.0.0");
        return AiHarnessSpec.<String, String>builder()
                .harnessId("test")
                .defaultModelId(ModelId.parse("fake:model"))
                .promptVersion(version)
                .promptBuilder((input, ctx) -> new RenderedPrompt(
                        List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, input)),
                        version))
                .validator(response -> new ValidationResult.Valid<>(response.rawText()))
                .refinePolicy(ctx -> new RefineDecision.Continue())
                .findingExtractor((value, ctx) -> List.of())
                .findingSink((findings, ctx) -> {
                })
                .runStore(store);
    }

    private record PendingCall(String callId, AtomicBoolean cancelled) implements AiModelCall {

        PendingCall(String callId) {
            this(callId, new AtomicBoolean());
        }

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.PENDING;
        }

        @Override
        public AiModelResponse result() {
            throw new IllegalStateException("pending");
        }

        @Override
        public Throwable error() {
            return null;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }
}

package io.github.parkkevinsb.flower.ai.harness.flow;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.recovery.AiRecoveryDecision;
import io.github.parkkevinsb.flower.ai.harness.refine.RefineDecision;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.InMemoryAiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.step.StepDefinition;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiHarnessFlowFactoryRecoveryTest {

    private static final PromptVersion VERSION = new PromptVersion("text-review", "1.0.0");
    private static final AiHarnessRunId RUN_ID = new AiHarnessRunId("recovered-run");

    @Test
    void conservativeRecoveryRetriesCurrentRequestFromAwaitResponseStep() {
        InMemoryAiHarnessRunStore store = new InMemoryAiHarnessRunStore();
        AtomicReference<AiModelRequest> submitted = new AtomicReference<>();
        AiHarnessFlowFactory<String, Review> factory = new AiHarnessFlowFactory<>(
                gateway(submitted),
                spec(store),
                () -> Instant.EPOCH);
        AiModelRequest request = TestFlowRequests.request();
        AiHarnessRunSnapshot snapshot = snapshot(AiHarnessRunStatus.WAITING_PROVIDER, Optional.of(request));

        AiHarnessFlow recovered = factory.createRecoveredFlow(snapshot);

        assertThat(recovered.flow().flowId().flowKey()).isEqualTo(RUN_ID.value());
        assertThat(recovered.flow().steps())
                .extracting(StepDefinition::stepId)
                .containsExactly(
                        AiHarnessFlowFactory.AWAIT_RESPONSE_STEP,
                        AiHarnessFlowFactory.VALIDATE_RESPONSE_STEP,
                        AiHarnessFlowFactory.REFINE_DECISION_STEP,
                        AiHarnessFlowFactory.EMIT_FINDINGS_STEP);

        runFlow(recovered.flow());

        assertThat(recovered.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(submitted.get()).isEqualTo(request);
        assertThat(recovered.context().attempt()).isEqualTo(snapshot.attempt() + 1);
        assertThat(recovered.context().status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
        assertThat(store.find(RUN_ID).orElseThrow().status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
    }

    @Test
    void recoveredCancelledSnapshotBuildsTerminalCancelledFlow() {
        InMemoryAiHarnessRunStore store = new InMemoryAiHarnessRunStore();
        AiHarnessFlowFactory<String, Review> factory = new AiHarnessFlowFactory<>(
                request -> {
                    throw new AssertionError("cancelled recovery must not call provider");
                },
                spec(store),
                () -> Instant.EPOCH);
        AiHarnessRunSnapshot snapshot = new AiHarnessRunSnapshot(
                RUN_ID,
                "text-review",
                VERSION,
                AiHarnessRunStatus.CANCELLED,
                1,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                Optional.of(TestFlowRequests.request()),
                Optional.of("call-1"),
                Optional.empty(),
                Optional.of("user cancelled"));

        AiHarnessFlow recovered = factory.createRecoveredFlow(snapshot);
        runFlow(recovered.flow());

        assertThat(recovered.flow().state()).isEqualTo(FlowState.FAILED);
        assertThat(recovered.context().status()).isEqualTo(AiHarnessRunStatus.CANCELLED);
        assertThat(store.find(RUN_ID).orElseThrow().status()).isEqualTo(AiHarnessRunStatus.CANCELLED);
    }

    @Test
    void customRecoveryDecisionCanFailRecoverableWithoutProviderCall() {
        InMemoryAiHarnessRunStore store = new InMemoryAiHarnessRunStore();
        AiHarnessFlowFactory<String, Review> factory = new AiHarnessFlowFactory<>(
                request -> {
                    throw new AssertionError("failed recovery must not call provider");
                },
                spec(store),
                () -> Instant.EPOCH);

        AiHarnessFlow recovered = factory.createRecoveredFlow(
                snapshot(AiHarnessRunStatus.WAITING_PROVIDER, Optional.of(TestFlowRequests.request())),
                context -> new AiRecoveryDecision.FailRecoverable("manual recovery required"));
        runFlow(recovered.flow());

        assertThat(recovered.flow().state()).isEqualTo(FlowState.FAILED);
        assertThat(recovered.context().status()).isEqualTo(AiHarnessRunStatus.FAILED);
        assertThat(recovered.context().terminalReason()).contains("manual recovery required");
        assertThat(store.find(RUN_ID).orElseThrow().terminalReason()).contains("manual recovery required");
    }

    @Test
    void rejectsSnapshotFromDifferentHarnessSpec() {
        AiHarnessFlowFactory<String, Review> factory = new AiHarnessFlowFactory<>(
                gateway(new AtomicReference<>()),
                spec(new InMemoryAiHarnessRunStore()),
                () -> Instant.EPOCH);
        AiHarnessRunSnapshot snapshot = new AiHarnessRunSnapshot(
                RUN_ID,
                "other-harness",
                VERSION,
                AiHarnessRunStatus.WAITING_PROVIDER,
                1,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                Optional.of(TestFlowRequests.request()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertThatThrownBy(() -> factory.createRecoveredFlow(snapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("harnessId");
    }

    private static AiHarnessRunSnapshot snapshot(
            AiHarnessRunStatus status,
            Optional<AiModelRequest> request
    ) {
        return new AiHarnessRunSnapshot(
                RUN_ID,
                "text-review",
                VERSION,
                status,
                1,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                request,
                Optional.of("call-1"),
                Optional.empty(),
                Optional.empty());
    }

    private static AiHarnessSpec<String, Review> spec(InMemoryAiHarnessRunStore store) {
        return AiHarnessSpec.<String, Review>builder()
                .harnessId("text-review")
                .defaultModelId(ModelId.parse("fake:model"))
                .promptVersion(VERSION)
                .promptBuilder((input, ctx) -> TestFlowRequests.request().prompt())
                .validator(response -> new ValidationResult.Valid<>(new Review(response.rawText())))
                .refinePolicy(ctx -> new RefineDecision.Continue())
                .findingExtractor((review, ctx) -> List.of(
                        AiFinding.of("RECOVERED", AiFindingSeverity.INFO, review.summary())))
                .findingSink((findings, ctx) -> {
                })
                .runStore(store)
                .build();
    }

    private static AiModelGateway gateway(AtomicReference<AiModelRequest> submitted) {
        return request -> {
            submitted.set(request);
            return new ReadyCall("recovered-call");
        };
    }

    private static void runFlow(Flow flow) {
        ManualClock clock = new ManualClock(0);
        Worker worker = Worker.builder("recovery-test").build();
        Engine engine = Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();
        engine.attach();
        worker.submit(flow);
        for (int i = 0; i < 20 && !flow.state().isTerminal(); i++) {
            worker.tickOnce();
            clock.advance(1);
        }
        if (!flow.state().isTerminal()) {
            throw new AssertionError("flow did not terminate: " + flow.flowId());
        }
    }

    private record ReadyCall(String callId) implements AiModelCall {

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.READY;
        }

        @Override
        public AiModelResponse result() {
            return new AiModelResponse("recovered", ModelId.parse("fake:model"), null);
        }

        @Override
        public Throwable error() {
            return null;
        }

        @Override
        public void cancel() {
        }
    }

    private record Review(String summary) {
    }
}

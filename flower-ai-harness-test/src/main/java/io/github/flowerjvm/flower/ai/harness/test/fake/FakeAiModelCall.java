package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse.ResponseMetadata;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;

import java.util.Objects;
import java.util.function.Consumer;

final class FakeAiModelCall implements AiModelCall {

    private final String callId;
    private final AiModelRequest request;
    private final FixedAiHarnessClock clock;
    private final long submittedTick;
    private final String text;
    private final Throwable error;
    private final int ticksUntilTerminal;
    private final AiModelCallStatus successStatus;
    private final Consumer<AiModelCallStatus> statusRecorder;

    private AiModelCallStatus status = AiModelCallStatus.PENDING;
    private boolean cancelled;

    FakeAiModelCall(
            String callId,
            AiModelRequest request,
            FixedAiHarnessClock clock,
            FakeResponseProgram program,
            Consumer<AiModelCallStatus> statusRecorder
    ) {
        this.callId = Objects.requireNonNull(callId, "callId must not be null");
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.submittedTick = clock.tickCount();
        this.statusRecorder = Objects.requireNonNull(statusRecorder, "statusRecorder must not be null");

        ProgramShape shape = shape(program);
        this.text = shape.text();
        this.error = shape.error();
        this.ticksUntilTerminal = shape.ticksUntilTerminal();
        this.successStatus = shape.status();
    }

    @Override
    public String callId() {
        return callId;
    }

    @Override
    public AiModelCallStatus poll() {
        if (cancelled) {
            return AiModelCallStatus.CANCELLED;
        }
        if (status != AiModelCallStatus.PENDING) {
            return status;
        }
        if (clock.tickCount() - submittedTick >= ticksUntilTerminal) {
            status = successStatus;
            statusRecorder.accept(status);
        }
        return status;
    }

    @Override
    public AiModelResponse result() {
        if (poll() != AiModelCallStatus.READY) {
            throw new IllegalStateException("fake model call is not ready: " + callId);
        }
        return new AiModelResponse(text, request.modelId(), ResponseMetadata.empty());
    }

    @Override
    public Throwable error() {
        if (poll() != AiModelCallStatus.FAILED) {
            return null;
        }
        return error;
    }

    @Override
    public void cancel() {
        cancelled = true;
        status = AiModelCallStatus.CANCELLED;
        statusRecorder.accept(status);
    }

    private static ProgramShape shape(FakeResponseProgram program) {
        Objects.requireNonNull(program, "program must not be null");
        if (program instanceof FakeResponseProgram.ImmediateText immediate) {
            return new ProgramShape(immediate.text(), null, 0, AiModelCallStatus.READY);
        }
        if (program instanceof FakeResponseProgram.ImmediateError immediate) {
            return new ProgramShape(null, immediate.error(), 0, AiModelCallStatus.FAILED);
        }
        if (program instanceof FakeResponseProgram.DelayedText delayed) {
            return new ProgramShape(delayed.text(), null, delayed.ticksUntilReady(), AiModelCallStatus.READY);
        }
        if (program instanceof FakeResponseProgram.DelayedError delayed) {
            return new ProgramShape(null, delayed.error(), delayed.ticksUntilFailed(), AiModelCallStatus.FAILED);
        }
        throw new IllegalArgumentException("sequence programs must be resolved before call creation");
    }

    private record ProgramShape(
            String text,
            Throwable error,
            int ticksUntilTerminal,
            AiModelCallStatus status
    ) {
    }
}

package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Programmable deterministic model gateway for harness tests.
 */
public final class FakeAiModelGateway implements AiModelGateway {

    private final FixedAiHarnessClock clock;
    private final List<ProgramRegistration> registrations = new ArrayList<>();
    private final List<MutableRecordedCall> recordedCalls = new ArrayList<>();
    private long callSequence;

    public FakeAiModelGateway(FixedAiHarnessClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public FakeAiModelGateway respondTo(RequestMatcher matcher, FakeResponseProgram program) {
        registrations.add(new ProgramRegistration(matcher, program));
        return this;
    }

    public FakeAiModelGateway respondToAny(FakeResponseProgram program) {
        return respondTo(RequestMatcher.any(), program);
    }

    public List<RecordedCall> recordedCalls() {
        return recordedCalls.stream()
                .map(MutableRecordedCall::snapshot)
                .toList();
    }

    public RecordedCall lastCall() {
        if (recordedCalls.isEmpty()) {
            throw new IllegalStateException("no fake model calls recorded");
        }
        return recordedCalls.get(recordedCalls.size() - 1).snapshot();
    }

    public void reset() {
        registrations.clear();
        recordedCalls.clear();
        callSequence = 0;
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ProgramRegistration registration = registrations.stream()
                .filter(candidate -> candidate.matches(request))
                .findFirst()
                .orElseThrow(() -> new GatewayException("No fake response program matched request"));
        FakeResponseProgram program = registration.nextProgram();
        MutableRecordedCall recorded = new MutableRecordedCall(request, clock.now());
        recordedCalls.add(recorded);
        String callId = "fake-call-" + ++callSequence;
        return new FakeAiModelCall(callId, request, clock, program, recorded::recordStatus);
    }

    private static final class ProgramRegistration {

        private final RequestMatcher matcher;
        private final FakeResponseProgram program;
        private int sequenceIndex;

        private ProgramRegistration(RequestMatcher matcher, FakeResponseProgram program) {
            this.matcher = Objects.requireNonNull(matcher, "matcher must not be null");
            this.program = Objects.requireNonNull(program, "program must not be null");
        }

        private boolean matches(AiModelRequest request) {
            return matcher.matches(request);
        }

        private FakeResponseProgram nextProgram() {
            if (program instanceof FakeResponseProgram.Sequence sequence) {
                if (sequenceIndex >= sequence.steps().size()) {
                    throw new GatewayException("Fake response sequence exhausted");
                }
                return sequence.steps().get(sequenceIndex++);
            }
            return program;
        }
    }

    private static final class MutableRecordedCall {

        private final AiModelRequest request;
        private final Instant submittedAt;
        private AiModelCallStatus status = AiModelCallStatus.PENDING;

        private MutableRecordedCall(AiModelRequest request, Instant submittedAt) {
            this.request = request;
            this.submittedAt = submittedAt;
        }

        private void recordStatus(AiModelCallStatus next) {
            status = next;
        }

        private RecordedCall snapshot() {
            return new RecordedCall(request, submittedAt, status);
        }
    }
}

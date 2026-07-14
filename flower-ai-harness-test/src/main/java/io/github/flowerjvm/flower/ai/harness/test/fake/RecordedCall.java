package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;

import java.time.Instant;
import java.util.Objects;

/**
 * Snapshot of one request submitted to {@link FakeAiModelGateway}.
 */
public record RecordedCall(
        AiModelRequest request,
        Instant submittedAt,
        AiModelCallStatus terminalStatus
) {

    public RecordedCall {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        Objects.requireNonNull(terminalStatus, "terminalStatus must not be null");
    }
}

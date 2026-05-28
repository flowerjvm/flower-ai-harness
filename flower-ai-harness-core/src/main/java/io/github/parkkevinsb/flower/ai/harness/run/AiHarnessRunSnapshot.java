package io.github.parkkevinsb.flower.ai.harness.run;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistable view of a harness run.
 *
 * <p>The snapshot is not a Flower checkpoint and does not replace Flower
 * durable resume. It records AI-run state that Flower deliberately does not
 * understand, such as attempts, provider call ids, terminal reasons, and the
 * current model request.
 */
public record AiHarnessRunSnapshot(
        AiHarnessRunId runId,
        String harnessId,
        PromptVersion promptVersion,
        AiHarnessRunStatus status,
        int attempt,
        Instant startedAt,
        Instant capturedAt,
        Optional<AiModelRequest> currentRequest,
        Optional<String> currentCallId,
        Optional<AiModelResponse> latestResponse,
        Optional<String> terminalReason
) {

    public AiHarnessRunSnapshot {
        Objects.requireNonNull(runId, "runId must not be null");
        harnessId = requireText(harnessId, "harnessId");
        Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        currentRequest = currentRequest == null ? Optional.empty() : currentRequest;
        currentCallId = currentCallId == null ? Optional.empty() : currentCallId;
        latestResponse = latestResponse == null ? Optional.empty() : latestResponse;
        terminalReason = terminalReason == null ? Optional.empty() : terminalReason;
    }

    public static AiHarnessRunSnapshot from(AiHarnessRunContext context, Instant capturedAt) {
        Objects.requireNonNull(context, "context must not be null");
        return new AiHarnessRunSnapshot(
                context.runId(),
                context.harnessId(),
                context.promptVersion(),
                context.status(),
                context.attempt(),
                context.startedAt(),
                capturedAt,
                Optional.ofNullable(context.currentRequest()),
                context.currentCall().map(call -> call.callId()),
                context.latestResponse(),
                context.terminalReason());
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}

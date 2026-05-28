package io.github.parkkevinsb.flower.ai.harness.run;

import io.github.parkkevinsb.flower.ai.harness.control.AiCancellationToken;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable state for one harness run, shared by the internal Flower steps.
 */
public final class AiHarnessRunContext {

    private final AiHarnessRunId runId;
    private final String harnessId;
    private final PromptVersion promptVersion;
    private final Instant startedAt;
    private final AiCancellationToken cancellationToken;
    private final Map<AttributeKey<?>, Object> attributes = new LinkedHashMap<>();

    private AiHarnessRunStatus status = AiHarnessRunStatus.QUEUED;
    private int attempt;
    private AiModelRequest currentRequest;
    private AiModelCall currentCall;
    private AiModelResponse latestResponse;
    private ValidationResult<?> latestValidation;
    private Throwable latestCallError;
    private List<AiFinding> latestFindings = List.of();
    private String terminalReason;

    public AiHarnessRunContext(
            AiHarnessRunId runId,
            String harnessId,
            PromptVersion promptVersion,
            Instant startedAt
    ) {
        this(runId, harnessId, promptVersion, startedAt, AiCancellationToken.none());
    }

    public AiHarnessRunContext(
            AiHarnessRunId runId,
            String harnessId,
            PromptVersion promptVersion,
            Instant startedAt,
            AiCancellationToken cancellationToken
    ) {
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
        this.harnessId = requireText(harnessId, "harnessId");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.cancellationToken = cancellationToken == null ? AiCancellationToken.none() : cancellationToken;
    }

    public AiHarnessRunId runId() {
        return runId;
    }

    public String harnessId() {
        return harnessId;
    }

    public PromptVersion promptVersion() {
        return promptVersion;
    }

    public int attempt() {
        return attempt;
    }

    public AiHarnessRunStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public AiCancellationToken cancellationToken() {
        return cancellationToken;
    }

    public AiModelRequest currentRequest() {
        return currentRequest;
    }

    public Optional<AiModelCall> currentCall() {
        return Optional.ofNullable(currentCall);
    }

    public Optional<AiModelResponse> latestResponse() {
        return Optional.ofNullable(latestResponse);
    }

    public Optional<ValidationResult<?>> latestValidation() {
        return Optional.ofNullable(latestValidation);
    }

    public Optional<Throwable> latestCallError() {
        return Optional.ofNullable(latestCallError);
    }

    public List<AiFinding> latestFindings() {
        return latestFindings;
    }

    public Optional<String> terminalReason() {
        return Optional.ofNullable(terminalReason);
    }

    public <T> Optional<T> attribute(AttributeKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");
        Object value = attributes.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(value));
    }

    public <T> void putAttribute(AttributeKey<T> key, T value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (!key.type().isInstance(value)) {
            throw new IllegalArgumentException("attribute value does not match key type: " + key.name());
        }
        attributes.put(key, value);
    }

    public Map<AttributeKey<?>, Object> attributes() {
        return Map.copyOf(attributes);
    }

    public void setCurrentRequest(AiModelRequest request) {
        currentRequest = Objects.requireNonNull(request, "request must not be null");
        currentCall = null;
        latestResponse = null;
        latestValidation = null;
        latestCallError = null;
        terminalReason = null;
    }

    public void beginModelCall(AiModelCall call) {
        currentCall = Objects.requireNonNull(call, "call must not be null");
        latestResponse = null;
        latestValidation = null;
        latestCallError = null;
        attempt++;
    }

    public void recordSubmissionFailure(Throwable error) {
        latestCallError = Objects.requireNonNull(error, "error must not be null");
        currentCall = null;
        latestResponse = null;
        latestValidation = null;
        attempt++;
    }

    public void recordResponse(AiModelResponse response) {
        latestResponse = Objects.requireNonNull(response, "response must not be null");
        latestCallError = null;
    }

    public void recordCallFailure(Throwable error) {
        latestCallError = Objects.requireNonNull(error, "error must not be null");
        latestResponse = null;
        latestValidation = null;
    }

    public void recordValidation(ValidationResult<?> validation) {
        latestValidation = Objects.requireNonNull(validation, "validation must not be null");
    }

    public void recordFindings(List<AiFinding> findings) {
        Objects.requireNonNull(findings, "findings must not be null");
        latestFindings = List.copyOf(findings);
    }

    public void markStatus(AiHarnessRunStatus nextStatus) {
        status = Objects.requireNonNull(nextStatus, "nextStatus must not be null");
    }

    public void markSucceeded() {
        status = AiHarnessRunStatus.SUCCEEDED;
        terminalReason = null;
    }

    public void markFailed(String reason) {
        status = AiHarnessRunStatus.FAILED;
        terminalReason = normalizeReason(reason, "failed");
    }

    public void markCancelled(String reason) {
        status = AiHarnessRunStatus.CANCELLED;
        terminalReason = normalizeReason(reason, "cancelled");
    }

    public static final class AttributeKey<T> {

        private final String name;
        private final Class<T> type;

        private AttributeKey(String name, Class<T> type) {
            this.name = requireText(name, "name");
            this.type = Objects.requireNonNull(type, "type must not be null");
        }

        public static <T> AttributeKey<T> of(String name, Class<T> type) {
            return new AttributeKey<>(name, type);
        }

        public String name() {
            return name;
        }

        public Class<T> type() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AttributeKey<?> that)) {
                return false;
            }
            return name.equals(that.name) && type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }

        @Override
        public String toString() {
            return "AttributeKey{" + name + ":" + type.getSimpleName() + "}";
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static String normalizeReason(String reason, String fallback) {
        if (reason == null || reason.trim().isEmpty()) {
            return fallback;
        }
        return reason.trim();
    }
}

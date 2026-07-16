package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Failure reported by or while executing an agent runner.
 */
public class AgentCliExecutionException extends GatewayException {

    private final String callId;
    private final Path runDirectory;
    private final OptionalInt exitCode;
    private final Optional<String> errorCode;
    private final boolean retryable;
    private final String stderrTail;

    public AgentCliExecutionException(
            String message,
            String callId,
            Path runDirectory,
            OptionalInt exitCode,
            Optional<String> errorCode,
            boolean retryable,
            String stderrTail
    ) {
        this(message, null, callId, runDirectory, exitCode, errorCode, retryable, stderrTail);
    }

    public AgentCliExecutionException(
            String message,
            Throwable cause,
            String callId,
            Path runDirectory,
            OptionalInt exitCode,
            Optional<String> errorCode,
            boolean retryable,
            String stderrTail
    ) {
        super(message, cause);
        this.callId = Objects.requireNonNull(callId, "callId must not be null");
        this.runDirectory = Objects.requireNonNull(runDirectory, "runDirectory must not be null");
        this.exitCode = exitCode == null ? OptionalInt.empty() : exitCode;
        this.errorCode = errorCode == null ? Optional.empty() : errorCode;
        this.retryable = retryable;
        this.stderrTail = stderrTail == null ? "" : stderrTail;
    }

    public String callId() {
        return callId;
    }

    public Path runDirectory() {
        return runDirectory;
    }

    public OptionalInt exitCode() {
        return exitCode;
    }

    public Optional<String> errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public String stderrTail() {
        return stderrTail;
    }
}

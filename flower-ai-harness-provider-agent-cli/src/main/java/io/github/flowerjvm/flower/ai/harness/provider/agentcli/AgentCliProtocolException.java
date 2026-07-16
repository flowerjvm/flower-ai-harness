package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Raised when a runner does not produce a valid v1 result envelope.
 */
public final class AgentCliProtocolException extends AgentCliExecutionException {

    public AgentCliProtocolException(
            String message,
            Throwable cause,
            String callId,
            Path runDirectory,
            OptionalInt exitCode,
            String stderrTail
    ) {
        super(
                message,
                cause,
                callId,
                runDirectory,
                exitCode,
                Optional.of("PROTOCOL_ERROR"),
                false,
                stderrTail);
    }
}

package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Raised when the configured effective execution timeout elapses.
 */
public final class AgentCliTimeoutException extends AgentCliExecutionException {

    private final Duration timeout;

    public AgentCliTimeoutException(
            String callId,
            Path runDirectory,
            Duration timeout,
            String stderrTail
    ) {
        super(
                "Agent CLI call timed out after " + timeout,
                callId,
                runDirectory,
                OptionalInt.empty(),
                Optional.of("TIMEOUT"),
                true,
                stderrTail);
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}

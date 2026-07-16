package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One observational progress line from an agent runner.
 */
public record AgentCliProgressEvent(
        String callId,
        String type,
        Optional<Instant> timestamp,
        Map<String, Object> data,
        String rawLine
) {

    public AgentCliProgressEvent {
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        timestamp = timestamp == null ? Optional.empty() : timestamp;
        data = data == null ? Map.of() : Map.copyOf(data);
        Objects.requireNonNull(rawLine, "rawLine must not be null");
    }
}

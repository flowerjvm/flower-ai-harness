package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

/**
 * Receives best-effort progress events emitted as JSON Lines by a runner.
 */
@FunctionalInterface
public interface AgentCliProgressListener {

    void onProgress(AgentCliProgressEvent event);

    static AgentCliProgressListener noOp() {
        return event -> {
        };
    }
}

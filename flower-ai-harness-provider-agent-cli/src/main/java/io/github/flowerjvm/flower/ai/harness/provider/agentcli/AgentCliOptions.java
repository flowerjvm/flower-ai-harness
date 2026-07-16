package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

/**
 * Conventional provider option keys understood by example agent runners.
 *
 * <p>The provider forwards every JSON-safe option to the runner. These keys
 * establish portable names without coupling the Java module to one vendor.
 */
public final class AgentCliOptions {

    public static final String RUNNER_MODE = "agentCli.runnerMode";
    public static final String SDK_MODEL = "agentCli.sdkModel";
    public static final String SANDBOX = "agentCli.sandbox";
    public static final String PERMISSION_MODE = "agentCli.permissionMode";
    public static final String MAX_TURNS = "agentCli.maxTurns";
    public static final String ALLOWED_TOOLS = "agentCli.allowedTools";

    private AgentCliOptions() {
    }
}

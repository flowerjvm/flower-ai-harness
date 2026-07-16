package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentCliGatewayConfigTest {

    @Test
    void rejectsUnknownPlaceholdersAndEscapingFileNames() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AgentCliGatewayConfig.builder()
                        .command(List.of("runner", "{unknown}"))
                        .build())
                .withMessageContaining("Unsupported command placeholder");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AgentCliGatewayConfig.builder()
                        .command(List.of("runner"))
                        .requestFileName(Path.of("..", "request.json").toString())
                        .build())
                .withMessageContaining("simple file name");
    }

    @Test
    void subscriptionPolicyFiltersApiKeysAndAllowsLoginLocations() {
        Map<String, String> resolved = AgentCliEnvironmentPolicy.subscriptionSafeDefaults().resolve(Map.of(
                "PATH", "path",
                "HOME", "home",
                "CODEX_HOME", "codex",
                "CLAUDE_CONFIG_DIR", "claude",
                "OPENAI_API_KEY", "openai-secret",
                "ANTHROPIC_API_KEY", "anthropic-secret",
                "CLAUDE_CODE_OAUTH_TOKEN", "oauth-secret",
                "UNRELATED_SECRET", "secret"));

        assertThat(resolved)
                .containsEntry("PATH", "path")
                .containsEntry("HOME", "home")
                .containsEntry("CODEX_HOME", "codex")
                .containsEntry("CLAUDE_CONFIG_DIR", "claude")
                .doesNotContainKeys(
                        "OPENAI_API_KEY",
                        "ANTHROPIC_API_KEY",
                        "CLAUDE_CODE_OAUTH_TOKEN",
                        "UNRELATED_SECRET");
    }

    @Test
    void deniedVariableCannotBeAddedExplicitly() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AgentCliEnvironmentPolicy.builder()
                        .denyName("SECRET")
                        .variable("secret", "value")
                        .build())
                .withMessageContaining("denied");
    }
}

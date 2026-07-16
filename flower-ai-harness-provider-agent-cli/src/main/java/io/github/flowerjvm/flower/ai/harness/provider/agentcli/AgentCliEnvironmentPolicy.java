package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Controls which environment variables are visible to an agent subprocess.
 */
public final class AgentCliEnvironmentPolicy {

    private static final Set<String> SUBSCRIPTION_SAFE_ALLOWLIST = Set.of(
            "PATH",
            "PATHEXT",
            "HOME",
            "USERPROFILE",
            "APPDATA",
            "LOCALAPPDATA",
            "TEMP",
            "TMP",
            "SYSTEMROOT",
            "COMSPEC",
            "PROGRAMFILES",
            "PROGRAMFILES(X86)",
            "PROGRAMDATA",
            "SHELL",
            "LANG",
            "LC_ALL",
            "XDG_CONFIG_HOME",
            "XDG_CACHE_HOME",
            "CODEX_HOME",
            "CLAUDE_CONFIG_DIR",
            "ANTHROPIC_CONFIG_DIR",
            "ANTHROPIC_PROFILE");

    private static final Set<String> DEFAULT_DENYLIST = Set.of(
            "OPENAI_API_KEY",
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_AUTH_TOKEN",
            "CLAUDE_API_KEY",
            "CLAUDE_CODE_OAUTH_TOKEN",
            "CODEX_API_KEY",
            "CODEX_ACCESS_TOKEN",
            "AZURE_OPENAI_API_KEY");

    private final boolean inheritParentEnvironment;
    private final Set<String> allowedNames;
    private final Set<String> deniedNames;
    private final Map<String, String> explicitVariables;

    private AgentCliEnvironmentPolicy(Builder builder) {
        inheritParentEnvironment = builder.inheritParentEnvironment;
        allowedNames = normalizedCopy(builder.allowedNames);
        deniedNames = normalizedCopy(builder.deniedNames);
        explicitVariables = Map.copyOf(builder.explicitVariables);
        for (String name : explicitVariables.keySet()) {
            if (deniedNames.contains(normalize(name))) {
                throw new IllegalArgumentException("Explicit environment variable is denied: " + name);
            }
        }
    }

    public static AgentCliEnvironmentPolicy subscriptionSafeDefaults() {
        return builder()
                .allowNames(SUBSCRIPTION_SAFE_ALLOWLIST)
                .denyNames(DEFAULT_DENYLIST)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves the environment without exposing or logging variable values.
     */
    public Map<String, String> resolve(Map<String, String> parentEnvironment) {
        Objects.requireNonNull(parentEnvironment, "parentEnvironment must not be null");
        Map<String, String> resolved = new LinkedHashMap<>();
        if (inheritParentEnvironment) {
            parentEnvironment.forEach((name, value) -> {
                if (!deniedNames.contains(normalize(name))) {
                    resolved.put(name, value);
                }
            });
        } else {
            parentEnvironment.forEach((name, value) -> {
                String normalized = normalize(name);
                if (allowedNames.contains(normalized) && !deniedNames.contains(normalized)) {
                    resolved.put(name, value);
                }
            });
        }
        explicitVariables.forEach(resolved::put);
        resolved.entrySet().removeIf(entry -> deniedNames.contains(normalize(entry.getKey())));
        return Map.copyOf(resolved);
    }

    private static Set<String> normalizedCopy(Set<String> names) {
        Set<String> normalized = new LinkedHashSet<>();
        names.forEach(name -> normalized.add(normalize(name)));
        return Set.copyOf(normalized);
    }

    private static String normalize(String name) {
        Objects.requireNonNull(name, "environment variable name must not be null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("environment variable name must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static final class Builder {

        private boolean inheritParentEnvironment;
        private final Set<String> allowedNames = new LinkedHashSet<>();
        private final Set<String> deniedNames = new LinkedHashSet<>();
        private final Map<String, String> explicitVariables = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder inheritParentEnvironment(boolean inherit) {
            inheritParentEnvironment = inherit;
            return this;
        }

        public Builder allowName(String name) {
            allowedNames.add(name);
            return this;
        }

        public Builder allowNames(Iterable<String> names) {
            Objects.requireNonNull(names, "names must not be null");
            names.forEach(allowedNames::add);
            return this;
        }

        public Builder denyName(String name) {
            deniedNames.add(name);
            return this;
        }

        public Builder denyNames(Iterable<String> names) {
            Objects.requireNonNull(names, "names must not be null");
            names.forEach(deniedNames::add);
            return this;
        }

        public Builder variable(String name, String value) {
            explicitVariables.put(
                    Objects.requireNonNull(name, "name must not be null"),
                    Objects.requireNonNull(value, "value must not be null"));
            return this;
        }

        public AgentCliEnvironmentPolicy build() {
            return new AgentCliEnvironmentPolicy(this);
        }
    }
}

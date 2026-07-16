package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable configuration for {@link AgentCliModelGateway}.
 */
public final class AgentCliGatewayConfig {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> SUPPORTED_PLACEHOLDERS = Set.of(
            "requestFile",
            "resultFile",
            "runDirectory",
            "workingDirectory",
            "callId",
            "model");

    private final List<String> command;
    private final Path processWorkingDirectory;
    private final Path runWorkspaceRoot;
    private final String requestFileName;
    private final String resultFileName;
    private final Duration maxExecutionTime;
    private final Duration killGracePeriod;
    private final AgentCliEnvironmentPolicy environmentPolicy;
    private final AgentCliProgressListener progressListener;
    private final int maxStderrTailCharacters;

    private AgentCliGatewayConfig(Builder builder) {
        command = validateCommand(builder.command);
        processWorkingDirectory = normalize(builder.processWorkingDirectory, "processWorkingDirectory");
        runWorkspaceRoot = normalize(builder.runWorkspaceRoot, "runWorkspaceRoot");
        requestFileName = validateFileName(builder.requestFileName, "requestFileName");
        resultFileName = validateFileName(builder.resultFileName, "resultFileName");
        if (requestFileName.equals(resultFileName)) {
            throw new IllegalArgumentException("requestFileName and resultFileName must differ");
        }
        maxExecutionTime = positive(builder.maxExecutionTime, "maxExecutionTime");
        killGracePeriod = positive(builder.killGracePeriod, "killGracePeriod");
        environmentPolicy = Objects.requireNonNull(builder.environmentPolicy, "environmentPolicy must not be null");
        progressListener = Objects.requireNonNull(builder.progressListener, "progressListener must not be null");
        if (builder.maxStderrTailCharacters < 256) {
            throw new IllegalArgumentException("maxStderrTailCharacters must be at least 256");
        }
        maxStderrTailCharacters = builder.maxStderrTailCharacters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> command() {
        return command;
    }

    public Path processWorkingDirectory() {
        return processWorkingDirectory;
    }

    public Path runWorkspaceRoot() {
        return runWorkspaceRoot;
    }

    public String requestFileName() {
        return requestFileName;
    }

    public String resultFileName() {
        return resultFileName;
    }

    public Duration maxExecutionTime() {
        return maxExecutionTime;
    }

    public Duration killGracePeriod() {
        return killGracePeriod;
    }

    public AgentCliEnvironmentPolicy environmentPolicy() {
        return environmentPolicy;
    }

    public AgentCliProgressListener progressListener() {
        return progressListener;
    }

    public int maxStderrTailCharacters() {
        return maxStderrTailCharacters;
    }

    private static List<String> validateCommand(List<String> value) {
        Objects.requireNonNull(value, "command must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        List<String> copy = List.copyOf(value);
        for (String argument : copy) {
            Objects.requireNonNull(argument, "command argument must not be null");
            if (argument.isBlank()) {
                throw new IllegalArgumentException("command argument must not be blank");
            }
            Matcher matcher = PLACEHOLDER.matcher(argument);
            while (matcher.find()) {
                if (!SUPPORTED_PLACEHOLDERS.contains(matcher.group(1))) {
                    throw new IllegalArgumentException("Unsupported command placeholder: " + matcher.group());
                }
            }
        }
        return copy;
    }

    private static String validateFileName(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        Path path = Path.of(value);
        if (value.isBlank()
                || path.isAbsolute()
                || path.getNameCount() != 1
                || ".".equals(value)
                || "..".equals(value)) {
            throw new IllegalArgumentException(fieldName + " must be a simple file name");
        }
        return value;
    }

    private static Path normalize(Path value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        return value.toAbsolutePath().normalize();
    }

    private static Duration positive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    public static final class Builder {

        private List<String> command;
        private Path processWorkingDirectory = Path.of(".");
        private Path runWorkspaceRoot = Path.of(".flower-ai-harness", "agent-cli-runs");
        private String requestFileName = "run-request.json";
        private String resultFileName = "run-result.json";
        private Duration maxExecutionTime = Duration.ofMinutes(15);
        private Duration killGracePeriod = Duration.ofSeconds(5);
        private AgentCliEnvironmentPolicy environmentPolicy =
                AgentCliEnvironmentPolicy.subscriptionSafeDefaults();
        private AgentCliProgressListener progressListener = AgentCliProgressListener.noOp();
        private int maxStderrTailCharacters = 8192;

        private Builder() {
        }

        public Builder command(List<String> value) {
            command = value;
            return this;
        }

        public Builder processWorkingDirectory(Path value) {
            processWorkingDirectory = value;
            return this;
        }

        public Builder runWorkspaceRoot(Path value) {
            runWorkspaceRoot = value;
            return this;
        }

        public Builder requestFileName(String value) {
            requestFileName = value;
            return this;
        }

        public Builder resultFileName(String value) {
            resultFileName = value;
            return this;
        }

        public Builder maxExecutionTime(Duration value) {
            maxExecutionTime = value;
            return this;
        }

        public Builder killGracePeriod(Duration value) {
            killGracePeriod = value;
            return this;
        }

        public Builder environmentPolicy(AgentCliEnvironmentPolicy value) {
            environmentPolicy = value;
            return this;
        }

        public Builder progressListener(AgentCliProgressListener value) {
            progressListener = value;
            return this;
        }

        public Builder maxStderrTailCharacters(int value) {
            maxStderrTailCharacters = value;
            return this;
        }

        public AgentCliGatewayConfig build() {
            return new AgentCliGatewayConfig(this);
        }
    }
}

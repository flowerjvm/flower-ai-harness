package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCliModelGatewayTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsRequestAndSuccessfulResult() throws Exception {
        try (AgentCliModelGateway gateway = gateway("success")) {
            AiModelCall call = gateway.submit(request("success"));

            awaitStatus(call, AiModelCallStatus.READY);

            assertThat(call.result().rawText()).isEqualTo("{\"ok\":true}");
            assertThat(call.result().metadata().inputTokens()).contains(11);
            assertThat(call.result().metadata().outputTokens()).contains(7);
            assertThat(call.result().metadata().providerTrace())
                    .containsEntry("runner.backend", "success")
                    .containsKey("runDirectory");
            Path runDirectory = Path.of(call.result().metadata().providerTrace().get("runDirectory"));
            JsonNode request = OBJECT_MAPPER.readTree(runDirectory.resolve("run-request.json").toFile());
            assertThat(request.path("contractVersion").asText()).isEqualTo("1");
            assertThat(request.path("model").path("name").asText()).isEqualTo("success");
            assertThat(request.path("prompt").path("messages").path(0).path("role").asText())
                    .isEqualTo("SYSTEM");
            assertThat(request.path("options").path(AgentCliOptions.RUNNER_MODE).asText())
                    .isEqualTo("read-only");
            assertThat(Files.readString(runDirectory.resolve("process.json")))
                    .doesNotContain("synthetic-secret");
        }
    }

    @Test
    void acceptsAnEmptySuccessfulOutput() {
        try (AgentCliModelGateway gateway = gateway("empty")) {
            AiModelCall call = gateway.submit(request("empty"));
            awaitStatus(call, AiModelCallStatus.READY);
            assertThat(call.result().rawText()).isEmpty();
        }
    }

    @Test
    void pollRemainsNonBlockingDuringDelayedExecution() {
        try (AgentCliModelGateway gateway = gateway("delay")) {
            AiModelCall call = gateway.submit(request("delay"));

            assertThat(call.poll()).isEqualTo(AiModelCallStatus.PENDING);
            awaitStatus(call, AiModelCallStatus.READY);
        }
    }

    @Test
    void dispatchesValidProgressAndRetainsMalformedLines() throws Exception {
        List<AgentCliProgressEvent> events = new CopyOnWriteArrayList<>();
        try (AgentCliModelGateway gateway = gateway(
                "progress",
                AgentCliEnvironmentPolicy.subscriptionSafeDefaults(),
                events::add,
                Duration.ofSeconds(5))) {
            AiModelCall call = gateway.submit(request("progress"));
            awaitStatus(call, AiModelCallStatus.READY);
            awaitCondition(() -> events.size() == 2, Duration.ofSeconds(2));

            assertThat(events).extracting(AgentCliProgressEvent::type)
                    .containsExactly("started", "tool");
            Path runDirectory = Path.of(call.result().metadata().providerTrace().get("runDirectory"));
            assertThat(Files.readString(runDirectory.resolve("stdout.log")))
                    .contains("this is retained but ignored as progress");
        }
    }

    @Test
    void mapsRunnerReportedFailure() {
        try (AgentCliModelGateway gateway = gateway("runner-failure")) {
            AiModelCall call = gateway.submit(request("runner-failure"));
            awaitStatus(call, AiModelCallStatus.FAILED);

            assertThat(call.error()).isInstanceOfSatisfying(
                    AgentCliExecutionException.class,
                    failure -> {
                        assertThat(failure.errorCode()).contains("AUTH_REQUIRED");
                        assertThat(failure.retryable()).isFalse();
                    });
        }
    }

    @Test
    void mapsNonZeroExitAndStderrTail() {
        try (AgentCliModelGateway gateway = gateway("exit-error")) {
            AiModelCall call = gateway.submit(request("exit-error"));
            awaitStatus(call, AiModelCallStatus.FAILED);

            assertThat(call.error()).isInstanceOfSatisfying(
                    AgentCliExecutionException.class,
                    failure -> {
                        assertThat(failure.exitCode()).hasValue(7);
                        assertThat(failure.stderrTail()).contains("synthetic runner failure");
                    });
        }
    }

    @Test
    void rejectsMissingAndMalformedResultFiles() {
        for (String scenario : List.of("missing-result", "malformed-result")) {
            try (AgentCliModelGateway gateway = gateway(scenario)) {
                AiModelCall call = gateway.submit(request(scenario));
                awaitStatus(call, AiModelCallStatus.FAILED);
                assertThat(call.error()).isInstanceOf(AgentCliProtocolException.class);
            }
        }
    }

    @Test
    void enforcesEffectiveTimeoutAndTerminatesProcess() throws Exception {
        try (AgentCliModelGateway gateway = gateway(
                "hang",
                AgentCliEnvironmentPolicy.subscriptionSafeDefaults(),
                AgentCliProgressListener.noOp(),
                Duration.ofMillis(200))) {
            AiModelCall call = gateway.submit(request("hang"));
            awaitStatus(call, AiModelCallStatus.FAILED);

            AgentCliTimeoutException failure = (AgentCliTimeoutException) call.error();
            assertThat(failure.timeout()).isEqualTo(Duration.ofMillis(200));
            long pid = OBJECT_MAPPER.readTree(
                    failure.runDirectory().resolve("process.json").toFile()).path("pid").asLong();
            awaitCondition(
                    () -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) == false,
                    Duration.ofSeconds(5));
        }
    }

    @Test
    void cancellationTerminatesParentAndChildProcess() throws Exception {
        try (AgentCliModelGateway gateway = gateway(
                "child-hang",
                AgentCliEnvironmentPolicy.subscriptionSafeDefaults(),
                AgentCliProgressListener.noOp(),
                Duration.ofSeconds(20))) {
            AiModelCall call = gateway.submit(request("child-hang"));
            Path runDirectory = awaitRunDirectory(call.callId());
            Path childPidFile = runDirectory.resolve("child.pid");
            awaitCondition(
                    () -> Files.isRegularFile(childPidFile)
                            && !Files.readString(childPidFile).isBlank(),
                    Duration.ofSeconds(5));
            long childPid = Long.parseLong(Files.readString(childPidFile).trim());

            call.cancel();

            assertThat(call.poll()).isEqualTo(AiModelCallStatus.CANCELLED);
            awaitCondition(
                    () -> ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false) == false,
                    Duration.ofSeconds(8));
        }
    }

    @Test
    void isolatesConcurrentCallWorkspaces() {
        try (AgentCliModelGateway gateway = gateway("concurrent")) {
            List<AiModelCall> calls = IntStream.range(0, 6)
                    .mapToObj(index -> gateway.submit(request("concurrent")))
                    .toList();
            calls.forEach(call -> awaitStatus(call, AiModelCallStatus.READY));

            assertThat(calls)
                    .extracting(call -> call.result().metadata().providerTrace().get("runDirectory"))
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void appliesConfiguredEnvironmentWithoutLeakingDeniedVariable() {
        AgentCliEnvironmentPolicy policy = AgentCliEnvironmentPolicy.builder()
                .allowName("PATH")
                .allowName("PATHEXT")
                .allowName("SYSTEMROOT")
                .allowName("COMSPEC")
                .variable("AGENT_CLI_TEST_ALLOWED", "yes")
                .denyName("AGENT_CLI_TEST_DENIED")
                .build();
        try (AgentCliModelGateway gateway = gateway(
                "environment",
                policy,
                AgentCliProgressListener.noOp(),
                Duration.ofSeconds(5))) {
            AiModelCall call = gateway.submit(request("environment"));
            awaitStatus(call, AiModelCallStatus.READY);

            assertThat(call.result().rawText())
                    .contains("\"allowed\":\"yes\"")
                    .contains("\"deniedPresent\":false");
        }
    }

    @Test
    void rejectsNonJsonSafeProviderOptionsBeforeProcessStart() {
        try (AgentCliModelGateway gateway = gateway("success")) {
            AiModelRequest invalid = request("success").withOptions(
                    ProviderOptions.empty().with("unsupported", new Object()));
            AiModelCall call = gateway.submit(invalid);
            awaitStatus(call, AiModelCallStatus.FAILED);

            assertThat(call.error()).hasMessageContaining("not JSON-safe");
        }
    }

    @Test
    void closeCancelsActiveCallsAndRejectsNewOnes() {
        AgentCliModelGateway gateway = gateway("hang");
        AiModelCall call = gateway.submit(request("hang"));

        gateway.close();

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.CANCELLED);
        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> gateway.submit(request("success")))
                .withMessageContaining("closed");
    }

    private AgentCliModelGateway gateway(String scenario) {
        return gateway(
                scenario,
                AgentCliEnvironmentPolicy.subscriptionSafeDefaults(),
                AgentCliProgressListener.noOp(),
                Duration.ofSeconds(5));
    }

    private AgentCliModelGateway gateway(
            String scenario,
            AgentCliEnvironmentPolicy environmentPolicy,
            AgentCliProgressListener listener,
            Duration maxExecutionTime
    ) {
        Path workingDirectory = temporaryDirectory.resolve("work-" + scenario);
        Path runRoot = temporaryDirectory.resolve("runs-" + scenario);
        try {
            Files.createDirectories(workingDirectory);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return new AgentCliModelGateway(AgentCliGatewayConfig.builder()
                .command(testRunnerCommand())
                .processWorkingDirectory(workingDirectory)
                .runWorkspaceRoot(runRoot)
                .maxExecutionTime(maxExecutionTime)
                .killGracePeriod(Duration.ofMillis(200))
                .environmentPolicy(environmentPolicy)
                .progressListener(listener)
                .build());
    }

    private List<String> testRunnerCommand() {
        String executable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java").toString();
        return List.of(
                executable,
                "-cp",
                System.getProperty("java.class.path"),
                AgentCliTestRunner.class.getName(),
                "--request",
                "{requestFile}",
                "--result",
                "{resultFile}",
                "--scenario",
                "{model}");
    }

    private AiModelRequest request(String scenario) {
        return new AiModelRequest(
                new ModelId("agent-cli", scenario),
                new RenderedPrompt(
                        List.of(
                                new RenderedPrompt.Message(
                                        RenderedPrompt.Role.SYSTEM,
                                        "Return JSON only."),
                                new RenderedPrompt.Message(
                                        RenderedPrompt.Role.USER,
                                        "Inspect the workspace.")),
                        new PromptVersion("agent-cli-test", "1.0.0")),
                ProviderOptions.of(Map.of(
                        AgentCliOptions.RUNNER_MODE, "read-only",
                        "nested", Map.of("enabled", true),
                        "secretExample", "synthetic-secret")),
                Duration.ofSeconds(10));
    }

    private Path awaitRunDirectory(String callId) {
        Path expected = temporaryDirectory.resolve("runs-child-hang").resolve(callId);
        awaitCondition(() -> Files.isDirectory(expected), Duration.ofSeconds(5));
        return expected;
    }

    private static void awaitStatus(AiModelCall call, AiModelCallStatus expected) {
        awaitCondition(() -> call.poll() == expected, Duration.ofSeconds(10));
        assertThat(call.poll()).isEqualTo(expected);
    }

    private static void awaitCondition(CheckedBoolean condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<Exception> failures = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.get()) {
                    return;
                }
            } catch (Exception ex) {
                failures.add(ex);
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting condition", ex);
            }
        }
        AssertionError error = new AssertionError("Condition did not become true within " + timeout);
        failures.forEach(error::addSuppressed);
        throw error;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @FunctionalInterface
    private interface CheckedBoolean {

        boolean get() throws Exception;
    }
}

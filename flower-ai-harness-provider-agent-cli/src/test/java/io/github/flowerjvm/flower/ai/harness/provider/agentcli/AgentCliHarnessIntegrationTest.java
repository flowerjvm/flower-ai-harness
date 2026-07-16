package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationError;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCliHarnessIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validationFailureCreatesASecondIsolatedAgentCall() throws Exception {
        Path runRoot = temporaryDirectory.resolve("runs");
        Path workingDirectory = temporaryDirectory.resolve("work");
        Files.createDirectories(workingDirectory);
        AgentCliGatewayConfig config = AgentCliGatewayConfig.builder()
                .command(testRunnerCommand())
                .processWorkingDirectory(workingDirectory)
                .runWorkspaceRoot(runRoot)
                .maxExecutionTime(Duration.ofSeconds(5))
                .killGracePeriod(Duration.ofMillis(200))
                .build();

        try (AgentCliModelGateway gateway = new AgentCliModelGateway(config)) {
            AiHarnessSpec<String, String> spec = AiHarnessSpec.<String, String>builder()
                    .harnessId("agent-cli-integration")
                    .defaultModelId(new ModelId("agent-cli", "retry"))
                    .defaultTimeout(Duration.ofSeconds(5))
                    .promptVersion(new PromptVersion("agent-cli-integration", "1.0.0"))
                    .promptBuilder((input, context) -> new RenderedPrompt(
                            List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, input)),
                            new PromptVersion("agent-cli-integration", "1.0.0")))
                    .validator(response -> response.rawText().contains("\"ok\":true")
                            ? new ValidationResult.Valid<>(response.rawText())
                            : new ValidationResult.Invalid<>(List.of(
                                    new ValidationError("$", "INVALID_OUTPUT", "Expected ok=true"))))
                    .refinePolicy(new MaxAttemptsRefinePolicy(2))
                    .findingExtractor((value, context) -> List.of())
                    .findingSink((findings, context) -> {
                    })
                    .build();
            AiHarnessFlow flow = new AiHarnessFlowFactory<>(gateway, spec, () -> Instant.EPOCH)
                    .createFlow("Inspect the workspace.");

            runToTerminal(flow);

            assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
            assertThat(flow.context().attempt()).isEqualTo(2);
            assertThat(flow.context().latestValidation()).hasValueSatisfying(
                    validation -> assertThat(validation.isValid()).isTrue());
            try (var directories = Files.list(runRoot)) {
                assertThat(directories.filter(Files::isDirectory).toList()).hasSize(2);
            }
        }
    }

    private void runToTerminal(AiHarnessFlow harnessFlow) throws Exception {
        ManualClock clock = new ManualClock(0L);
        Worker worker = Worker.builder("agent-cli-integration").build();
        Engine engine = Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();
        engine.attach();
        worker.submit(harnessFlow.flow());
        for (int index = 0; index < 1000 && !harnessFlow.flow().state().isTerminal(); index++) {
            worker.tickOnce();
            clock.advance(1L);
            Thread.sleep(5L);
        }
        assertThat(harnessFlow.flow().state().isTerminal()).isTrue();
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

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

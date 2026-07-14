package io.github.flowerjvm.flower.ai.harness.control;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalControlTest {

    @Test
    void manualCancellationTokenRecordsFirstReasonOnly() {
        ManualAiCancellationToken token = new ManualAiCancellationToken();

        assertThat(token.isCancellationRequested()).isFalse();
        assertThat(token.cancel("user cancelled")).isTrue();
        assertThat(token.cancel("second reason")).isFalse();

        assertThat(token.isCancellationRequested()).isTrue();
        assertThat(token.cancellationReason()).contains("user cancelled");
    }

    @Test
    void maxAttemptsBudgetRejectsAttemptBeyondLimit() {
        MaxAttemptsBudgetPolicy policy = new MaxAttemptsBudgetPolicy(2);
        AiHarnessRunContext context = new AiHarnessRunContext(
                new AiHarnessRunId("run-1"),
                "test",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);

        AiBudgetDecision allowed = policy.evaluate(new AiBudgetContext(context, TestRequests.request(), 2));
        AiBudgetDecision rejected = policy.evaluate(new AiBudgetContext(context, TestRequests.request(), 3));

        assertThat(allowed.allowed()).isTrue();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.rejectionReason()).contains("AI budget exhausted: maxAttempts=2");
    }

    @Test
    void semaphoreGovernorCapsConcurrentPermitsWithoutBlocking() {
        SemaphoreAiResourceGovernor governor = new SemaphoreAiResourceGovernor(1);
        AiHarnessRunContext context = new AiHarnessRunContext(
                new AiHarnessRunId("run-1"),
                "test",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);

        AiResourcePermit first = governor.tryAcquire(TestRequests.request(), context).orElseThrow();

        assertThat(governor.availablePermits()).isZero();
        assertThat(governor.tryAcquire(TestRequests.request(), context)).isEmpty();

        first.close();
        assertThat(governor.availablePermits()).isEqualTo(1);
        assertThat(governor.tryAcquire(TestRequests.request(), context)).isPresent();
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new MaxAttemptsBudgetPolicy(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemaphoreAiResourceGovernor(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

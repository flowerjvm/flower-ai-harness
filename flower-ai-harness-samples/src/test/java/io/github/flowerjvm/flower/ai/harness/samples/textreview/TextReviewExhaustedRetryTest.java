package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.flowerjvm.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.flowerjvm.flower.core.flow.FlowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static io.github.flowerjvm.flower.ai.harness.test.assertion.AiHarnessRunAssertions.assertThatRun;
import static org.assertj.core.api.Assertions.assertThat;

class TextReviewExhaustedRetryTest {

    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void repeatedMalformedJsonFailsAfterMaxAttempts() {
        InMemoryFindingSink sink = new InMemoryFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("{"),
                new FakeResponseProgram.ImmediateText("{"),
                new FakeResponseProgram.ImmediateText("{"))));

        AiHarnessFlow flow = harness.runHarness(
                TextReviewHarness.spec(sink),
                new TextReviewInput("deployment without rollback"));

        assertThat(flow.flow().state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.flow().failureCause()).hasMessageContaining("Validation failed after 3 attempts");
        assertThatRun(flow.context()).hasAttempts(3);
        assertThat(sink.findings()).isEmpty();
        assertThat(harness.gateway().recordedCalls()).hasSize(3);
    }
}

package io.github.parkkevinsb.flower.ai.harness.samples.textreview;

import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.github.parkkevinsb.flower.ai.harness.test.assertion.AiFindingAssertions.assertThatFindings;
import static io.github.parkkevinsb.flower.ai.harness.test.assertion.AiHarnessRunAssertions.assertThatRun;
import static org.assertj.core.api.Assertions.assertThat;

class TextReviewHappyPathTest {

    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void fakeReturnsValidJsonAndHarnessEmitsFindings() {
        InMemoryFindingSink sink = new InMemoryFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText(
                TextReviewSampleApplication.sampleResponseJson()));

        AiHarnessFlow flow = harness.runHarness(
                TextReviewHarness.spec(sink),
                new TextReviewInput("deployment without rollback"));

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThatRun(flow.context())
                .hasAttempts(1)
                .hasValidValidation()
                .hasNoCallError()
                .hasFindings(1);
        assertThatFindings(sink.findings())
                .hasSize(1)
                .hasCode("MISSING_ROLLBACK")
                .hasMessageContaining("rollback");
        assertThat(harness.gateway().recordedCalls()).hasSize(1);
    }
}

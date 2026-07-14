package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.flowerjvm.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.flowerjvm.flower.core.flow.FlowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static io.github.flowerjvm.flower.ai.harness.test.assertion.AiFindingAssertions.assertThatFindings;
import static io.github.flowerjvm.flower.ai.harness.test.assertion.AiHarnessRunAssertions.assertThatRun;
import static org.assertj.core.api.Assertions.assertThat;

class TextReviewTransportFailureTest {

    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void transportFailureRetriesAndSecondAttemptSucceeds() {
        InMemoryFindingSink sink = new InMemoryFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateError(new RuntimeException("provider unavailable")),
                new FakeResponseProgram.ImmediateText(TextReviewSampleApplication.sampleResponseJson()))));

        AiHarnessFlow flow = harness.runHarness(
                TextReviewHarness.spec(sink),
                new TextReviewInput("deployment without rollback"));

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThatRun(flow.context())
                .hasAttempts(2)
                .hasValidValidation()
                .hasNoCallError();
        assertThatFindings(sink.findings()).hasCode("MISSING_ROLLBACK");
        assertThat(harness.gateway().recordedCalls()).hasSize(2);
    }
}

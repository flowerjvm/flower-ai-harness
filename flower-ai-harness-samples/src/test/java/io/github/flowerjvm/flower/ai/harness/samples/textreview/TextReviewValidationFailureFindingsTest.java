package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.spi.TraceListener;
import io.github.flowerjvm.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.flowerjvm.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.flowerjvm.flower.core.flow.FlowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextReviewValidationFailureFindingsTest {

    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void traceListenerSeesRefineTriggeredAfterValidationFailure() {
        InMemoryFindingSink sink = new InMemoryFindingSink();
        RecordingTraceListener listener = new RecordingTraceListener();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("{"),
                new FakeResponseProgram.ImmediateText(TextReviewSampleApplication.sampleResponseJson()))));

        AiHarnessFlow flow = harness.runHarness(
                TextReviewHarness.spec(sink, listener),
                new TextReviewInput("deployment without rollback"));

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(listener.refineTriggeredCount).isEqualTo(1);
        assertThat(listener.lastRunId).isEqualTo(flow.context().runId().value());
        assertThat(listener.lastRetryPromptMessageCount).isGreaterThan(2);
    }

    private static final class RecordingTraceListener implements TraceListener {

        private int refineTriggeredCount;
        private String lastRunId;
        private int lastRetryPromptMessageCount;

        @Override
        public void onRefineTriggered(AiHarnessRunContext ctx, AiModelRequest nextRequest) {
            refineTriggeredCount++;
            lastRunId = ctx.runId().value();
            lastRetryPromptMessageCount = nextRequest.prompt().messages().size();
        }
    }
}

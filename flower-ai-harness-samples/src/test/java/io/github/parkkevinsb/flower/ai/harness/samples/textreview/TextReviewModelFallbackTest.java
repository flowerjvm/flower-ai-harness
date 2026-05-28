package io.github.parkkevinsb.flower.ai.harness.samples.textreview;

import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.refine.ModelFallbackPlan;
import io.github.parkkevinsb.flower.ai.harness.refine.ModelFallbackRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.ai.harness.test.fake.RequestMatcher;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.github.parkkevinsb.flower.ai.harness.test.assertion.AiFindingAssertions.assertThatFindings;
import static io.github.parkkevinsb.flower.ai.harness.test.assertion.AiHarnessRunAssertions.assertThatRun;
import static org.assertj.core.api.Assertions.assertThat;

class TextReviewModelFallbackTest {

    private static final ModelId CHEAP = ModelId.parse("fake:text-review-cheap");
    private static final ModelId STRONG = ModelId.parse("fake:text-review-strong");

    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void failedCheapModelFallsBackToStrongModel() {
        InMemoryFindingSink sink = new InMemoryFindingSink();
        harness.gateway()
                .respondTo(
                        RequestMatcher.modelEquals(CHEAP),
                        new FakeResponseProgram.ImmediateError(new RuntimeException("cheap model unavailable")))
                .respondTo(
                        RequestMatcher.modelEquals(STRONG),
                        new FakeResponseProgram.ImmediateText(TextReviewSampleApplication.sampleResponseJson()));
        ModelFallbackRefinePolicy policy = new ModelFallbackRefinePolicy(ModelFallbackPlan.builder()
                .model(CHEAP, 1)
                .model(STRONG, 1)
                .build());

        AiHarnessFlow flow = harness.runHarness(
                TextReviewHarness.spec(sink, CHEAP, policy),
                new TextReviewInput("deployment without rollback"));

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThatRun(flow.context())
                .hasAttempts(2)
                .hasValidValidation()
                .hasNoCallError();
        assertThatFindings(sink.findings()).hasCode("MISSING_ROLLBACK");
        assertThat(harness.gateway().recordedCalls()).hasSize(2);
        assertThat(harness.gateway().recordedCalls().get(0).request().modelId()).isEqualTo(CHEAP);
        assertThat(harness.gateway().recordedCalls().get(1).request().modelId()).isEqualTo(STRONG);
    }
}

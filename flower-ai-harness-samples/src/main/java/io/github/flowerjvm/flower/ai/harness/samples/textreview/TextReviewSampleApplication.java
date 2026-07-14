package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.test.fake.FakeAiModelGateway;
import io.github.flowerjvm.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;

/**
 * Runnable text-review sample using the fake provider.
 */
public final class TextReviewSampleApplication {

    private TextReviewSampleApplication() {
    }

    public static void main(String[] args) {
        FixedAiHarnessClock harnessClock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(harnessClock)
                .respondToAny(new FakeResponseProgram.ImmediateText(sampleResponseJson()));
        InMemoryFindingSink sink = new InMemoryFindingSink();
        AiHarnessFlowFactory<TextReviewInput, TextReviewDraft> factory =
                new AiHarnessFlowFactory<>(gateway, TextReviewHarness.spec(sink), harnessClock);

        AiHarnessFlow harnessFlow = factory.createFlow(new TextReviewInput(
                "This deployment plan maybe works, but it never mentions rollback."));
        runToTerminal(harnessFlow, harnessClock);

        if (harnessFlow.flow().state() != FlowState.FINISHED) {
            throw new IllegalStateException("sample failed: " + harnessFlow.flow().failureCause());
        }

        System.out.println("runId=" + harnessFlow.context().runId().value());
        System.out.println("attempts=" + harnessFlow.context().attempt());
        for (AiFinding finding : sink.findings()) {
            System.out.println(finding.severity() + " " + finding.code() + " - " + finding.message());
            if (!finding.evidence().isBlank()) {
                System.out.println("  evidence: " + finding.evidence());
            }
        }
    }

    public static String sampleResponseJson() {
        return """
                {
                  "issues": [
                    {
                      "code": "MISSING_ROLLBACK",
                      "severity": "MEDIUM",
                      "message": "The text mentions deployment without a rollback plan.",
                      "quote": "never mentions rollback"
                    }
                  ]
                }
                """;
    }

    private static void runToTerminal(AiHarnessFlow harnessFlow, FixedAiHarnessClock harnessClock) {
        ManualClock flowerClock = new ManualClock(harnessClock.instant().toEpochMilli());
        Worker worker = Worker.builder("sample").build();
        Engine engine = Engine.builder()
                .clock(flowerClock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();
        engine.attach();
        worker.submit(harnessFlow.flow());

        for (int i = 0; i < 100 && !harnessFlow.flow().state().isTerminal(); i++) {
            worker.tickOnce();
            harnessClock.tick();
            flowerClock.advance(1);
        }
        if (!harnessFlow.flow().state().isTerminal()) {
            throw new IllegalStateException("sample did not finish within 100 ticks");
        }
    }
}

package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAiModelGatewayImmediateTest {

    @Test
    void returnsImmediateText() {
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondToAny(new FakeResponseProgram.ImmediateText("{\"ok\":true}"));

        AiModelCall call = gateway.submit(FakeGatewayTestSupport.request("return ok"));

        assertThat(call.callId()).isEqualTo("fake-call-1");
        assertThat(call.poll()).isEqualTo(AiModelCallStatus.READY);
        assertThat(call.result().rawText()).isEqualTo("{\"ok\":true}");
        assertThat(gateway.recordedCalls()).hasSize(1);
        assertThat(gateway.lastCall().terminalStatus()).isEqualTo(AiModelCallStatus.READY);
    }

    @Test
    void requestMatcherCanMatchPromptAndModel() {
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondTo(
                        RequestMatcher.and(
                                RequestMatcher.modelEquals(FakeGatewayTestSupport.MODEL),
                                RequestMatcher.promptContains("needle")),
                        new FakeResponseProgram.ImmediateText("matched"));

        AiModelCall call = gateway.submit(FakeGatewayTestSupport.request("find needle"));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.READY);
        assertThat(call.result().rawText()).isEqualTo("matched");
    }
}

package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAiModelGatewayDelayedTest {

    @Test
    void staysPendingUntilReadyTick() {
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondToAny(new FakeResponseProgram.DelayedText("done", 2));
        AiModelCall call = gateway.submit(FakeGatewayTestSupport.request("slow"));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.PENDING);
        assertThat(gateway.lastCall().terminalStatus()).isEqualTo(AiModelCallStatus.PENDING);

        clock.tick();
        assertThat(call.poll()).isEqualTo(AiModelCallStatus.PENDING);

        clock.tick();
        assertThat(call.poll()).isEqualTo(AiModelCallStatus.READY);
        assertThat(call.result().rawText()).isEqualTo("done");
        assertThat(gateway.lastCall().terminalStatus()).isEqualTo(AiModelCallStatus.READY);
    }
}

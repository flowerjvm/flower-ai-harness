package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiModelGatewayErrorInjectionTest {

    @Test
    void returnsImmediateError() {
        RuntimeException error = new RuntimeException("provider down");
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondToAny(new FakeResponseProgram.ImmediateError(error));

        AiModelCall call = gateway.submit(FakeGatewayTestSupport.request("fail"));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.FAILED);
        assertThat(call.error()).isSameAs(error);
        assertThat(gateway.lastCall().terminalStatus()).isEqualTo(AiModelCallStatus.FAILED);
    }

    @Test
    void returnsDelayedError() {
        RuntimeException error = new RuntimeException("timeout");
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondToAny(new FakeResponseProgram.DelayedError(error, 1));
        AiModelCall call = gateway.submit(FakeGatewayTestSupport.request("fail later"));

        assertThat(call.poll()).isEqualTo(AiModelCallStatus.PENDING);

        clock.tick();
        assertThat(call.poll()).isEqualTo(AiModelCallStatus.FAILED);
        assertThat(call.error()).isSameAs(error);
    }

    @Test
    void throwsWhenNoProgramMatches() {
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock);

        assertThatThrownBy(() -> gateway.submit(FakeGatewayTestSupport.request("unmatched")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("No fake response program matched");
    }
}

package io.github.flowerjvm.flower.ai.harness.test.fake;

import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.test.time.FixedAiHarnessClock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiModelGatewaySequenceTest {

    @Test
    void returnsSequenceStepPerMatchedRequest() {
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondToAny(new FakeResponseProgram.Sequence(List.of(
                        new FakeResponseProgram.ImmediateText("invalid"),
                        new FakeResponseProgram.ImmediateText("valid"))));

        assertThat(gateway.submit(FakeGatewayTestSupport.request("first")).result().rawText())
                .isEqualTo("invalid");
        assertThat(gateway.submit(FakeGatewayTestSupport.request("second")).result().rawText())
                .isEqualTo("valid");

        assertThat(gateway.recordedCalls())
                .extracting(call -> call.request().prompt().messages().get(0).content())
                .containsExactly("first", "second");
        assertThat(gateway.recordedCalls())
                .extracting(RecordedCall::terminalStatus)
                .containsExactly(AiModelCallStatus.READY, AiModelCallStatus.READY);
    }

    @Test
    void throwsWhenSequenceIsExhausted() {
        FixedAiHarnessClock clock = new FixedAiHarnessClock();
        FakeAiModelGateway gateway = new FakeAiModelGateway(clock)
                .respondToAny(new FakeResponseProgram.Sequence(List.of(
                        new FakeResponseProgram.ImmediateText("only"))));

        gateway.submit(FakeGatewayTestSupport.request("first")).poll();

        assertThatThrownBy(() -> gateway.submit(FakeGatewayTestSupport.request("second")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("sequence exhausted");
    }
}

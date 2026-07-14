package io.github.flowerjvm.flower.ai.harness.run;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AiHarnessRunStoreTest {

    @Test
    void snapshotCapturesOperationalState() {
        AiHarnessRunContext context = new AiHarnessRunContext(
                new AiHarnessRunId("run-1"),
                "test",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);
        context.setCurrentRequest(TestRunRequests.request());
        context.beginModelCall(new StaticCall("call-1"));
        context.recordResponse(new AiModelResponse("ok", ModelId.parse("fake:model"), null));
        context.markSucceeded();

        AiHarnessRunSnapshot snapshot = AiHarnessRunSnapshot.from(context, Instant.EPOCH.plusSeconds(1));

        assertThat(snapshot.runId()).isEqualTo(context.runId());
        assertThat(snapshot.status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
        assertThat(snapshot.attempt()).isEqualTo(1);
        assertThat(snapshot.currentRequest()).contains(context.currentRequest());
        assertThat(snapshot.currentCallId()).contains("call-1");
        assertThat(snapshot.latestResponse()).isPresent();
    }

    @Test
    void inMemoryRunStoreSavesLatestSnapshotByRunId() {
        InMemoryAiHarnessRunStore store = new InMemoryAiHarnessRunStore();
        AiHarnessRunContext context = new AiHarnessRunContext(
                new AiHarnessRunId("run-1"),
                "test",
                new PromptVersion("test", "1.0.0"),
                Instant.EPOCH);
        AiHarnessRunSnapshot queued = AiHarnessRunSnapshot.from(context, Instant.EPOCH);
        context.markFailed("boom");
        AiHarnessRunSnapshot failed = AiHarnessRunSnapshot.from(context, Instant.EPOCH.plusSeconds(1));

        store.save(queued);
        store.save(failed);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find(context.runId())).contains(failed);
        assertThat(AiHarnessRunStore.noop().find(context.runId())).isEmpty();
    }

    private record StaticCall(String callId) implements AiModelCall {

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.READY;
        }

        @Override
        public AiModelResponse result() {
            return new AiModelResponse("ok", ModelId.parse("fake:model"), null);
        }

        @Override
        public Throwable error() {
            return null;
        }

        @Override
        public void cancel() {
        }
    }
}

package io.github.flowerjvm.flower.ai.harness.provider.openaicompatible;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class OpenAiCompatibleModelCall implements AiModelCall {

    private final String callId;
    private final CompletableFuture<?> transportFuture;
    private final CompletableFuture<AiModelResponse> resultFuture;

    OpenAiCompatibleModelCall(
            String callId,
            CompletableFuture<?> transportFuture,
            CompletableFuture<AiModelResponse> resultFuture
    ) {
        this.callId = Objects.requireNonNull(callId, "callId must not be null");
        this.transportFuture = Objects.requireNonNull(transportFuture, "transportFuture must not be null");
        this.resultFuture = Objects.requireNonNull(resultFuture, "resultFuture must not be null");
    }

    @Override
    public String callId() {
        return callId;
    }

    @Override
    public AiModelCallStatus poll() {
        if (resultFuture.isCancelled()) {
            return AiModelCallStatus.CANCELLED;
        }
        if (!resultFuture.isDone()) {
            return AiModelCallStatus.PENDING;
        }
        return error() == null ? AiModelCallStatus.READY : AiModelCallStatus.FAILED;
    }

    @Override
    public AiModelResponse result() {
        if (poll() != AiModelCallStatus.READY) {
            throw new IllegalStateException("OpenAI-compatible call is not ready: " + callId);
        }
        return resultFuture.join();
    }

    @Override
    public Throwable error() {
        if (!resultFuture.isDone() || resultFuture.isCancelled()) {
            return null;
        }
        try {
            resultFuture.join();
            return null;
        } catch (CompletionException ex) {
            return unwrap(ex);
        } catch (CancellationException ex) {
            return ex;
        }
    }

    @Override
    public void cancel() {
        transportFuture.cancel(true);
        resultFuture.cancel(true);
    }

    private static Throwable unwrap(CompletionException ex) {
        return ex.getCause() == null ? ex : ex.getCause();
    }
}

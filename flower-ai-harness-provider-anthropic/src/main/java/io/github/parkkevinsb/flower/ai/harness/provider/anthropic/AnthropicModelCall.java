package io.github.parkkevinsb.flower.ai.harness.provider.anthropic;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class AnthropicModelCall implements AiModelCall {

    private final String callId;
    private final CompletableFuture<AiModelResponse> future;

    AnthropicModelCall(String callId, CompletableFuture<AiModelResponse> future) {
        this.callId = Objects.requireNonNull(callId, "callId must not be null");
        this.future = Objects.requireNonNull(future, "future must not be null");
    }

    @Override
    public String callId() {
        return callId;
    }

    @Override
    public AiModelCallStatus poll() {
        if (future.isCancelled()) {
            return AiModelCallStatus.CANCELLED;
        }
        if (!future.isDone()) {
            return AiModelCallStatus.PENDING;
        }
        return error() == null ? AiModelCallStatus.READY : AiModelCallStatus.FAILED;
    }

    @Override
    public AiModelResponse result() {
        if (poll() != AiModelCallStatus.READY) {
            throw new IllegalStateException("Anthropic call is not ready: " + callId);
        }
        return future.join();
    }

    @Override
    public Throwable error() {
        if (!future.isDone() || future.isCancelled()) {
            return null;
        }
        try {
            future.join();
            return null;
        } catch (CompletionException ex) {
            return unwrap(ex);
        } catch (CancellationException ex) {
            return ex;
        }
    }

    @Override
    public void cancel() {
        future.cancel(true);
    }

    private static Throwable unwrap(CompletionException ex) {
        return ex.getCause() == null ? ex : ex.getCause();
    }
}

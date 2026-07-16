package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

final class AgentCliModelCall implements AiModelCall {

    private final String callId;
    private final Executor cancellationExecutor;
    private final AtomicReference<CallState> state =
            new AtomicReference<>(new CallState(AiModelCallStatus.PENDING, null, null));
    private final AtomicReference<Runnable> cancellationAction =
            new AtomicReference<>(() -> {
            });

    AgentCliModelCall(String callId, Executor cancellationExecutor) {
        this.callId = Objects.requireNonNull(callId, "callId must not be null");
        this.cancellationExecutor =
                Objects.requireNonNull(cancellationExecutor, "cancellationExecutor must not be null");
    }

    @Override
    public String callId() {
        return callId;
    }

    @Override
    public AiModelCallStatus poll() {
        return state.get().status();
    }

    @Override
    public AiModelResponse result() {
        if (poll() != AiModelCallStatus.READY) {
            throw new IllegalStateException("Agent CLI call is not ready: " + callId);
        }
        return state.get().response();
    }

    @Override
    public Throwable error() {
        return state.get().failure();
    }

    @Override
    public void cancel() {
        CallState current = state.get();
        if (current.status() == AiModelCallStatus.PENDING
                && state.compareAndSet(
                        current,
                        new CallState(AiModelCallStatus.CANCELLED, null, null))) {
            executeCancellation(cancellationAction.get());
        }
    }

    boolean isPending() {
        return state.get().status() == AiModelCallStatus.PENDING;
    }

    void attachCancellationAction(Runnable action) {
        cancellationAction.set(Objects.requireNonNull(action, "action must not be null"));
        if (state.get().status() == AiModelCallStatus.CANCELLED) {
            executeCancellation(action);
        }
    }

    void completeReady(AiModelResponse value) {
        Objects.requireNonNull(value, "value must not be null");
        CallState current = state.get();
        if (current.status() == AiModelCallStatus.PENDING) {
            state.compareAndSet(
                    current,
                    new CallState(AiModelCallStatus.READY, value, null));
        }
    }

    void completeFailed(Throwable value) {
        Objects.requireNonNull(value, "value must not be null");
        CallState current = state.get();
        if (current.status() == AiModelCallStatus.PENDING) {
            state.compareAndSet(
                    current,
                    new CallState(AiModelCallStatus.FAILED, null, value));
        }
    }

    private void executeCancellation(Runnable action) {
        try {
            cancellationExecutor.execute(action);
        } catch (RuntimeException ignored) {
            action.run();
        }
    }

    private record CallState(
            AiModelCallStatus status,
            AiModelResponse response,
            Throwable failure
    ) {
    }
}

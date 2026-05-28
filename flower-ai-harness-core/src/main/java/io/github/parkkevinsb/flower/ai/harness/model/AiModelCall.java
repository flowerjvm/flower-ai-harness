package io.github.parkkevinsb.flower.ai.harness.model;

/**
 * Non-blocking handle for one submitted model request.
 */
public interface AiModelCall {

    String callId();

    AiModelCallStatus poll();

    AiModelResponse result();

    Throwable error();

    void cancel();
}

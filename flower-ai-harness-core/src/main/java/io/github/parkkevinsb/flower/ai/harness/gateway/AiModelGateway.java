package io.github.parkkevinsb.flower.ai.harness.gateway;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;

/**
 * Provider-neutral port for submitting model requests.
 */
public interface AiModelGateway {

    AiModelCall submit(AiModelRequest request);
}

package io.github.parkkevinsb.flower.ai.harness.validate;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;

/**
 * Validates a raw model response into a typed structured value.
 */
public interface AiSchemaValidator<T> {

    ValidationResult<T> validate(AiModelResponse response);
}

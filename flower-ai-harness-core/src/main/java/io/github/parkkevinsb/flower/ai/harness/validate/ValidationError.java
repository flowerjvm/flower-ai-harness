package io.github.parkkevinsb.flower.ai.harness.validate;

import java.util.Objects;

/**
 * A machine-readable structural validation failure.
 */
public record ValidationError(
        String path,
        String code,
        String message
) {

    public ValidationError {
        path = path == null ? "" : path;
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}

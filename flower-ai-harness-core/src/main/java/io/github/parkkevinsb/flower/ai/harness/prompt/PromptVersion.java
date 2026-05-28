package io.github.parkkevinsb.flower.ai.harness.prompt;

import java.util.Objects;

/**
 * Stable prompt identity and version pair.
 */
public record PromptVersion(String id, String version) {

    private static final String SEPARATOR = "@";

    public PromptVersion {
        id = requireText(id, "id");
        version = requireText(version, "version");
    }

    public String asString() {
        return id + SEPARATOR + version;
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

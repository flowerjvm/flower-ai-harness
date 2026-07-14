package io.github.flowerjvm.flower.ai.harness.model;

import java.util.Objects;

/**
 * Provider-neutral model identifier in {@code provider:name} form.
 */
public record ModelId(String provider, String name) {

    private static final String SEPARATOR = ":";

    public ModelId {
        provider = requireText(provider, "provider");
        name = requireText(name, "name");
    }

    public static ModelId parse(String spec) {
        String value = requireText(spec, "spec");
        int separatorIndex = value.indexOf(SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
            throw new IllegalArgumentException("model spec must be in provider:name form");
        }
        return new ModelId(value.substring(0, separatorIndex), value.substring(separatorIndex + 1));
    }

    public String asString() {
        return provider + SEPARATOR + name;
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

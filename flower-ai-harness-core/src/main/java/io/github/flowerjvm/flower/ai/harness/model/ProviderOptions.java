package io.github.flowerjvm.flower.ai.harness.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, provider-specific option bag.
 */
public final class ProviderOptions {

    private static final ProviderOptions EMPTY = new ProviderOptions(Map.of());

    private final Map<String, Object> options;

    private ProviderOptions(Map<String, Object> options) {
        this.options = Map.copyOf(options);
    }

    public static ProviderOptions empty() {
        return EMPTY;
    }

    public static ProviderOptions of(Map<String, Object> options) {
        Objects.requireNonNull(options, "options must not be null");
        if (options.isEmpty()) {
            return empty();
        }
        return new ProviderOptions(options);
    }

    public ProviderOptions with(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Map<String, Object> next = new LinkedHashMap<>(options);
        next.put(key, value);
        return new ProviderOptions(next);
    }

    public Optional<Object> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(options.get(key));
    }

    public Map<String, Object> asMap() {
        return options;
    }
}

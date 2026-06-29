package io.github.parkkevinsb.flower.ai.harness.provider.openaicompatible;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Connection settings for an OpenAI-compatible chat completions endpoint.
 */
public final class OpenAiCompatibleGatewayConfig {

    private final URI baseUrl;
    private final String apiKey;

    private OpenAiCompatibleGatewayConfig(Builder builder) {
        this.baseUrl = Objects.requireNonNull(builder.baseUrl, "baseUrl must not be null");
        this.apiKey = blankToNull(builder.apiKey);
    }

    public static Builder builder(URI baseUrl) {
        return new Builder(baseUrl);
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public Optional<String> apiKey() {
        return Optional.ofNullable(apiKey);
    }

    public URI chatCompletionsUri() {
        String normalized = baseUrl.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static final class Builder {

        private final URI baseUrl;
        private String apiKey;

        private Builder(URI baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        }

        public Builder apiKey(String value) {
            apiKey = value;
            return this;
        }

        public OpenAiCompatibleGatewayConfig build() {
            return new OpenAiCompatibleGatewayConfig(this);
        }
    }
}

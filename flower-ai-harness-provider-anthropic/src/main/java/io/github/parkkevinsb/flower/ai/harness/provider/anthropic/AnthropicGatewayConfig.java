package io.github.parkkevinsb.flower.ai.harness.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Connection settings used to create an official Anthropic Java SDK client.
 */
public final class AnthropicGatewayConfig {

    private static final long DEFAULT_MAX_TOKENS = 1024;

    private final String apiKey;
    private final String authToken;
    private final String baseUrl;
    private final Integer maxRetries;
    private final Duration clientTimeout;
    private final long defaultMaxTokens;

    private AnthropicGatewayConfig(Builder builder) {
        this.apiKey = blankToNull(builder.apiKey);
        this.authToken = blankToNull(builder.authToken);
        this.baseUrl = blankToNull(builder.baseUrl);
        this.maxRetries = builder.maxRetries;
        this.clientTimeout = builder.clientTimeout;
        this.defaultMaxTokens = builder.defaultMaxTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> apiKey() {
        return Optional.ofNullable(apiKey);
    }

    public Optional<String> authToken() {
        return Optional.ofNullable(authToken);
    }

    public Optional<String> baseUrl() {
        return Optional.ofNullable(baseUrl);
    }

    public Optional<Integer> maxRetries() {
        return Optional.ofNullable(maxRetries);
    }

    public Optional<Duration> clientTimeout() {
        return Optional.ofNullable(clientTimeout);
    }

    public long defaultMaxTokens() {
        return defaultMaxTokens;
    }

    public AnthropicClient createClient() {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
        if (apiKey == null && authToken == null) {
            builder.fromEnv();
        }
        apiKey().ifPresent(builder::apiKey);
        authToken().ifPresent(builder::authToken);
        baseUrl().ifPresent(builder::baseUrl);
        maxRetries().ifPresent(builder::maxRetries);
        clientTimeout().ifPresent(builder::timeout);
        return builder.build();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static final class Builder {

        private String apiKey;
        private String authToken;
        private String baseUrl;
        private Integer maxRetries;
        private Duration clientTimeout;
        private long defaultMaxTokens = DEFAULT_MAX_TOKENS;

        private Builder() {
        }

        public Builder apiKey(String value) {
            apiKey = value;
            return this;
        }

        public Builder authToken(String value) {
            authToken = value;
            return this;
        }

        public Builder baseUrl(String value) {
            baseUrl = value;
            return this;
        }

        public Builder maxRetries(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxRetries must be greater than or equal to 0");
            }
            maxRetries = value;
            return this;
        }

        public Builder clientTimeout(Duration value) {
            clientTimeout = Objects.requireNonNull(value, "clientTimeout must not be null");
            return this;
        }

        public Builder defaultMaxTokens(long value) {
            if (value <= 0) {
                throw new IllegalArgumentException("defaultMaxTokens must be greater than 0");
            }
            defaultMaxTokens = value;
            return this;
        }

        public AnthropicGatewayConfig build() {
            return new AnthropicGatewayConfig(this);
        }
    }
}

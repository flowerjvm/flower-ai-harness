package io.github.flowerjvm.flower.ai.harness.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Connection settings used to create an official OpenAI Java SDK client.
 */
public final class OpenAiGatewayConfig {

    private final String apiKey;
    private final String baseUrl;
    private final String organization;
    private final String project;
    private final Integer maxRetries;
    private final Duration clientTimeout;

    private OpenAiGatewayConfig(Builder builder) {
        this.apiKey = blankToNull(builder.apiKey);
        this.baseUrl = blankToNull(builder.baseUrl);
        this.organization = blankToNull(builder.organization);
        this.project = blankToNull(builder.project);
        this.maxRetries = builder.maxRetries;
        this.clientTimeout = builder.clientTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> apiKey() {
        return Optional.ofNullable(apiKey);
    }

    public Optional<String> baseUrl() {
        return Optional.ofNullable(baseUrl);
    }

    public Optional<String> organization() {
        return Optional.ofNullable(organization);
    }

    public Optional<String> project() {
        return Optional.ofNullable(project);
    }

    public Optional<Integer> maxRetries() {
        return Optional.ofNullable(maxRetries);
    }

    public Optional<Duration> clientTimeout() {
        return Optional.ofNullable(clientTimeout);
    }

    public OpenAIClient createClient() {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();
        if (apiKey == null) {
            builder.fromEnv();
        } else {
            builder.apiKey(apiKey);
        }
        baseUrl().ifPresent(builder::baseUrl);
        organization().ifPresent(builder::organization);
        project().ifPresent(builder::project);
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
        private String baseUrl;
        private String organization;
        private String project;
        private Integer maxRetries;
        private Duration clientTimeout;

        private Builder() {
        }

        public Builder apiKey(String value) {
            apiKey = value;
            return this;
        }

        public Builder baseUrl(String value) {
            baseUrl = value;
            return this;
        }

        public Builder organization(String value) {
            organization = value;
            return this;
        }

        public Builder project(String value) {
            project = value;
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

        public OpenAiGatewayConfig build() {
            return new OpenAiGatewayConfig(this);
        }
    }
}

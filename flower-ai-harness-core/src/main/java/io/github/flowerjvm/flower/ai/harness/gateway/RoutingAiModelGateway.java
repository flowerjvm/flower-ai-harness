package io.github.flowerjvm.flower.ai.harness.gateway;

import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Dispatches model requests by {@code ModelId.provider()}.
 */
public class RoutingAiModelGateway implements AiModelGateway {

    private final Map<String, AiModelGateway> byProvider;
    private final AiModelGateway fallback;

    public RoutingAiModelGateway(Map<String, AiModelGateway> byProvider) {
        this(byProvider, null);
    }

    private RoutingAiModelGateway(Map<String, AiModelGateway> byProvider, AiModelGateway fallback) {
        Objects.requireNonNull(byProvider, "byProvider must not be null");
        this.byProvider = copyProviderMap(byProvider);
        this.fallback = fallback;
    }

    public RoutingAiModelGateway withFallback(AiModelGateway fallback) {
        Objects.requireNonNull(fallback, "fallback must not be null");
        return new RoutingAiModelGateway(byProvider, fallback);
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String provider = request.modelId().provider();
        AiModelGateway gateway = byProvider.get(provider);
        if (gateway != null) {
            return gateway.submit(request);
        }
        if (fallback != null) {
            return fallback.submit(request);
        }
        throw new GatewayException("No AiModelGateway registered for provider: " + provider);
    }

    private static Map<String, AiModelGateway> copyProviderMap(Map<String, AiModelGateway> source) {
        Map<String, AiModelGateway> copy = new LinkedHashMap<>();
        for (Map.Entry<String, AiModelGateway> entry : source.entrySet()) {
            String provider = requireProvider(entry.getKey());
            AiModelGateway gateway = Objects.requireNonNull(entry.getValue(), "gateway must not be null");
            copy.put(provider, gateway);
        }
        return Map.copyOf(copy);
    }

    private static String requireProvider(String value) {
        Objects.requireNonNull(value, "provider must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        return trimmed;
    }
}

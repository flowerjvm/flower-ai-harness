package io.github.parkkevinsb.flower.ai.harness.spec;

import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.model.ProviderOptions;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.refine.AiRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.AiSchemaValidator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable description of one harness type.
 */
public final class AiHarnessSpec<I, T> {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String harnessId;
    private final ModelId defaultModelId;
    private final ProviderOptions defaultOptions;
    private final Duration defaultTimeout;
    private final PromptVersion promptVersion;
    private final PromptBuilder<I> promptBuilder;
    private final AiSchemaValidator<T> validator;
    private final AiRefinePolicy refinePolicy;
    private final FindingExtractor<T> findingExtractor;
    private final FindingSink findingSink;
    private final List<TraceListener> traceListeners;

    private AiHarnessSpec(Builder<I, T> builder) {
        harnessId = requireText(builder.harnessId, "harnessId");
        defaultModelId = require(builder.defaultModelId, "defaultModelId");
        defaultOptions = builder.defaultOptions == null ? ProviderOptions.empty() : builder.defaultOptions;
        defaultTimeout = requirePositiveDuration(builder.defaultTimeout, "defaultTimeout");
        promptVersion = require(builder.promptVersion, "promptVersion");
        promptBuilder = require(builder.promptBuilder, "promptBuilder");
        validator = require(builder.validator, "validator");
        refinePolicy = require(builder.refinePolicy, "refinePolicy");
        findingExtractor = require(builder.findingExtractor, "findingExtractor");
        findingSink = require(builder.findingSink, "findingSink");
        traceListeners = List.copyOf(builder.traceListeners);
    }

    public static <I, T> Builder<I, T> builder() {
        return new Builder<>();
    }

    public String harnessId() {
        return harnessId;
    }

    public ModelId defaultModelId() {
        return defaultModelId;
    }

    public ProviderOptions defaultOptions() {
        return defaultOptions;
    }

    public Duration defaultTimeout() {
        return defaultTimeout;
    }

    public PromptVersion promptVersion() {
        return promptVersion;
    }

    public PromptBuilder<I> promptBuilder() {
        return promptBuilder;
    }

    public AiSchemaValidator<T> validator() {
        return validator;
    }

    public AiRefinePolicy refinePolicy() {
        return refinePolicy;
    }

    public FindingExtractor<T> findingExtractor() {
        return findingExtractor;
    }

    public FindingSink findingSink() {
        return findingSink;
    }

    public List<TraceListener> traceListeners() {
        return traceListeners;
    }

    public static final class Builder<I, T> {

        private String harnessId;
        private ModelId defaultModelId;
        private ProviderOptions defaultOptions = ProviderOptions.empty();
        private Duration defaultTimeout = DEFAULT_TIMEOUT;
        private PromptVersion promptVersion;
        private PromptBuilder<I> promptBuilder;
        private AiSchemaValidator<T> validator;
        private AiRefinePolicy refinePolicy;
        private FindingExtractor<T> findingExtractor;
        private FindingSink findingSink;
        private final List<TraceListener> traceListeners = new ArrayList<>();

        private Builder() {
        }

        public Builder<I, T> harnessId(String id) {
            harnessId = id;
            return this;
        }

        public Builder<I, T> defaultModelId(ModelId id) {
            defaultModelId = id;
            return this;
        }

        public Builder<I, T> defaultOptions(ProviderOptions opts) {
            defaultOptions = opts;
            return this;
        }

        public Builder<I, T> defaultTimeout(Duration t) {
            defaultTimeout = t;
            return this;
        }

        public Builder<I, T> promptVersion(PromptVersion v) {
            promptVersion = v;
            return this;
        }

        public Builder<I, T> promptBuilder(PromptBuilder<I> pb) {
            promptBuilder = pb;
            return this;
        }

        public Builder<I, T> validator(AiSchemaValidator<T> v) {
            validator = v;
            return this;
        }

        public Builder<I, T> refinePolicy(AiRefinePolicy p) {
            refinePolicy = p;
            return this;
        }

        public Builder<I, T> findingExtractor(FindingExtractor<T> fe) {
            findingExtractor = fe;
            return this;
        }

        public Builder<I, T> findingSink(FindingSink fs) {
            findingSink = fs;
            return this;
        }

        public Builder<I, T> addTraceListener(TraceListener l) {
            traceListeners.add(require(l, "traceListener"));
            return this;
        }

        public AiHarnessSpec<I, T> build() {
            return new AiHarnessSpec<>(this);
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static Duration requirePositiveDuration(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static <V> V require(V value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }
}

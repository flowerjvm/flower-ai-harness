package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.refine.AiRefinePolicy;
import io.github.flowerjvm.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.TraceListener;
import io.github.flowerjvm.flower.ai.harness.validator.jackson.JacksonPojoSchemaValidator;

import java.time.Duration;
import java.util.Objects;

/**
 * Shared spec factory for the text-review sample.
 */
public final class TextReviewHarness {

    public static final String HARNESS_ID = "sample.text-review";
    public static final ModelId MODEL_ID = ModelId.parse("fake:text-review");
    public static final PromptVersion PROMPT_VERSION = new PromptVersion("text-review", "1.0.0");

    private TextReviewHarness() {
    }

    public static AiHarnessSpec<TextReviewInput, TextReviewDraft> spec(InMemoryFindingSink sink) {
        return spec(sink, new TraceListener[0]);
    }

    public static AiHarnessSpec<TextReviewInput, TextReviewDraft> spec(
            InMemoryFindingSink sink,
            TraceListener... listeners
    ) {
        return spec(sink, MODEL_ID, new MaxAttemptsRefinePolicy(3), listeners);
    }

    public static AiHarnessSpec<TextReviewInput, TextReviewDraft> spec(
            InMemoryFindingSink sink,
            ModelId defaultModelId,
            AiRefinePolicy refinePolicy,
            TraceListener... listeners
    ) {
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(defaultModelId, "defaultModelId must not be null");
        Objects.requireNonNull(refinePolicy, "refinePolicy must not be null");
        AiHarnessSpec.Builder<TextReviewInput, TextReviewDraft> builder =
                AiHarnessSpec.<TextReviewInput, TextReviewDraft>builder()
                        .harnessId(HARNESS_ID)
                        .defaultModelId(defaultModelId)
                        .defaultTimeout(Duration.ofSeconds(30))
                        .promptVersion(PROMPT_VERSION)
                        .promptBuilder(new TextReviewPromptBuilder())
                        .validator(new JacksonPojoSchemaValidator<>(TextReviewDraft.class, new ObjectMapper()))
                        .refinePolicy(refinePolicy)
                        .findingExtractor(new TextReviewFindingExtractor())
                        .findingSink(sink);

        if (listeners != null) {
            for (TraceListener listener : listeners) {
                builder.addTraceListener(listener);
            }
        }
        return builder.build();
    }
}

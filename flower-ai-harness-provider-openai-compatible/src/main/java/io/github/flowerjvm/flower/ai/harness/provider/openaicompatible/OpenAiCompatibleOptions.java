package io.github.flowerjvm.flower.ai.harness.provider.openaicompatible;

/**
 * Provider option keys understood by {@link OpenAiCompatibleModelGateway}.
 */
public final class OpenAiCompatibleOptions {

    public static final String TEMPERATURE = "temperature";
    public static final String MAX_TOKENS = "maxTokens";
    public static final String TOP_P = "topP";
    public static final String FREQUENCY_PENALTY = "frequencyPenalty";
    public static final String PRESENCE_PENALTY = "presencePenalty";
    public static final String STOP_SEQUENCES = "stopSequences";
    public static final String EXTRA_BODY = "openAiCompatible.extraBody";

    private OpenAiCompatibleOptions() {
    }
}

package io.github.parkkevinsb.flower.ai.harness.provider.openai;

/**
 * Provider option keys understood by {@link OpenAiModelGateway}.
 */
public final class OpenAiOptions {

    public static final String TEMPERATURE = "temperature";
    public static final String MAX_TOKENS = "maxTokens";
    public static final String MAX_COMPLETION_TOKENS = "maxCompletionTokens";
    public static final String TOP_P = "topP";
    public static final String FREQUENCY_PENALTY = "frequencyPenalty";
    public static final String PRESENCE_PENALTY = "presencePenalty";
    public static final String STOP_SEQUENCES = "stopSequences";
    public static final String SEED = "seed";
    public static final String USER = "user";
    public static final String STORE = "store";

    private OpenAiOptions() {
    }
}

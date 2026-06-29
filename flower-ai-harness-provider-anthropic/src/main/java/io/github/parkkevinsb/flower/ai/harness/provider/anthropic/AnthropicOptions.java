package io.github.parkkevinsb.flower.ai.harness.provider.anthropic;

/**
 * Provider option keys understood by {@link AnthropicModelGateway}.
 */
public final class AnthropicOptions {

    public static final String MAX_TOKENS = "maxTokens";
    public static final String TEMPERATURE = "temperature";
    public static final String TOP_P = "topP";
    public static final String TOP_K = "topK";
    public static final String STOP_SEQUENCES = "stopSequences";
    public static final String USER_PROFILE_ID = "userProfileId";
    public static final String CONTAINER = "container";

    private AnthropicOptions() {
    }
}

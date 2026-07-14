package io.github.flowerjvm.flower.ai.harness.springai;

import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves a harness model id to the Spring AI client that should serve it.
 */
@FunctionalInterface
public interface SpringAiModelResolver {

    ChatClient resolve(ModelId modelId);

    static SpringAiModelResolver fixed(ChatClient client) {
        Objects.requireNonNull(client, "client must not be null");
        return modelId -> client;
    }

    static SpringAiModelResolver byModelId(Map<ModelId, ChatClient> clients) {
        Objects.requireNonNull(clients, "clients must not be null");
        Map<ModelId, ChatClient> copy = Map.copyOf(clients);
        return modelId -> {
            ChatClient client = copy.get(modelId);
            if (client == null) {
                throw new IllegalArgumentException("No Spring AI ChatClient registered for model: "
                        + modelId.asString());
            }
            return client;
        };
    }
}

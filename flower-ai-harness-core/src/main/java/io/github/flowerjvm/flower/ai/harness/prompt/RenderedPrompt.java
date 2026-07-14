package io.github.flowerjvm.flower.ai.harness.prompt;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral, role-tagged prompt ready for model submission.
 */
public record RenderedPrompt(
        List<Message> messages,
        PromptVersion version
) {

    public RenderedPrompt {
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        Objects.requireNonNull(version, "version must not be null");
    }

    public record Message(Role role, String content) {

        public Message {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}

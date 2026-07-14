package io.github.flowerjvm.flower.ai.harness.prompt;

import java.util.Objects;

/**
 * Versioned prompt template text supplied by the host application.
 */
public record PromptTemplate(PromptVersion version, String template) {

    public PromptTemplate {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(template, "template must not be null");
    }
}

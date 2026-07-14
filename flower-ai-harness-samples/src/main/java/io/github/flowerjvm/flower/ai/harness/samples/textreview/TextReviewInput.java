package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import java.util.Objects;

/**
 * Input text for the sample review harness.
 */
public record TextReviewInput(String text) {

    public TextReviewInput {
        Objects.requireNonNull(text, "text must not be null");
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }
}

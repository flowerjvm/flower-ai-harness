package io.github.parkkevinsb.flower.ai.harness.samples.textreview;

import java.util.List;
import java.util.Objects;

/**
 * Structured output expected from the sample text-review prompt.
 */
public record TextReviewDraft(List<TextIssue> issues) {

    public TextReviewDraft {
        Objects.requireNonNull(issues, "issues must not be null");
        issues = List.copyOf(issues);
    }
}

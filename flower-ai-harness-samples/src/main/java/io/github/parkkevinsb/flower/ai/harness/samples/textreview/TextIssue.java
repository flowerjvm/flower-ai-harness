package io.github.parkkevinsb.flower.ai.harness.samples.textreview;

/**
 * One issue reported by the sample model response.
 */
public record TextIssue(
        String code,
        String severity,
        String message,
        String quote
) {
}

package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.finding.AiFindingSeverity;
import io.github.flowerjvm.flower.ai.harness.finding.FindingExtractor;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

/**
 * Converts the sample structured response into neutral harness findings.
 */
public final class TextReviewFindingExtractor implements FindingExtractor<TextReviewDraft> {

    @Override
    public List<AiFinding> extract(TextReviewDraft value, AiHarnessRunContext ctx) {
        return value.issues().stream()
                .map(this::toFinding)
                .toList();
    }

    private AiFinding toFinding(TextIssue issue) {
        return AiFinding.of(
                        normalizeCode(issue.code()),
                        parseSeverity(issue.severity()),
                        normalizeMessage(issue.message()))
                .withEvidence(issue.quote() == null ? "" : issue.quote());
    }

    private static String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "TEXT_REVIEW_ISSUE";
        }
        return code.trim();
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Text review issue";
        }
        return message.trim();
    }

    private static AiFindingSeverity parseSeverity(String severity) {
        if (severity == null || severity.trim().isEmpty()) {
            return AiFindingSeverity.INFO;
        }
        try {
            return AiFindingSeverity.valueOf(severity.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AiFindingSeverity.INFO;
        }
    }
}

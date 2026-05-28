package io.github.parkkevinsb.flower.ai.harness.test.assertion;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertions for lists of {@link AiFinding}.
 */
public final class AiFindingAssertions {

    private final List<AiFinding> findings;

    private AiFindingAssertions(List<AiFinding> findings) {
        this.findings = List.copyOf(Objects.requireNonNull(findings, "findings must not be null"));
    }

    public static AiFindingAssertions assertThatFindings(List<AiFinding> findings) {
        return new AiFindingAssertions(findings);
    }

    public AiFindingAssertions hasSize(int expected) {
        assertThat(findings).hasSize(expected);
        return this;
    }

    public AiFindingAssertions isEmpty() {
        assertThat(findings).isEmpty();
        return this;
    }

    public AiFindingAssertions hasCode(String code) {
        assertThat(findings).anySatisfy(finding -> assertThat(finding.code()).isEqualTo(code));
        return this;
    }

    public AiFindingAssertions hasSeverity(AiFindingSeverity severity) {
        assertThat(findings).anySatisfy(finding -> assertThat(finding.severity()).isEqualTo(severity));
        return this;
    }

    public AiFindingAssertions hasMessageContaining(String fragment) {
        assertThat(findings).anySatisfy(finding -> assertThat(finding.message()).contains(fragment));
        return this;
    }
}

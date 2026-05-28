package io.github.parkkevinsb.flower.ai.harness.finding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFindingTest {

    @Test
    void createsMinimalFinding() {
        AiFinding finding = AiFinding.of("DOC_001", AiFindingSeverity.HIGH, "Missing section");

        assertThat(finding.code()).isEqualTo("DOC_001");
        assertThat(finding.severity()).isEqualTo(AiFindingSeverity.HIGH);
        assertThat(finding.message()).isEqualTo("Missing section");
        assertThat(finding.evidence()).isEmpty();
        assertThat(finding.location()).isEmpty();
        assertThat(finding.attributes()).isEmpty();
    }

    @Test
    void withMethodsReturnNewFindingWithoutMutatingOriginal() {
        AiFinding original = AiFinding.of("DOC_001", AiFindingSeverity.MEDIUM, "Check wording");

        AiFinding next = original
                .withEvidence("quoted text")
                .withLocation("paragraph:3")
                .withAttribute("rule", "R-7");

        assertThat(original.evidence()).isEmpty();
        assertThat(original.location()).isEmpty();
        assertThat(original.attributes()).isEmpty();

        assertThat(next.evidence()).isEqualTo("quoted text");
        assertThat(next.location()).isEqualTo("paragraph:3");
        assertThat(next.attributes()).containsEntry("rule", "R-7");
    }

    @Test
    void copiesAttributes() {
        Map<String, String> attributes = new java.util.HashMap<>();
        attributes.put("source", "ai");

        AiFinding finding = new AiFinding(
                "DOC_001",
                AiFindingSeverity.LOW,
                "Message",
                "",
                "",
                attributes);
        attributes.put("source", "manual");

        assertThat(finding.attributes()).containsEntry("source", "ai");
    }

    @Test
    void exposesUnmodifiableAttributes() {
        AiFinding finding = AiFinding.of("DOC_001", AiFindingSeverity.INFO, "Message")
                .withAttribute("key", "value");

        assertThatThrownBy(() -> finding.attributes().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> AiFinding.of("", AiFindingSeverity.INFO, "Message"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiFinding.of("DOC_001", AiFindingSeverity.INFO, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

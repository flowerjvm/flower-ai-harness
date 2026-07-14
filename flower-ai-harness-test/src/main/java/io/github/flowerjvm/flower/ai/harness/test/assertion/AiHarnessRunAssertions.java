package io.github.flowerjvm.flower.ai.harness.test.assertion;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertions for a completed or in-progress harness run context.
 */
public final class AiHarnessRunAssertions {

    private final AiHarnessRunContext context;

    private AiHarnessRunAssertions(AiHarnessRunContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    public static AiHarnessRunAssertions assertThatRun(AiHarnessRunContext context) {
        return new AiHarnessRunAssertions(context);
    }

    public AiHarnessRunAssertions hasAttempts(int expected) {
        assertThat(context.attempt()).isEqualTo(expected);
        return this;
    }

    public AiHarnessRunAssertions hasFindings(int expected) {
        assertThat(context.latestFindings()).hasSize(expected);
        return this;
    }

    public AiHarnessRunAssertions hasFindingCode(String code) {
        AiFindingAssertions.assertThatFindings(context.latestFindings()).hasCode(code);
        return this;
    }

    public AiHarnessRunAssertions hasNoCallError() {
        assertThat(context.latestCallError()).isEmpty();
        return this;
    }

    public AiHarnessRunAssertions hasCallErrorContaining(String fragment) {
        assertThat(context.latestCallError()).hasValueSatisfying(error ->
                assertThat(error.getMessage()).contains(fragment));
        return this;
    }

    public AiHarnessRunAssertions hasValidValidation() {
        assertThat(context.latestValidation()).hasValueSatisfying(validation ->
                assertThat(validation).isInstanceOf(ValidationResult.Valid.class));
        return this;
    }
}

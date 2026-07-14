package io.github.flowerjvm.flower.ai.harness.samples.textreview;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.finding.FindingSink;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simple finding sink used by the sample and its tests.
 */
public final class InMemoryFindingSink implements FindingSink {

    private final List<AiFinding> findings = new ArrayList<>();

    @Override
    public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
        Objects.requireNonNull(findings, "findings must not be null");
        this.findings.addAll(findings);
    }

    public List<AiFinding> findings() {
        return List.copyOf(findings);
    }

    public void clear() {
        findings.clear();
    }
}

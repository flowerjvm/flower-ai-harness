package io.github.flowerjvm.flower.ai.harness.finding;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

/**
 * Fast host-side publication boundary for findings.
 */
public interface FindingSink {

    void accept(List<AiFinding> findings, AiHarnessRunContext ctx);
}

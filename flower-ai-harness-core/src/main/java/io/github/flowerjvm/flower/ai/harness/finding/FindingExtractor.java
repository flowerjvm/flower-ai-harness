package io.github.flowerjvm.flower.ai.harness.finding;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

/**
 * Converts a validated typed value into domain-neutral findings.
 */
public interface FindingExtractor<T> {

    List<AiFinding> extract(T value, AiHarnessRunContext ctx);
}

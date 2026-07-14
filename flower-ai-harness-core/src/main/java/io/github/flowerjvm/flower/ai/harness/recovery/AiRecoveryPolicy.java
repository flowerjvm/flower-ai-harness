package io.github.flowerjvm.flower.ai.harness.recovery;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;

/**
 * Policy that decides how to rebuild a harness flow from a run snapshot.
 */
@FunctionalInterface
public interface AiRecoveryPolicy {

    AiRecoveryDecision decide(AiRecoveryContext context);

    static AiRecoveryPolicy conservative() {
        return context -> {
            AiHarnessRunStatus status = context.snapshot().status();
            if (status == AiHarnessRunStatus.CANCELLED) {
                return new AiRecoveryDecision.MarkCancelled(
                        context.snapshot().terminalReason().orElse("Recovered run was already cancelled"));
            }
            if (status == AiHarnessRunStatus.SUCCEEDED) {
                return new AiRecoveryDecision.FailRecoverable("Recovered run already succeeded");
            }
            return context.snapshot().currentRequest()
                    .<AiRecoveryDecision>map(AiRecoveryDecision.RetryCurrentRequest::new)
                    .orElseGet(() -> new AiRecoveryDecision.FailRecoverable(
                            "Recovered run has no current request to retry"));
        };
    }
}

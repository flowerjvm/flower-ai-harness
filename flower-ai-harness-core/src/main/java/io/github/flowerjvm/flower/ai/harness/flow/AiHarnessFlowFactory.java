package io.github.flowerjvm.flower.ai.harness.flow;

import io.github.flowerjvm.flower.ai.harness.control.AiCancellationToken;
import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.recovery.AiRecoveryContext;
import io.github.flowerjvm.flower.ai.harness.recovery.AiRecoveryDecision;
import io.github.flowerjvm.flower.ai.harness.recovery.AiRecoveryPolicy;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunId;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;
import io.github.flowerjvm.flower.core.flow.Flow;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds runnable Flower flows for a configured AI harness spec.
 */
public final class AiHarnessFlowFactory<I, T> {

    static final String PREPARE_PROMPT_STEP = "prepare-prompt";
    static final String AWAIT_RESPONSE_STEP = "await-response";
    static final String VALIDATE_RESPONSE_STEP = "validate-response";
    static final String REFINE_DECISION_STEP = "refine-decision";
    static final String EMIT_FINDINGS_STEP = "emit-findings";
    static final String RECOVERED_TERMINAL_STEP = "recovered-terminal";

    private final AiModelGateway gateway;
    private final AiHarnessSpec<I, T> spec;
    private final AiHarnessClock clock;

    public AiHarnessFlowFactory(AiModelGateway gateway, AiHarnessSpec<I, T> spec, AiHarnessClock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AiHarnessFlow createFlow(I input) {
        return createFlow(input, RunOverrides.none());
    }

    public AiHarnessFlow createFlow(I input, RunOverrides overrides) {
        Objects.requireNonNull(input, "input must not be null");
        overrides = overrides == null ? RunOverrides.none() : overrides;

        AiHarnessRunContext context = new AiHarnessRunContext(
                AiHarnessRunId.random(),
                spec.harnessId(),
                spec.promptVersion(),
                clock.now(),
                overrides.cancellationToken().orElse(AiCancellationToken.none()));
        overrides.attributes().entrySet().forEach(e -> putAttribute(context, e));

        ModelId modelId = overrides.modelId().orElse(spec.defaultModelId());
        ProviderOptions options = overrides.providerOptions().orElse(spec.defaultOptions());
        Duration timeout = overrides.timeout().orElse(spec.defaultTimeout());

        Flow flow = Flow.builder(spec.harnessId(), context.runId().value())
                .definitionVersion(spec.promptVersion().asString())
                .step(PREPARE_PROMPT_STEP, new PreparePromptStep<>(input, spec, context, modelId, options, timeout, clock))
                .step(AWAIT_RESPONSE_STEP, new AwaitResponseStep(gateway, context, spec, clock))
                .step(VALIDATE_RESPONSE_STEP, new ValidateResponseStep<>(spec, context, clock))
                .step(REFINE_DECISION_STEP, new RefineDecisionStep(spec, context, AWAIT_RESPONSE_STEP, clock))
                .step(EMIT_FINDINGS_STEP, new EmitFindingsStep<>(spec, context, clock))
                .build();
        return new AiHarnessFlow(flow, context);
    }

    public AiHarnessFlow createRecoveredFlow(AiHarnessRunSnapshot snapshot) {
        return createRecoveredFlow(snapshot, AiRecoveryPolicy.conservative(), RunOverrides.none());
    }

    public AiHarnessFlow createRecoveredFlow(AiHarnessRunSnapshot snapshot, AiRecoveryPolicy recoveryPolicy) {
        return createRecoveredFlow(snapshot, recoveryPolicy, RunOverrides.none());
    }

    public AiHarnessFlow createRecoveredFlow(
            AiHarnessRunSnapshot snapshot,
            AiRecoveryPolicy recoveryPolicy,
            RunOverrides overrides
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(recoveryPolicy, "recoveryPolicy must not be null");
        overrides = overrides == null ? RunOverrides.none() : overrides;
        validateSnapshot(snapshot);

        AiHarnessRunContext context = AiHarnessRunContext.fromSnapshot(
                snapshot,
                overrides.cancellationToken().orElse(AiCancellationToken.none()));
        overrides.attributes().entrySet().forEach(e -> putAttribute(context, e));

        AiRecoveryDecision decision = recoveryPolicy.decide(new AiRecoveryContext(snapshot, spec));
        if (decision == null) {
            return terminalRecoveredFlow(context, RecoveredTerminalStep.Outcome.FAIL_RECOVERABLE,
                    "Recovery policy returned null");
        }
        if (decision instanceof AiRecoveryDecision.RetryCurrentRequest retry) {
            context.setCurrentRequest(retry.request());
            return activeRecoveredFlow(context);
        }
        if (decision instanceof AiRecoveryDecision.ContinueFromFlow) {
            return snapshot.currentRequest()
                    .map(request -> {
                        context.setCurrentRequest(request);
                        return activeRecoveredFlow(context);
                    })
                    .orElseGet(() -> terminalRecoveredFlow(
                            context,
                            RecoveredTerminalStep.Outcome.FAIL_RECOVERABLE,
                            "Cannot continue recovered run without current request"));
        }
        if (decision instanceof AiRecoveryDecision.FailRecoverable fail) {
            return terminalRecoveredFlow(context, RecoveredTerminalStep.Outcome.FAIL_RECOVERABLE, fail.reason());
        }
        if (decision instanceof AiRecoveryDecision.MarkCancelled cancelled) {
            return terminalRecoveredFlow(context, RecoveredTerminalStep.Outcome.CANCELLED, cancelled.reason());
        }
        return terminalRecoveredFlow(context, RecoveredTerminalStep.Outcome.FAIL_RECOVERABLE,
                "Unsupported recovery decision: " + decision.getClass().getName());
    }

    private AiHarnessFlow activeRecoveredFlow(AiHarnessRunContext context) {
        Flow flow = Flow.builder(spec.harnessId(), context.runId().value())
                .definitionVersion(spec.promptVersion().asString())
                .step(AWAIT_RESPONSE_STEP, new AwaitResponseStep(gateway, context, spec, clock))
                .step(VALIDATE_RESPONSE_STEP, new ValidateResponseStep<>(spec, context, clock))
                .step(REFINE_DECISION_STEP, new RefineDecisionStep(spec, context, AWAIT_RESPONSE_STEP, clock))
                .step(EMIT_FINDINGS_STEP, new EmitFindingsStep<>(spec, context, clock))
                .build();
        return new AiHarnessFlow(flow, context);
    }

    private AiHarnessFlow terminalRecoveredFlow(
            AiHarnessRunContext context,
            RecoveredTerminalStep.Outcome outcome,
            String reason
    ) {
        Flow flow = Flow.builder(spec.harnessId(), context.runId().value())
                .definitionVersion(spec.promptVersion().asString())
                .step(RECOVERED_TERMINAL_STEP, new RecoveredTerminalStep(spec, context, outcome, reason, clock))
                .build();
        return new AiHarnessFlow(flow, context);
    }

    private void validateSnapshot(AiHarnessRunSnapshot snapshot) {
        if (!spec.harnessId().equals(snapshot.harnessId())) {
            throw new IllegalArgumentException("snapshot harnessId does not match spec: " + snapshot.harnessId());
        }
        if (!spec.promptVersion().equals(snapshot.promptVersion())) {
            throw new IllegalArgumentException("snapshot promptVersion does not match spec: "
                    + snapshot.promptVersion().asString());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void putAttribute(AiHarnessRunContext context, Map.Entry<AiHarnessRunContext.AttributeKey<?>, Object> e) {
        context.putAttribute((AiHarnessRunContext.AttributeKey) e.getKey(), e.getValue());
    }

    public record RunOverrides(
            Optional<ModelId> modelId,
            Optional<ProviderOptions> providerOptions,
            Optional<Duration> timeout,
            Optional<AiCancellationToken> cancellationToken,
            Map<AiHarnessRunContext.AttributeKey<?>, Object> attributes
    ) {

        private static final RunOverrides NONE = new RunOverrides(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of());

        public RunOverrides {
            modelId = modelId == null ? Optional.empty() : modelId;
            providerOptions = providerOptions == null ? Optional.empty() : providerOptions;
            timeout = timeout == null ? Optional.empty() : timeout;
            cancellationToken = cancellationToken == null ? Optional.empty() : cancellationToken;
            timeout.ifPresent(t -> {
                if (t.isNegative() || t.isZero()) {
                    throw new IllegalArgumentException("timeout must be positive");
                }
            });
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public static RunOverrides none() {
            return NONE;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private ModelId modelId;
            private ProviderOptions providerOptions;
            private Duration timeout;
            private AiCancellationToken cancellationToken;
            private final Map<AiHarnessRunContext.AttributeKey<?>, Object> attributes = new LinkedHashMap<>();

            private Builder() {
            }

            public Builder modelId(ModelId value) {
                modelId = value;
                return this;
            }

            public Builder providerOptions(ProviderOptions value) {
                providerOptions = value;
                return this;
            }

            public Builder timeout(Duration value) {
                timeout = value;
                return this;
            }

            public Builder cancellationToken(AiCancellationToken value) {
                cancellationToken = value;
                return this;
            }

            public <V> Builder attribute(AiHarnessRunContext.AttributeKey<V> key, V value) {
                Objects.requireNonNull(key, "key must not be null");
                Objects.requireNonNull(value, "value must not be null");
                if (!key.type().isInstance(value)) {
                    throw new IllegalArgumentException("attribute value does not match key type: " + key.name());
                }
                attributes.put(key, value);
                return this;
            }

            public RunOverrides build() {
                return new RunOverrides(
                        Optional.ofNullable(modelId),
                        Optional.ofNullable(providerOptions),
                        Optional.ofNullable(timeout),
                        Optional.ofNullable(cancellationToken),
                        attributes);
            }
        }
    }
}

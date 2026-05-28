package io.github.parkkevinsb.flower.ai.harness.test;

import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeAiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.test.time.FixedAiHarnessClock;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 helper for deterministic harness flow tests.
 */
public final class AiHarnessTestExtension
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final int DEFAULT_MAX_TICKS = 1_000;

    private FixedAiHarnessClock clock;
    private FakeAiModelGateway gateway;
    private int maxTicks = DEFAULT_MAX_TICKS;

    @Override
    public void beforeEach(ExtensionContext context) {
        resetState();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (gateway != null) {
            gateway.reset();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == FakeAiModelGateway.class
                || type == FixedAiHarnessClock.class
                || type == AiHarnessTestExtension.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        ensureState();
        Class<?> type = parameterContext.getParameter().getType();
        if (type == FakeAiModelGateway.class) {
            return gateway;
        }
        if (type == FixedAiHarnessClock.class) {
            return clock;
        }
        if (type == AiHarnessTestExtension.class) {
            return this;
        }
        throw new ParameterResolutionException("unsupported parameter type: " + type.getName());
    }

    public FakeAiModelGateway gateway() {
        ensureState();
        return gateway;
    }

    public FixedAiHarnessClock clock() {
        ensureState();
        return clock;
    }

    public AiHarnessTestExtension maxTicks(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("maxTicks must be at least 1");
        }
        maxTicks = value;
        return this;
    }

    public <I, T> AiHarnessFlow runHarness(AiHarnessSpec<I, T> spec, I input) {
        return runHarness(spec, input, AiHarnessFlowFactory.RunOverrides.none());
    }

    public <I, T> AiHarnessFlow runHarness(
            AiHarnessSpec<I, T> spec,
            I input,
            AiHarnessFlowFactory.RunOverrides overrides
    ) {
        ensureState();
        AiHarnessFlowFactory<I, T> factory = new AiHarnessFlowFactory<>(gateway, spec, clock);
        AiHarnessFlow harnessFlow = factory.createFlow(input, overrides);
        runFlow(harnessFlow.flow());
        return harnessFlow;
    }

    public Flow runFlow(Flow flow) {
        ensureState();
        ManualClock flowerClock = new ManualClock(clock.instant().toEpochMilli());
        Worker worker = Worker.builder("harness-test").build();
        Engine engine = Engine.builder()
                .clock(flowerClock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();
        engine.attach();
        worker.submit(flow);
        for (int i = 0; i < maxTicks && !flow.state().isTerminal(); i++) {
            worker.tickOnce();
            clock.tick();
            flowerClock.advance(1);
        }
        if (!flow.state().isTerminal()) {
            throw new AssertionError("flow did not terminate within " + maxTicks + " ticks: " + flow.flowId());
        }
        return flow;
    }

    private void ensureState() {
        if (clock == null || gateway == null) {
            resetState();
        }
    }

    private void resetState() {
        clock = new FixedAiHarnessClock();
        gateway = new FakeAiModelGateway(clock);
    }
}

package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.gateway.GatewayException;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vendor-neutral gateway that executes one external agent runner per request.
 */
public final class AgentCliModelGateway implements AiModelGateway, AutoCloseable {

    private final AgentCliGatewayConfig config;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final ExecutorService progressExecutor;
    private final Map<String, AgentCliModelCall> activeCalls = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    public AgentCliModelGateway(AgentCliGatewayConfig config) {
        this(config, new ObjectMapper());
    }

    public AgentCliModelGateway(AgentCliGatewayConfig config, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        executor = Executors.newVirtualThreadPerTaskExecutor();
        progressExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256),
                runnable -> Thread.ofPlatform()
                        .daemon()
                        .name("flower-agent-cli-progress")
                        .unstarted(runnable),
                new ThreadPoolExecutor.DiscardPolicy());
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        synchronized (lifecycleLock) {
            if (closed.get()) {
                throw new IllegalStateException("Agent CLI gateway is closed");
            }
            String callId = "agent-cli-call-" + UUID.randomUUID();
            AgentCliModelCall call = new AgentCliModelCall(callId, executor);
            activeCalls.put(callId, call);
            try {
                executor.execute(() -> {
                    try {
                        new AgentCliProcessRunner(
                                config,
                                objectMapper,
                                executor,
                                this::dispatchProgress)
                                .execute(request, call);
                    } catch (Throwable failure) {
                        call.completeFailed(new GatewayException(
                                "Agent CLI call failed before process completion: " + callId,
                                failure));
                    } finally {
                        activeCalls.remove(callId);
                    }
                });
            } catch (RejectedExecutionException ex) {
                activeCalls.remove(callId);
                call.completeFailed(new GatewayException("Agent CLI executor rejected call " + callId, ex));
            }
            return call;
        }
    }

    private void dispatchProgress(AgentCliProgressEvent event) {
        try {
            progressExecutor.execute(() -> {
                try {
                    config.progressListener().onProgress(event);
                } catch (RuntimeException ignored) {
                    // Progress is observational and must not fail the model call.
                }
            });
        } catch (RejectedExecutionException ignored) {
            // A full or shutting-down progress queue drops observational events.
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
        }
        activeCalls.values().forEach(AgentCliModelCall::cancel);
        executor.shutdown();
        progressExecutor.shutdown();
        try {
            long waitMillis = Math.max(1000L, config.killGracePeriod().multipliedBy(2).toMillis());
            if (!executor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
            progressExecutor.awaitTermination(1000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            progressExecutor.shutdownNow();
        }
    }
}

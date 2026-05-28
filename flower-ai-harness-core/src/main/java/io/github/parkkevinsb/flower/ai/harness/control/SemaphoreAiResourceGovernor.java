package io.github.parkkevinsb.flower.ai.harness.control;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-JVM concurrency cap for provider calls.
 */
public final class SemaphoreAiResourceGovernor implements AiResourceGovernor {

    private final Semaphore semaphore;

    public SemaphoreAiResourceGovernor(int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be at least 1");
        }
        semaphore = new Semaphore(maxConcurrency);
    }

    @Override
    public Optional<AiResourcePermit> tryAcquire(AiModelRequest request, AiHarnessRunContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (!semaphore.tryAcquire()) {
            return Optional.empty();
        }
        return Optional.of(new SemaphorePermit(semaphore));
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }

    private static final class SemaphorePermit implements AiResourcePermit {

        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SemaphorePermit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}

package io.github.parkkevinsb.flower.ai.harness.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Spring AI backed harness gateway.
 */
@ConfigurationProperties("flower.ai.harness.spring-ai")
public class FlowerAiHarnessSpringAiProperties {

    private boolean enabled = true;

    private ExecutorProperties executor = new ExecutorProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorProperties executor) {
        this.executor = executor;
    }

    public static class ExecutorProperties {

        private int threads = defaultThreads();

        private String threadNamePrefix = "flower-ai-harness-";

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        private static int defaultThreads() {
            return Math.max(2, Runtime.getRuntime().availableProcessors());
        }
    }
}

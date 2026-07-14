package io.github.flowerjvm.flower.ai.harness.springboot;

import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.springai.SpringAiModelGateway;
import io.github.flowerjvm.flower.ai.harness.springai.SpringAiModelResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring Boot auto-configuration for the Spring AI backed model gateway.
 */
@AutoConfiguration
@ConditionalOnClass({ChatClient.class, SpringAiModelGateway.class})
@ConditionalOnProperty(
        prefix = "flower.ai.harness.spring-ai",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(FlowerAiHarnessSpringAiProperties.class)
public class FlowerAiHarnessSpringAiAutoConfiguration {

    public static final String MODEL_EXECUTOR_BEAN_NAME = "flowerAiHarnessModelExecutor";

    @Bean(name = MODEL_EXECUTOR_BEAN_NAME, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = MODEL_EXECUTOR_BEAN_NAME)
    ExecutorService flowerAiHarnessModelExecutor(FlowerAiHarnessSpringAiProperties properties) {
        FlowerAiHarnessSpringAiProperties.ExecutorProperties executor = properties.getExecutor();
        int threads = Math.max(1, executor.getThreads());
        String threadNamePrefix = normalizeThreadNamePrefix(executor.getThreadNamePrefix());
        return Executors.newFixedThreadPool(threads, threadFactory(threadNamePrefix));
    }

    @Bean
    @ConditionalOnMissingBean(SpringAiModelResolver.class)
    @ConditionalOnSingleCandidate(ChatClient.class)
    SpringAiModelResolver flowerAiHarnessSpringAiModelResolver(ChatClient chatClient) {
        return SpringAiModelResolver.fixed(chatClient);
    }

    @Bean
    @ConditionalOnBean(SpringAiModelResolver.class)
    @ConditionalOnMissingBean(AiModelGateway.class)
    SpringAiModelGateway flowerAiHarnessSpringAiModelGateway(
            SpringAiModelResolver resolver,
            @Qualifier(MODEL_EXECUTOR_BEAN_NAME) Executor executor) {
        return new SpringAiModelGateway(resolver, executor);
    }

    private static ThreadFactory threadFactory(String threadNamePrefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, threadNamePrefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String normalizeThreadNamePrefix(String threadNamePrefix) {
        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            return "flower-ai-harness-";
        }
        return threadNamePrefix;
    }
}

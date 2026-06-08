package com.bc.credit.integration.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class Resilience4jConfig {

    @Value("${credit.integration.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${credit.integration.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${credit.integration.retry.wait-duration-ms:500}")
    private int retryWaitDurationMs;

    @Value("${credit.integration.circuit-breaker.failure-threshold:50}")
    private int failureThresholdPercentage;

    @Value("${credit.integration.circuit-breaker.wait-duration-ms:30000}")
    private int waitDurationInOpenStateMs;

    @Value("${credit.integration.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    private static final String[] DATA_SOURCES = {"PBOC", "BAIHANG", "SOCIAL_SECURITY", "HOUSING_FUND"};

    @Bean
    public Map<String, CircuitBreaker> creditCircuitBreakers(CircuitBreakerRegistry registry) {
        Map<String, CircuitBreaker> circuitBreakers = new HashMap<>();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureThresholdPercentage)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        for (String dataSource : DATA_SOURCES) {
            CircuitBreaker circuitBreaker = registry.circuitBreaker("credit-" + dataSource, config);
            circuitBreakers.put(dataSource, circuitBreaker);

            circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> log.info("[熔断器-{}] 状态变更: {} -> {}",
                            dataSource, event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()))
                    .onError(event -> log.warn("[熔断器-{}] 调用失败: {}",
                            dataSource, event.getThrowable().getMessage()))
                    .onSuccess(event -> log.debug("[熔断器-{}] 调用成功", dataSource));

            log.info("[熔断器-{}] 初始化完成, failureThreshold: {}%, windowSize: {}",
                    dataSource, failureThresholdPercentage, slidingWindowSize);
        }

        return circuitBreakers;
    }

    @Bean
    public Map<String, Retry> creditRetries(RetryRegistry registry) {
        Map<String, Retry> retries = new HashMap<>();

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(retryWaitDurationMs))
                .retryExceptions(RuntimeException.class, Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .failAfterMaxAttempts(true)
                .build();

        for (String dataSource : DATA_SOURCES) {
            Retry retry = registry.retry("credit-" + dataSource, config);
            retries.put(dataSource, retry);

            retry.getEventPublisher()
                    .onRetry(event -> log.info("[重试-{}] 第{}次重试, 异常: {}",
                            dataSource, event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage()))
                    .onSuccess(event -> log.debug("[重试-{}] 重试成功, 总尝试次数: {}",
                            dataSource, event.getNumberOfRetryAttempts() + 1))
                    .onError(event -> log.warn("[重试-{}] 重试{}次后仍失败: {}",
                            dataSource, event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage()));

            log.info("[重试-{}] 初始化完成, maxAttempts: {}, waitDuration: {}ms",
                    dataSource, maxRetryAttempts, retryWaitDurationMs);
        }

        return retries;
    }

    @Bean
    public Map<String, TimeLimiter> creditTimeLimiters() {
        Map<String, TimeLimiter> timeLimiters = new HashMap<>();

        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .cancelRunningFuture(true)
                .build();

        for (String dataSource : DATA_SOURCES) {
            TimeLimiter timeLimiter = TimeLimiter.of("credit-" + dataSource, config);
            timeLimiters.put(dataSource, timeLimiter);
            log.info("[超时控制-{}] 初始化完成, timeout: {}ms", dataSource, timeoutMs);
        }

        return timeLimiters;
    }
}

package org.twins.mcp.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Resilience4j retry policy for outbound twins REST calls (ARCH-15; Story 1.4 AC-6).
 *
 * <p>3 attempts with exponential backoff (100 ms → 200 ms), retrying on transient faults only:
 * HTTP 5xx ({@link HttpServerErrorException}) and connection/IO errors
 * ({@link ResourceAccessException}). 4xx responses are deterministic and are NOT retried — a 401 is
 * handled by the single-replay in {@link org.twins.mcp.client.TwinsHeadersInterceptor}, and other
 * 4xx surface immediately.
 */
@Configuration
public class ResilienceConfig {

    public static final String TWINS_RETRY_NAME = "twins";

    @Bean
    public Retry twinsRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2.0))
                .retryOnException(e ->
                        e instanceof HttpServerErrorException
                                || e instanceof ResourceAccessException)
                .build();
        return Retry.of(TWINS_RETRY_NAME, config);
    }
}

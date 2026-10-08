package org.twins.mcp.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.twins.mcp.client.TwinsHeadersInterceptor;

/**
 * Two distinct {@link RestClient} beans (Story 1.4 AC-8; T5.4):
 *
 * <ul>
 *   <li>{@code m2mRestClient} — for {@code POST /auth/m2m/token/v1}. No headers interceptor (the
 *       auth call must not loop on a missing AuthToken) and no retry (fetching a token IS the
 *       recovery action for a downstream 401).</li>
 *   <li>{@code twinsRestClient} — for all other calls. Carries {@link TwinsHeadersInterceptor}
 *       (DomainId + AuthToken, 401 single-replay) and is wrapped in the {@code twins} Retry by
 *       {@link org.twins.mcp.client.TwinsRestClient}.</li>
 * </ul>
 *
 * <p>Per-attempt timeout is 30 s, enforced at the HTTP layer via the request factory. Resilience4j
 * {@code TimeLimiter} is intentionally not used: it is async-oriented and cannot reliably interrupt a
 * blocking sync RestClient call (see Story 1.4 Completion Notes).
 */
@Configuration
public class RestClientConfig {

    /** Per-attempt call budget (AC-6 "30 s max per attempt"). */
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public ClientHttpRequestFactory twinsHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CALL_TIMEOUT.toMillis());
        factory.setReadTimeout((int) CALL_TIMEOUT.toMillis());
        return factory;
    }

    @Bean
    public RestClient m2mRestClient(TwinsConnectionProperties connection,
                                    ClientHttpRequestFactory twinsHttpRequestFactory) {
        return RestClient.builder()
                .baseUrl(connection.baseUrl())
                .requestFactory(twinsHttpRequestFactory)
                .build();
    }

    @Bean
    public RestClient twinsRestClient(TwinsConnectionProperties connection,
                                      ClientHttpRequestFactory twinsHttpRequestFactory,
                                      TwinsHeadersInterceptor headersInterceptor) {
        return RestClient.builder()
                .baseUrl(connection.baseUrl())
                .requestFactory(twinsHttpRequestFactory)
                .requestInterceptor(headersInterceptor)
                .build();
    }
}

package org.twins.mcp.client;

import io.github.resilience4j.retry.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Façade over the configured {@code twinsRestClient} bean (Story 1.4 AC-8). Tools inject this and
 * nothing else — no component constructs its own {@link RestClient}.
 *
 * <p>Each call is wrapped in the {@code twins} {@link Retry} (3 attempts, exponential backoff) so
 * transient 5xx and connection errors are retried, while the {@link TwinsHeadersInterceptor} (applied
 * to the bean) handles auth and the 401 single-replay.
 */
@Component
public class TwinsRestClient {

    private final RestClient restClient;
    private final Retry retry;

    public TwinsRestClient(@Qualifier("twinsRestClient") RestClient restClient, Retry retry) {
        this.restClient = restClient;
        this.retry = retry;
    }

    public <T> T post(String path, Object body, ParameterizedTypeReference<T> type) {
        return retry.executeSupplier(() ->
                restClient.post().uri(path).body(body).retrieve().body(type));
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return retry.executeSupplier(() ->
                restClient.post().uri(path).body(body).retrieve().body(type));
    }

    public <T> T get(String path, Class<T> type) {
        return retry.executeSupplier(() ->
                restClient.get().uri(path).retrieve().body(type));
    }
}

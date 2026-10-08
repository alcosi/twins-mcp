package org.twins.mcp.client;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.twins.mcp.config.TwinsConnectionProperties;
import org.twins.mcp.error.TwinsPermissionDenied;

/**
 * Single chokepoint that attaches the twins-mandated headers to every outbound call (Story 1.4
 * AC-4 / AC-5; ARCH-7; NFR-TM-004).
 *
 * <ul>
 *   <li>{@code DomainId} — from {@code TWINS_DOMAIN_ID}; OVERWRITES any caller value (immutable).</li>
 *   <li>{@code AuthToken} — from {@link TokenHolder}; OVERWRITES any caller value (immutable).</li>
 * </ul>
 *
 * <p>Header names are the twins-defined literals {@code "DomainId"} / {@code "AuthToken"} (matching
 * {@code HttpRequestService.HEADER_DOMAIN_ID} / {@code HEADER_AUTH_TOKEN}); NOT kebab-case, NOT
 * standard {@code Authorization: Bearer}.
 *
 * <p>401 recovery (AC-3): on the first 401 the cached token is invalidated, a fresh token is fetched,
 * and the request is replayed EXACTLY ONCE within this interceptor invocation. A second 401 raises
 * {@link TwinsPermissionDenied}. Because the replay happens inside one {@code intercept} call (it
 * does not re-enter the interceptor chain), no loop-guard attribute is required.
 */
@Component
public class TwinsHeadersInterceptor implements ClientHttpRequestInterceptor {

    public static final String DOMAIN_ID_HEADER = "DomainId";
    public static final String AUTH_TOKEN_HEADER = "AuthToken";

    private final TwinsConnectionProperties connection;
    private final TokenHolder tokenHolder;
    private final TwinsM2MClient m2mClient;

    public TwinsHeadersInterceptor(TwinsConnectionProperties connection,
                                   TokenHolder tokenHolder,
                                   TwinsM2MClient m2mClient) {
        this.connection = connection;
        this.tokenHolder = tokenHolder;
        this.m2mClient = m2mClient;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        // Proactively ensure a token so the first call does not waste a 401 round-trip (T3.3).
        if (tokenHolder.getToken().isEmpty()) {
            m2mClient.fetchNewToken();
        }
        applyHeaders(request);
        ClientHttpResponse response = execution.execute(request, body);

        if (response.getStatusCode().value() == 401) {
            response.close();
            tokenHolder.invalidate();
            m2mClient.fetchNewToken(); // throws TwinsPermissionDenied/TwinsUnavailable on auth failure
            applyHeaders(request);
            ClientHttpResponse replayed = execution.execute(request, body);
            if (replayed.getStatusCode().value() == 401) {
                throw new TwinsPermissionDenied("Twins rejected the refreshed token (repeated 401)");
            }
            return replayed;
        }
        return response;
    }

    private void applyHeaders(HttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        // OVERWRITE caller-supplied values — these headers are immutable from tool args (NFR-TM-004).
        headers.set(DOMAIN_ID_HEADER, connection.domainId());
        String token = tokenHolder.getToken().orElse(null);
        if (token != null) {
            headers.set(AUTH_TOKEN_HEADER, token);
        } else {
            headers.remove(AUTH_TOKEN_HEADER);
        }
    }
}

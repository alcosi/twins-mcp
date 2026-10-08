package org.twins.mcp.client;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.twins.core.dto.rest.auth.AuthM2MLoginRqDTOv1;
import org.twins.core.dto.rest.auth.AuthM2MTokenRsDTOv1;
import org.twins.mcp.config.M2mCredentialsProperties;
import org.twins.mcp.config.TwinsConnectionProperties;
import org.twins.mcp.error.TwinsPermissionDenied;
import org.twins.mcp.error.TwinsUnavailable;

/**
 * Fetches M2M tokens from {@code POST /auth/m2m/token/v1} (the {@code /login/v1} sibling is
 * {@code @Deprecated}) and caches them in {@link TokenHolder} (Story 1.4 AC-1 / AC-3).
 *
 * <p>Uses a dedicated {@code m2mRestClient} bean with NO {@link TwinsHeadersInterceptor} — the auth
 * call must not carry an {@code AuthToken} header (none exists yet) and must not retry: fetching a
 * new token IS the recovery action for a 401 on a downstream call. The {@code DomainId} header is
 * sent explicitly because the twins endpoint is annotated {@code @ParameterDomainHeader}.
 *
 * <p>Token + expiry are read from {@code authData} under keys {@code auth_token} and
 * {@code auth_token_expires_at} (ISO-8601 instant). There is no {@code expires_in} seconds field.
 */
@Component
public class TwinsM2MClient {

    private static final Logger log = LoggerFactory.getLogger(TwinsM2MClient.class);

    /** Current M2M token endpoint; the {@code /login/v1} sibling is {@code @Deprecated}. */
    public static final String TOKEN_PATH = "/auth/m2m/token/v1";

    /** authData keys — the constants live in twins' ClientSideAuthData, absent from the DTO artifact. */
    static final String AUTH_TOKEN_KEY = "auth_token";
    static final String AUTH_TOKEN_EXPIRES_AT_KEY = "auth_token_expires_at";

    /** Refresh this many seconds before the server-advertised absolute expiry (ARCH-6 safety margin). */
    static final long TTL_SAFETY_MARGIN_SECONDS = 60;

    /** Fallback TTL when the server omits auth_token_expires_at (e.g. Alcosi external IdP). */
    static final long FALLBACK_TTL_SECONDS = 50 * 60;

    private final RestClient m2mRestClient;
    private final M2mCredentialsProperties creds;
    private final TwinsConnectionProperties connection;
    private final TokenHolder tokenHolder;

    public TwinsM2MClient(@Qualifier("m2mRestClient") RestClient m2mRestClient,
                          M2mCredentialsProperties creds,
                          TwinsConnectionProperties connection,
                          TokenHolder tokenHolder) {
        this.m2mRestClient = m2mRestClient;
        this.creds = creds;
        this.connection = connection;
        this.tokenHolder = tokenHolder;
    }

    /**
     * Obtains a fresh token and stores it in {@link TokenHolder}. Throws {@link TwinsPermissionDenied}
     * on a 4xx (bad/forbidden credentials) and {@link TwinsUnavailable} on connection errors, 5xx,
     * or a malformed response.
     */
    public void fetchNewToken() {
        AuthM2MLoginRqDTOv1 rq = new AuthM2MLoginRqDTOv1();
        rq.clientId = creds.clientId();
        rq.clientSecret = creds.clientSecret();
        String publicKeyId = creds.publicKeyId();
        if (publicKeyId != null && !publicKeyId.isBlank()) {
            rq.publicKeyId = UUID.fromString(publicKeyId.trim());
        }

        AuthM2MTokenRsDTOv1 rs;
        try {
            rs = m2mRestClient.post()
                    .uri(TOKEN_PATH)
                    .header(TwinsHeadersInterceptor.DOMAIN_ID_HEADER, connection.domainId())
                    .body(rq)
                    .retrieve()
                    .body(AuthM2MTokenRsDTOv1.class);
        } catch (HttpClientErrorException e) {
            // 4xx — bad/forbidden credentials or domain mismatch. No retry on the auth call itself.
            throw new TwinsPermissionDenied(
                    "M2M auth rejected by twins (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (ResourceAccessException e) {
            throw new TwinsUnavailable("M2M auth endpoint unreachable", e);
        } catch (HttpServerErrorException e) {
            throw new TwinsUnavailable(
                    "M2M auth failed (HTTP " + e.getStatusCode().value() + ")", e);
        }

        if (rs == null || rs.authData == null) {
            throw new TwinsUnavailable("M2M auth returned an empty response");
        }
        Map<String, String> data = rs.authData;
        String token = data.get(AUTH_TOKEN_KEY);
        if (token == null || token.isBlank()) {
            throw new TwinsUnavailable("M2M auth response carried no auth_token");
        }
        long threshold = computeRefreshThreshold(data.get(AUTH_TOKEN_EXPIRES_AT_KEY));
        tokenHolder.setToken(token, threshold);
        log.debug("M2M token acquired");
    }

    /**
     * Converts the absolute ISO-8601 {@code auth_token_expires_at} into a refresh threshold
     * (expiry minus the safety margin). Falls back to a fixed TTL when the field is absent or
     * unparseable.
     */
    long computeRefreshThreshold(String expiresAtIso) {
        long now = System.currentTimeMillis();
        if (expiresAtIso == null || expiresAtIso.isBlank()) {
            log.warn("auth_token_expires_at absent; using fallback TTL of {}s", FALLBACK_TTL_SECONDS);
            return now + FALLBACK_TTL_SECONDS * 1000L;
        }
        try {
            long serverExpiry = Instant.parse(expiresAtIso).toEpochMilli();
            long threshold = serverExpiry - TTL_SAFETY_MARGIN_SECONDS * 1000L;
            if (threshold <= now) {
                // Clock skew or a near-zero server TTL — cache briefly; the 401-replay path refreshes.
                log.warn("auth_token already expired at server time; caching with minimal TTL");
                return now + 5_000L;
            }
            return threshold;
        } catch (DateTimeParseException e) {
            log.warn("Unparseable auth_token_expires_at '{}'; using fallback TTL", expiresAtIso);
            return now + FALLBACK_TTL_SECONDS * 1000L;
        }
    }
}

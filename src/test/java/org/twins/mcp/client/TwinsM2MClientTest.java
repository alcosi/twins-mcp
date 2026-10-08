package org.twins.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.twins.mcp.config.M2mCredentialsProperties;
import org.twins.mcp.config.TwinsConnectionProperties;
import org.twins.mcp.error.TwinsPermissionDenied;
import org.twins.mcp.error.TwinsUnavailable;
import org.twins.mcp.secrets.SecretsSanitiser;

/**
 * Tests for {@link TwinsM2MClient} (AC-1, AC-6). Uses {@link MockRestServiceServer} bound to a
 * {@link RestClient.Builder}; no Spring context.
 */
class TwinsM2MClientTest {

    private static final String DOMAIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String BASE_URL = "http://twins.test";
    private static final String TOKEN_URL = BASE_URL + TwinsM2MClient.TOKEN_PATH;

    private MockRestServiceServer server;
    private TokenHolder tokenHolder;
    private TwinsM2MClient client;

    @BeforeEach
    void setUp() {
        SecretsSanitiser.getInstance().clearForTest();
        tokenHolder = new TokenHolder();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient m2mRestClient = builder.build();
        M2mCredentialsProperties creds = new M2mCredentialsProperties("client-id", "s3cret", null);
        TwinsConnectionProperties conn = new TwinsConnectionProperties(BASE_URL, DOMAIN_ID);
        client = new TwinsM2MClient(m2mRestClient, creds, conn, tokenHolder);
    }

    @AfterEach
    void tearDown() {
        SecretsSanitiser.getInstance().clearForTest();
    }

    @Test
    @DisplayName("AC-1: POSTs /auth/m2m/token/v1 with DomainId + body; caches the token")
    void fetchHappyPath() {
        String futureIso = Instant.now().plusSeconds(3600).toString();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("DomainId", DOMAIN_ID))
                .andExpect(jsonPath("$.clientId", equalTo("client-id")))
                .andExpect(jsonPath("$.clientSecret", equalTo("s3cret")))
                .andRespond(withSuccess(
                        "{\"authData\":{\"auth_token\":\"tok-123\",\"auth_token_expires_at\":\""
                                + futureIso + "\"}}",
                        MediaType.APPLICATION_JSON));

        client.fetchNewToken();
        server.verify();
        assertThat(tokenHolder.getToken()).hasValue("tok-123");
    }

    @Test
    @DisplayName("AC-1: HTTP 401 -> TwinsPermissionDenied (no retry on the auth call)")
    void auth401mapsToPermissionDenied() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(client::fetchNewToken).isInstanceOf(TwinsPermissionDenied.class);
    }

    @Test
    @DisplayName("AC-1: HTTP 403 -> TwinsPermissionDenied")
    void auth403mapsToPermissionDenied() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(client::fetchNewToken).isInstanceOf(TwinsPermissionDenied.class);
    }

    @Test
    @DisplayName("AC-6: HTTP 5xx -> TwinsUnavailable")
    void auth5xxmapsToUnavailable() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(client::fetchNewToken).isInstanceOf(TwinsUnavailable.class);
    }

    @Test
    @DisplayName("200 with no auth_token -> TwinsUnavailable")
    void noTokenInResponse() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"authData\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::fetchNewToken).isInstanceOf(TwinsUnavailable.class);
    }

    @Test
    @DisplayName("AC-2: refresh threshold = server expiry - 60s margin")
    void thresholdAppliesMargin() {
        long serverExpiry = System.currentTimeMillis() + 3_600_000L;
        long threshold = client.computeRefreshThreshold(Instant.ofEpochMilli(serverExpiry).toString());
        assertThat(threshold).isBetween(serverExpiry - 61_000L, serverExpiry - 59_000L);
    }

    @Test
    @DisplayName("AC-2: absent expires_at -> fallback TTL (~50 min)")
    void thresholdNullFallback() {
        long before = System.currentTimeMillis();
        long threshold = client.computeRefreshThreshold(null);
        long fallback = TwinsM2MClient.FALLBACK_TTL_SECONDS * 1000L;
        assertThat(threshold).isBetween(before + fallback - 1_000L, System.currentTimeMillis() + fallback + 1_000L);
    }

    @Test
    @DisplayName("AC-2: unparseable expires_at -> fallback TTL (~50 min)")
    void thresholdUnparseableFallback() {
        long before = System.currentTimeMillis();
        long threshold = client.computeRefreshThreshold("not-a-date");
        long fallback = TwinsM2MClient.FALLBACK_TTL_SECONDS * 1000L;
        assertThat(threshold).isBetween(before + fallback - 1_000L, System.currentTimeMillis() + fallback + 1_000L);
    }

    @Test
    @DisplayName("AC-2: already-expired -> minimal TTL (rely on 401-replay)")
    void thresholdAlreadyExpiredMinimalTtl() {
        long before = System.currentTimeMillis();
        long threshold = client.computeRefreshThreshold(Instant.now().minusSeconds(10).toString());
        assertThat(threshold).isBetween(before, System.currentTimeMillis() + 6_000L);
    }
}

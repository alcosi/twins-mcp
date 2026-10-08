package org.twins.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.twins.mcp.config.TwinsConnectionProperties;
import org.twins.mcp.error.TwinsPermissionDenied;
import org.twins.mcp.secrets.SecretsSanitiser;

/**
 * Tests for {@link TwinsHeadersInterceptor} (AC-3, AC-4, AC-5). The interceptor is wired onto a
 * {@link RestClient} backed by {@link MockRestServiceServer}; {@link TwinsM2MClient} is mocked.
 */
class TwinsHeadersInterceptorTest {

    private static final String DOMAIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String BASE_URL = "http://twins.test";

    private MockRestServiceServer server;
    private RestClient twinsRestClient;
    private TokenHolder tokenHolder;
    private TwinsM2MClient m2mClient;

    @BeforeEach
    void setUp() {
        SecretsSanitiser.getInstance().clearForTest();
        tokenHolder = new TokenHolder();
        m2mClient = mock(TwinsM2MClient.class);
        TwinsConnectionProperties conn = new TwinsConnectionProperties(BASE_URL, DOMAIN_ID);
        TwinsHeadersInterceptor interceptor = new TwinsHeadersInterceptor(conn, tokenHolder, m2mClient);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        twinsRestClient = builder.requestInterceptor(interceptor).build();
    }

    @AfterEach
    void tearDown() {
        SecretsSanitiser.getInstance().clearForTest();
    }

    private void stubFetchInstalls(String token) {
        doAnswer(inv -> {
            tokenHolder.setToken(token, System.currentTimeMillis() + 3_600_000L);
            return null;
        }).when(m2mClient).fetchNewToken();
    }

    @Test
    @DisplayName("AC-4/AC-5: DomainId + AuthToken applied and overwrite caller values (immutable)")
    void headersAppliedAndOverwriteCallerValues() {
        tokenHolder.setToken("real-token", System.currentTimeMillis() + 3_600_000L);
        server.expect(requestTo(BASE_URL + "/ping"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("DomainId", DOMAIN_ID))
                .andExpect(header("AuthToken", "real-token"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        // Caller attempts to override both headers — the interceptor must overwrite them.
        String body = twinsRestClient.get().uri("/ping")
                .header("DomainId", "EVIL")
                .header("AuthToken", "EVIL")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("ok");
        server.verify();
        verify(m2mClient, times(0)).fetchNewToken(); // token present -> no proactive fetch
    }

    @Test
    @DisplayName("AC-3: 401 -> invalidate, fetch fresh token, single replay with the new token")
    void replayOn401WithFreshToken() {
        tokenHolder.setToken("old-token", System.currentTimeMillis() + 3_600_000L);
        stubFetchInstalls("fresh-token");

        server.expect(requestTo(BASE_URL + "/data")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/data"))
                .andExpect(header("AuthToken", "fresh-token"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String body = twinsRestClient.get().uri("/data").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        verify(m2mClient, times(1)).fetchNewToken();
        server.verify();
    }

    @Test
    @DisplayName("AC-3: a second 401 after refresh -> TwinsPermissionDenied")
    void second401throwsPermissionDenied() {
        tokenHolder.setToken("old-token", System.currentTimeMillis() + 3_600_000L);
        stubFetchInstalls("fresh-token");

        server.expect(requestTo(BASE_URL + "/data")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/data")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> twinsRestClient.get().uri("/data").retrieve().body(String.class))
                .isInstanceOf(TwinsPermissionDenied.class);
        // Only the first 401 triggers a fetch; the replay 401 throws directly.
        verify(m2mClient, times(1)).fetchNewToken();
        server.verify();
    }

    @Test
    @DisplayName("AC-3: empty cache -> proactive fetch before the first attempt")
    void proactiveFetchWhenCacheEmpty() {
        stubFetchInstalls("first-token");
        server.expect(requestTo(BASE_URL + "/ping"))
                .andExpect(header("AuthToken", "first-token"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String body = twinsRestClient.get().uri("/ping").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        verify(m2mClient, times(1)).fetchNewToken();
        server.verify();
    }
}

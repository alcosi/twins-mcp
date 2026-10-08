package org.twins.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.twins.mcp.secrets.SecretsSanitiser;

/**
 * Unit tests for {@link TokenHolder} — AC-2 (TTL cache), AC-3 (invalidate), AC-7 (token registered
 * with the sanitiser, never leaked). Pure JUnit; no Spring context.
 */
class TokenHolderTest {

    private TokenHolder holder;
    private SecretsSanitiser sanitiser;

    @BeforeEach
    void setUp() {
        sanitiser = SecretsSanitiser.getInstance();
        sanitiser.clearForTest();
        holder = new TokenHolder();
    }

    @AfterEach
    void tearDown() {
        sanitiser.clearForTest();
    }

    @Test
    @DisplayName("AC-2: empty by default")
    void emptyByDefault() {
        assertThat(holder.getToken()).isEmpty();
    }

    @Test
    @DisplayName("AC-2: setToken then getToken returns the value within threshold")
    void setAndGet() {
        holder.setToken("tok-1", System.currentTimeMillis() + 3_600_000L);
        assertThat(holder.getToken()).hasValue("tok-1");
    }

    @Test
    @DisplayName("AC-2: getToken is empty once the threshold is reached")
    void expiresAtThreshold() {
        holder.setToken("tok-1", System.currentTimeMillis() - 1_000L);
        assertThat(holder.getToken()).isEmpty();
    }

    @Test
    @DisplayName("AC-3: invalidate clears the token")
    void invalidateClears() {
        holder.setToken("tok-1", System.currentTimeMillis() + 3_600_000L);
        holder.invalidate();
        assertThat(holder.getToken()).isEmpty();
    }

    @Test
    @DisplayName("AC-7: setToken registers the token with the sanitiser")
    void registersWithSanitiser() {
        holder.setToken("secret-token-xyz", System.currentTimeMillis() + 3_600_000L);
        assertThat(sanitiser.sanitise("X-Auth: secret-token-xyz")).contains("[REDACTED:authtoken]");
    }

    @Test
    @DisplayName("AC-7: replacing the token unregisters the previous value (bounded registry)")
    void replacingUnregistersOld() {
        holder.setToken("old-token-aaa", System.currentTimeMillis() + 3_600_000L);
        holder.setToken("new-token-bbb", System.currentTimeMillis() + 3_600_000L);
        String out = sanitiser.sanitise("old=old-token-aaa new=new-token-bbb");
        assertThat(out).contains("[REDACTED:authtoken]");
        assertThat(out).doesNotContain("new-token-bbb");
        assertThat(out).contains("old-token-aaa"); // old unregistered -> visible
    }

    @Test
    @DisplayName("AC-7: invalidate-then-replace still bounds the registry")
    void invalidateThenReplaceBoundsRegistry() {
        holder.setToken("old-token-aaa", System.currentTimeMillis() + 3_600_000L);
        holder.invalidate();
        holder.setToken("new-token-bbb", System.currentTimeMillis() + 3_600_000L);
        assertThat(sanitiser.sanitise("old-token-aaa")).contains("old-token-aaa");
        assertThat(sanitiser.sanitise("new-token-bbb")).contains("[REDACTED:authtoken]");
    }
}

package org.twins.mcp.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.twins.mcp.secrets.SecretsSanitiser;

/**
 * In-memory cache for the live M2M auth token (ARCH-2, ARCH-6; Story 1.4 AC-2 / AC-3 / AC-7).
 *
 * <p>Singleton-scoped bean holding the current token plus the absolute epoch-millis threshold before
 * which it must be refreshed. The threshold is supplied by {@link TwinsM2MClient}, which parses the
 * {@code auth_token_expires_at} ISO-8601 timestamp from the twins response and subtracts a 60 s
 * safety margin — the M2M response has NO {@code expires_in} seconds field (see Story 1.4 Completion
 * Notes).
 *
 * <p>Token-discipline (NFR-TM-003): the value is NEVER logged, NEVER returned by {@code toString},
 * and NEVER compared in {@code equals/hashCode}. {@link #getToken()} returns {@link Optional} so
 * callers cannot silently hold a raw {@code null}. {@link #setToken(String, long)} registers the
 * value with {@link SecretsSanitiser} so any accidental log emission is masked, and unregisters the
 * previously-registered value so the sanitiser's dynamic registry stays bounded across rotations.
 */
@Component
public class TokenHolder {

    private static final Logger log = LoggerFactory.getLogger(TokenHolder.class);

    private volatile String token;
    private volatile long expiresAtMillis;

    /** Value currently registered with the sanitiser (guarded by the synchronized mutators). */
    private String registeredValue;

    /**
     * @return the cached token if it is present and still within its refresh threshold, otherwise
     *         {@link Optional#empty()} to signal that the caller must fetch a fresh token.
     */
    public Optional<String> getToken() {
        String t = token;
        if (t == null || System.currentTimeMillis() >= expiresAtMillis) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    /**
     * Stores the token and its refresh threshold (already margin-adjusted by the caller), and keeps
     * the sanitiser registry in sync.
     */
    public synchronized void setToken(String token, long expiresAtEpochMilli) {
        SecretsSanitiser sanitiser = SecretsSanitiser.getInstance();
        if (registeredValue != null && !registeredValue.equals(token)) {
            sanitiser.unregisterDynamic(registeredValue);
        }
        if (token != null) {
            sanitiser.registerDynamic(token, "authtoken");
        }
        this.registeredValue = token;
        this.token = token;
        this.expiresAtMillis = expiresAtEpochMilli;
        log.debug("M2M token cache updated");
    }

    /**
     * Clears the cached token so the next call refetches. The previously-registered value is left
     * registered with the sanitiser defensively (a just-rejected token may still be in flight in
     * log buffers); it is unregistered when a replacement arrives via {@link #setToken}.
     */
    public synchronized void invalidate() {
        this.token = null;
        this.expiresAtMillis = 0L;
        log.debug("M2M token cache invalidated (401 observed)");
    }
}

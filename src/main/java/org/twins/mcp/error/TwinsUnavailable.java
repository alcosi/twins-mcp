package org.twins.mcp.error;

/**
 * Raised when twins is unreachable or returns a transient 5xx after Resilience4j retries are
 * exhausted, or when the M2M auth endpoint fails to yield a usable token (Story 1.4 AC-1/AC-6).
 *
 * <p>Story 1.6's {@code ErrorEnvelopeMapper} maps this to envelope code {@code TWINS_UNAVAILABLE}.
 * Messages MUST NOT carry token/secret values (NFR-TM-003); attach the underlying exception as the
 * cause so its stack trace is logged separately and sanitised by {@code SecretsSanitiser}.
 */
public class TwinsUnavailable extends RuntimeException {

    public TwinsUnavailable(String message) {
        super(message);
    }

    public TwinsUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}

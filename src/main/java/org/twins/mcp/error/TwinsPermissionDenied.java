package org.twins.mcp.error;

/**
 * Raised when twins rejects a call for authorization reasons — M2M credentials invalid/forbidden
 * (HTTP 401/403 on the auth endpoint), or a repeated 401 after a token refresh on a downstream
 * call (Story 1.4 AC-3).
 *
 * <p>Story 1.6's {@code ErrorEnvelopeMapper} maps this to envelope code {@code TWINS_PERMISSION_DENIED}.
 * Messages MUST NOT carry token/secret values (NFR-TM-003); attach the underlying exception as the
 * cause so its stack trace is logged separately and sanitised by {@code SecretsSanitiser}.
 */
public class TwinsPermissionDenied extends RuntimeException {

    public TwinsPermissionDenied(String message) {
        super(message);
    }

    public TwinsPermissionDenied(String message, Throwable cause) {
        super(message, cause);
    }
}

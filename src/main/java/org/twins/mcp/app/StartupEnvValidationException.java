package org.twins.mcp.app;

/**
 * Thrown by {@link StartupEnvValidator} when required twins env vars are missing or malformed.
 *
 * <p>{@link #getMessage()} deliberately references env var NAMES only ({@code TWINS_BASE_URL} etc.)
 * — never their VALUES. This is the first line of defense against secret leakage (NFR-TM-003);
 * Story 1.3's Logback secrets sanitiser is the second.
 */
public class StartupEnvValidationException extends RuntimeException {

    public StartupEnvValidationException(String message) {
        super(message);
    }
}

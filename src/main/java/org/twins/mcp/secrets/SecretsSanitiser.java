package org.twins.mcp.secrets;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless-ish redaction engine for log lines (ARCH-8, AC-2..AC-5, AC-8).
 *
 * <p>Singleton via {@link #getInstance()} so that Logback-instantiated layouts and Spring-managed
 * beans share the same dynamic-value registry. Story 1.4's {@code TokenHolder} registers the
 * live AuthToken value via {@link #registerDynamic(String, String)}; the static
 * {@code TWINS_M2M_CLIENT_SECRET} literal is registered by {@link LoggingConfig} at
 * {@code ApplicationStartedEvent}.
 *
 * <p>Pattern-based redaction (over-redaction is acceptable per ARCH-8):
 * <ul>
 *   <li>JWT — 3 base64-url segments separated by dots (AC-2)</li>
 *   <li>{@code Bearer <token>} prefix (AC-5)</li>
 *   <li>Standalone base64 blobs ≥ 32 chars (AC-5)</li>
 *   <li>Static secret literal — case-insensitive (AC-3)</li>
 *   <li>Dynamic values — exact match, tagged (AC-4)</li>
 * </ul>
 *
 * <p>{@link #sanitise(String)} NEVER throws — on any internal exception it returns
 * {@code "[REDACTED:error]"} so the log line still goes through (AC-8).
 */
public final class SecretsSanitiser {

    /** Tag used when the sanitiser itself fails — preserves the log line, signals trouble. */
    public static final String ERROR_TAG = "[REDACTED:error]";

    /**
     * JWT: 3 base64url segments separated by dots, with standalone boundaries. The lookbehind
     * and lookahead exclude {@code .} so that a 3-segment run embedded in a longer dotted path
     * (e.g. a Java FQN like {@code com.example.MyClass.autoconfigure.annotations.X}) does NOT
     * match. Per AC-2 the total length must also be ≥ 32 chars; the length check is applied in
     * {@link #sanitise(String)} before replacing (avoids false positives like {@code v0.1.0}).
     */
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_.-])[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_.-])");

    /** Minimum total length for a 3-segment dotted string to count as a JWT (AC-2). */
    private static final int JWT_MIN_TOTAL_LENGTH = 32;

    /** {@code Bearer <token>} prefix, case-insensitive. Captures the token along with the prefix. */
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+\\S+");

    /**
     * Standalone base64 blob — ≥ 32 chars of base64 content followed by 0-2 {@code =} padding
     * chars (AC-5). Padding chars are NOT in the main char class so that key=value contexts like
     * {@code v=abc...} are not over-matched. Boundaries also exclude {@code .} so that a Java
     * class name at the end of an FQN (preceded by {@code .}) is not over-matched.
     */
    private static final Pattern BASE64 = Pattern.compile(
            "(?<![A-Za-z0-9+/=.])[A-Za-z0-9+/]{32,}={0,2}(?![A-Za-z0-9+/=.])");

    private static final SecretsSanitiser INSTANCE = new SecretsSanitiser();

    private volatile String secretLiteral;
    private final java.util.concurrent.CopyOnWriteArrayList<DynamicValue> dynamicValues =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private SecretsSanitiser() {}

    public static SecretsSanitiser getInstance() {
        return INSTANCE;
    }

    /** Sets the static secret literal (case-insensitive match). Null/blank disables. */
    public void setSecretLiteral(String value) {
        this.secretLiteral = (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Registers a dynamic value (e.g., AuthToken) with a tag. The same value registered twice
     * with different tags updates the tag (last wins). Null/blank values are ignored.
     */
    public void registerDynamic(String value, String tag) {
        if (value == null || value.isBlank()) return;
        // Remove any existing entry with the same value, then add. CopyOnWriteArrayList makes
        // this O(n) per call, but the dynamic registry stays small (1 entry for AuthToken).
        dynamicValues.removeIf(dv -> value.equals(dv.value));
        dynamicValues.add(new DynamicValue(value, tag));
    }

    /** Removes a previously-registered dynamic value (e.g., when AuthToken refreshes). */
    public void unregisterDynamic(String value) {
        if (value == null) return;
        dynamicValues.removeIf(dv -> value.equals(dv.value));
    }

    /** Test-only: clears all state. Not safe for production use. */
    public void clearForTest() {
        secretLiteral = null;
        dynamicValues.clear();
    }

    /**
     * Returns a sanitised copy of {@code input} with all known secret patterns replaced by
     * their respective tags. Returns the input unchanged if null/empty. Returns
     * {@link #ERROR_TAG} on any internal exception (AC-8).
     */
    public String sanitise(String input) {
        if (input == null || input.isEmpty()) return input;
        try {
            String result = input;
            result = replaceJwt(result);
            result = BEARER.matcher(result).replaceAll("[REDACTED:bearer]");
            result = BASE64.matcher(result).replaceAll("[REDACTED:base64]");

            String secret = secretLiteral;
            if (secret != null && !secret.isEmpty()) {
                result = replaceLiteralIgnoreCase(result, secret, "[REDACTED:secret]");
            }
            // Dynamic values — exact case-sensitive match. Iterate snapshot via toArray to avoid
            // COW sublist issues if a concurrent register/unregister happens mid-iteration.
            for (Object o : dynamicValues.toArray()) {
                DynamicValue dv = (DynamicValue) o;
                result = result.replace(dv.value, "[REDACTED:" + dv.tag + "]");
            }
            return result;
        } catch (Throwable t) {
            // Sanitiser NEVER throws (AC-8) — fall back to ERROR_TAG so the line still goes out.
            return ERROR_TAG;
        }
    }

    /**
     * JWT replacement with total-length check. The 3-segment dotted pattern matches short strings
     * like {@code v0.1.0}; AC-2 requires ≥ 32 chars total. We iterate matches and only replace
     * those that meet the length threshold.
     */
    private String replaceJwt(String input) {
        Matcher m = JWT.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String match = m.group();
            String replacement = match.length() >= JWT_MIN_TOTAL_LENGTH
                    ? "[REDACTED:jwt]"
                    : Matcher.quoteReplacement(match);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Case-insensitive literal-string replacement. Uses {@link Pattern#CASE_INSENSITIVE} combined
     * with {@link Pattern#UNICODE_CASE} so Cyrillic (and other non-ASCII) secrets match
     * case-insensitively per AC-3.
     */
    private static String replaceLiteralIgnoreCase(String haystack, String needle, String replacement) {
        Pattern p = Pattern.compile(
                Pattern.quote(needle), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m = p.matcher(haystack);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private record DynamicValue(String value, String tag) {}
}

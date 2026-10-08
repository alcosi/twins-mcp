package org.twins.mcp.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link SecretsSanitiser} covering AC-2..AC-5, AC-8. Each test resets the
 * singleton via {@link SecretsSanitiser#clearForTest()} to keep state isolated.
 */
class SecretsSanitiserTest {

    private SecretsSanitiser sanitiser;

    @BeforeEach
    void setUp() {
        sanitiser = SecretsSanitiser.getInstance();
        sanitiser.clearForTest();
    }

    @AfterEach
    void tearDown() {
        sanitiser.clearForTest();
    }

    // ----- AC-2 JWT redaction ------------------------------------------------

    @Test
    @DisplayName("AC-2: standard JWT replaced with [REDACTED:jwt]")
    void jwtReplaced() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String input = "Authorization header: " + jwt;
        assertThat(sanitiser.sanitise(input)).contains("[REDACTED:jwt]");
        assertThat(sanitiser.sanitise(input)).doesNotContain(jwt);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not.a.url but multiple dots",
        "single",
        "two.parts",
        "no.special.chars"
    })
    @DisplayName("AC-2 negative: non-JWT patterns NOT redacted")
    void nonJwtNotRedacted(String candidate) {
        // None of these are 3-segment base64-with-dots ≥ the JWT pattern.
        String input = "ctx: " + candidate;
        // The assertion: original input passes through unchanged for the JWT tag specifically.
        // (Some of these may match BASE64 — but never [REDACTED:jwt].)
        assertThat(sanitiser.sanitise(input)).doesNotContain("[REDACTED:jwt]");
    }

    // ----- AC-3 secret literal redaction -------------------------------------

    @Test
    @DisplayName("AC-3: secret literal replaced with [REDACTED:secret] (case-sensitive input)")
    void secretLiteralReplaced() {
        sanitiser.setSecretLiteral("topsecret123");
        String input = "Using secret topsecret123 for auth";
        assertThat(sanitiser.sanitise(input)).contains("[REDACTED:secret]");
        assertThat(sanitiser.sanitise(input)).doesNotContain("topsecret123");
    }

    @Test
    @DisplayName("AC-3: case-insensitive match — SECRETVALUE caught in any case")
    void secretLiteralCaseInsensitive() {
        sanitiser.setSecretLiteral("SECRETVAL");
        assertThat(sanitiser.sanitise("value=secretval")).contains("[REDACTED:secret]");
        assertThat(sanitiser.sanitise("value=SecretVal")).contains("[REDACTED:secret]");
        assertThat(sanitiser.sanitise("value=SECRETVAL")).contains("[REDACTED:secret]");
    }

    @Test
    @DisplayName("AC-3: secret with regex special chars is treated literally")
    void secretLiteralWithRegexChars() {
        sanitiser.setSecretLiteral("a+b*c?d");
        assertThat(sanitiser.sanitise("input a+b*c?d here")).contains("[REDACTED:secret]");
    }

    // ----- AC-5 Bearer prefix redaction --------------------------------------

    @Test
    @DisplayName("AC-5: 'Bearer <token>' replaced with [REDACTED:bearer]")
    void bearerReplaced() {
        assertThat(sanitiser.sanitise("Authorization: Bearer abc.def.ghi"))
                .contains("[REDACTED:bearer]")
                .doesNotContain("abc.def.ghi");
    }

    @Test
    @DisplayName("AC-5: Bearer match is case-insensitive")
    void bearerCaseInsensitive() {
        assertThat(sanitiser.sanitise("h: bearer xyz")).contains("[REDACTED:bearer]");
        assertThat(sanitiser.sanitise("h: BEARER xyz")).contains("[REDACTED:bearer]");
    }

    @Test
    @DisplayName("AC-5: Bearer redaction preserves surrounding JSON structure (NFR-TM-005)")
    void bearerDoesNotEatJsonDelimiters() {
        // Compact JSON log line where 'Bearer <token>' sits at the end with no whitespace before
        // the closing delimiter. The old greedy \S+ consumed the trailing "} → invalid JSON.
        // The token-class match now stops at " so the value-closing quote + object brace survive.
        String line = "{\"level\":\"INFO\",\"message\":\"auth=Bearer abc.def.ghi\"}";
        String result = sanitiser.sanitise(line);
        assertThat(result).contains("[REDACTED:bearer]");
        assertThat(result).doesNotContain("abc.def.ghi");
        // JSON structure intact: line still closes with the value quote and the object brace.
        assertThat(result).endsWith("\"}");
    }

    // ----- AC-5 base64 blob redaction (boundary) -----------------------------

    @Test
    @DisplayName("AC-5 boundary: 31-char base64 NOT redacted")
    void base64_31chars_notRedacted() {
        String blob31 = "abcdefghijklmnopqrstuvwxyz12345"; // 31 chars
        // Make it stand alone so the lookbehind/lookahead accepts it.
        String input = "v=" + blob31 + ";";
        assertThat(sanitiser.sanitise(input)).doesNotContain("[REDACTED:base64]");
    }

    @Test
    @DisplayName("AC-5 boundary: 32-char base64 with standalone boundaries redacted")
    void base64_32chars_redacted() {
        String blob32 = "abcdefghijklmnopqrstuvwxyz123456"; // 32 chars
        String input = "blob: " + blob32 + " end";
        assertThat(sanitiser.sanitise(input)).contains("[REDACTED:base64]");
        assertThat(sanitiser.sanitise(input)).doesNotContain(blob32);
    }

    @Test
    @DisplayName("AC-5 standalone: 40-char base64 with non-base64 boundaries IS redacted")
    void base64_standalone_redacted() {
        // 40-char base64 surrounded by non-base64 chars ('_') — standalone boundary qualifies.
        String blob40 = "abcdefghijklmnopqrstuvwxyz1234567890ABCD"; // 40 chars
        String input = "prefix_" + blob40 + "_suffix";
        assertThat(sanitiser.sanitise(input)).contains("[REDACTED:base64]");
    }

    @Test
    @DisplayName("AC-5 negative: key=value context (v=<short blob>) NOT over-matched")
    void base64_keyValueContext_notOverMatched() {
        // Without the strict char-class restriction, 'v=' + 31-char blob would match the loose
        // base64 regex. Verifies the lookbehind/lookahead + class restriction prevents that.
        String blob31 = "abcdefghijklmnopqrstuvwxyz12345"; // 31 chars
        String input = "v=" + blob31 + ";";
        assertThat(sanitiser.sanitise(input)).doesNotContain("[REDACTED:base64]");
    }

    // ----- AC-4 dynamic registry ---------------------------------------------

    @Test
    @DisplayName("AC-4: registerDynamic(value, tag) — subsequent matches replaced with [REDACTED:tag]")
    void dynamicValueRegistered() {
        String token = "live-auth-token-value-xyz";
        sanitiser.registerDynamic(token, "authtoken");
        String input = "X-Auth: " + token;
        assertThat(sanitiser.sanitise(input))
                .contains("[REDACTED:authtoken]")
                .doesNotContain(token);
    }

    @Test
    @DisplayName("AC-4: unregisterDynamic removes the value from redaction")
    void dynamicValueUnregistered() {
        String token = "live-auth-token-value-xyz";
        sanitiser.registerDynamic(token, "authtoken");
        sanitiser.unregisterDynamic(token);
        assertThat(sanitiser.sanitise("X-Auth: " + token)).contains(token);
    }

    @Test
    @DisplayName("AC-4: re-registering the same value with a different tag updates the tag")
    void dynamicValueReRegisterUpdatesTag() {
        String token = "tok-value";
        sanitiser.registerDynamic(token, "old");
        sanitiser.registerDynamic(token, "new");
        assertThat(sanitiser.sanitise("v=" + token)).contains("[REDACTED:new]");
        assertThat(sanitiser.sanitise("v=" + token)).doesNotContain("[REDACTED:old]");
    }

    @Test
    @DisplayName("AC-4: registerDynamic with null/blank value is a no-op")
    void registerDynamicNullBlank_noOp() {
        sanitiser.registerDynamic(null, "x");
        sanitiser.registerDynamic("", "y");
        sanitiser.registerDynamic("   ", "z");
        // Nothing to verify directly beyond "no exception"; assert that redaction does not occur
        // for any of the blank/null registrations.
        assertThat(sanitiser.sanitise("nothing here")).isEqualTo("nothing here");
    }

    // ----- AC-8 edge cases (never throws) ------------------------------------

    @Nested
    @DisplayName("AC-8: edge cases — sanitiser never throws")
    class EdgeCases {

        @Test
        @DisplayName("null input returns null")
        void nullInput() {
            assertThat(sanitiser.sanitise(null)).isNull();
        }

        @Test
        @DisplayName("empty input returns empty")
        void emptyInput() {
            assertThat(sanitiser.sanitise("")).isEmpty();
        }

        @Test
        @DisplayName("multi-line input handled (JWT ≥32 chars on its own line)")
        void multiLineInput() {
            // Use a real JWT-length string so the length check (≥ 32) lets it redact.
            String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
            String multi = "line1\n" + jwt + "\nline3";
            String result = sanitiser.sanitise(multi);
            assertThat(result).contains("[REDACTED:jwt]");
            // Multi-line structure preserved.
            assertThat(result.split("\n")).hasSize(3);
        }

        @Test
        @DisplayName("Unicode content handled (Cyrillic, emoji)")
        void unicodeInput() {
            String input = "Russian: Привет мир. Emoji: 🚀. End.";
            // No secrets — should pass through with Unicode intact.
            String result = sanitiser.sanitise(input);
            assertThat(result).contains("Привет мир");
            assertThat(result).contains("🚀");
        }

        @Test
        @DisplayName("secret literal with Unicode is matched correctly")
        void unicodeSecretLiteral() {
            sanitiser.setSecretLiteral("секрет");
            String input = "Russian secret: СЕКРЕТ here";
            assertThat(sanitiser.sanitise(input)).contains("[REDACTED:secret]");
        }
    }
}

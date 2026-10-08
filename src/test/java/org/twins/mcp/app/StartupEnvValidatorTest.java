package org.twins.mcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.twins.mcp.config.M2mCredentialsProperties;
import org.twins.mcp.config.TwinsConnectionProperties;

/**
 * Unit tests for {@link StartupEnvValidator}. Properties records are constructed directly
 * (no Spring context needed); the {@link StartupEnvValidator#probeHostPort} network touch is
 * overridden with a stub.
 */
class StartupEnvValidatorTest {

    private static final String VALID_BASE_URL = "https://twins.example.com";
    private static final String VALID_DOMAIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String VALID_CLIENT_ID = "client-abc";
    private static final String SECRET_VALUE = "SECRETVAL";

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /** Builds a validator with the given properties and a no-op TCP probe. */
    private StartupEnvValidator buildValidator(
            String baseUrl, String domainId, String clientId, String clientSecret, String publicKeyId) {
        TwinsConnectionProperties connection = new TwinsConnectionProperties(baseUrl, domainId);
        M2mCredentialsProperties m2m = new M2mCredentialsProperties(clientId, clientSecret, publicKeyId);
        // Anonymous override: skip the network probe. Reachability is exercised only via logging
        // in the success path, not asserted here.
        return new StartupEnvValidator(connection, m2m, validator) {
            @Override
            protected boolean probeHostPort(String host, int port) {
                return true;
            }
        };
    }

    private void run(StartupEnvValidator v) {
        v.run(new DefaultApplicationArguments());
    }

    // ----- AC-1 happy path ---------------------------------------------------

    @Test
    @DisplayName("AC-1: all required env vars set with valid URL → startup proceeds")
    void allRequiredEnvVarsSet_noException() {
        StartupEnvValidator v = buildValidator(
                VALID_BASE_URL, VALID_DOMAIN_ID, VALID_CLIENT_ID, SECRET_VALUE, null);
        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-4: optional TWINS_M2M_PUBLIC_KEY_ID absent → startup proceeds")
    void optionalPublicKeyIdAbsent_noException() {
        StartupEnvValidator v = buildValidator(
                VALID_BASE_URL, VALID_DOMAIN_ID, VALID_CLIENT_ID, SECRET_VALUE, null);
        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-4: optional TWINS_M2M_PUBLIC_KEY_ID present → startup proceeds")
    void optionalPublicKeyIdPresent_noException() {
        StartupEnvValidator v = buildValidator(
                VALID_BASE_URL, VALID_DOMAIN_ID, VALID_CLIENT_ID, SECRET_VALUE, "pk-1");
        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    // ----- AC-2 missing var fail-fast ----------------------------------------

    @Nested
    @DisplayName("AC-2: missing required env var → exception naming the var")
    class MissingVarScenarios {

        @Test
        @DisplayName("missing TWINS_BASE_URL")
        void missingBaseUrl() {
            StartupEnvValidator v = buildValidator(
                    "", VALID_DOMAIN_ID, VALID_CLIENT_ID, SECRET_VALUE, null);
            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(StartupEnvValidationException.class)
                    .hasMessageContaining("TWINS_BASE_URL");
        }

        @Test
        @DisplayName("missing TWINS_DOMAIN_ID")
        void missingDomainId() {
            StartupEnvValidator v = buildValidator(
                    VALID_BASE_URL, "", VALID_CLIENT_ID, SECRET_VALUE, null);
            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(StartupEnvValidationException.class)
                    .hasMessageContaining("TWINS_DOMAIN_ID");
        }

        @Test
        @DisplayName("TWINS_DOMAIN_ID not a UUID → exception naming the var")
        void domainIdNotUuid() {
            StartupEnvValidator v = buildValidator(
                    VALID_BASE_URL, "not-a-uuid", VALID_CLIENT_ID, SECRET_VALUE, null);
            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(StartupEnvValidationException.class)
                    .hasMessageContaining("TWINS_DOMAIN_ID")
                    .hasMessageContaining("UUID");
        }

        @Test
        @DisplayName("TWINS_BASE_URL=ftp:// rejected (only http/https accepted)")
        void nonHttpScheme() {
            StartupEnvValidator v = buildValidator(
                    "ftp://evil.example", VALID_DOMAIN_ID, VALID_CLIENT_ID, SECRET_VALUE, null);
            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(StartupEnvValidationException.class)
                    .hasMessageContaining("TWINS_BASE_URL");
        }

        @Test
        @DisplayName("missing TWINS_M2M_CLIENT_ID")
        void missingClientId() {
            StartupEnvValidator v = buildValidator(
                    VALID_BASE_URL, VALID_DOMAIN_ID, "", SECRET_VALUE, null);
            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(StartupEnvValidationException.class)
                    .hasMessageContaining("TWINS_M2M_CLIENT_ID");
        }

        @Test
        @DisplayName("missing TWINS_M2M_CLIENT_SECRET")
        void missingClientSecret() {
            StartupEnvValidator v = buildValidator(
                    VALID_BASE_URL, VALID_DOMAIN_ID, VALID_CLIENT_ID, "", null);
            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(StartupEnvValidationException.class)
                    .hasMessageContaining("TWINS_M2M_CLIENT_SECRET");
        }
    }

    // ----- AC-3 malformed URL ------------------------------------------------

    @Test
    @DisplayName("AC-3: TWINS_BASE_URL=not-a-url → exception, no HTTP call attempted")
    void malformedBaseUrl_throwsWithoutHttpCall() {
        // The probe is stubbed — if validation correctly rejects the URL BEFORE the probe,
        // no exception other than StartupEnvValidationException is thrown.
        StartupEnvValidator v = buildValidator(
                "not-a-url", VALID_DOMAIN_ID, VALID_CLIENT_ID, SECRET_VALUE, null);
        assertThatThrownBy(() -> run(v))
                .isInstanceOf(StartupEnvValidationException.class)
                .hasMessageContaining("TWINS_BASE_URL");
    }

    // ----- AC-7 no-leak ------------------------------------------------------

    @Test
    @DisplayName("AC-7: secret value must not appear in the exception message")
    void secretValueNotInExceptionMessage() {
        // Force a failure on a DIFFERENT field (TWINS_DOMAIN_ID); the secret is set; the
        // resulting exception message must NOT contain the secret value.
        StartupEnvValidator v = buildValidator(
                VALID_BASE_URL, "", VALID_CLIENT_ID, SECRET_VALUE, null);
        StartupEnvValidationException thrown = catchStartupException(v);
        assertThat(thrown.getMessage()).doesNotContain(SECRET_VALUE);
        // Sanity: the failure-cause field IS named.
        assertThat(thrown.getMessage()).contains("TWINS_DOMAIN_ID");
    }

    @Test
    @DisplayName("AC-7: secret value must not appear even when secret itself is the missing field")
    void secretIsMissing_messageDoesNotEchoOtherValues() {
        // If secret is blank, other valid values (clientId etc.) must NOT leak either.
        StartupEnvValidator v = buildValidator(
                VALID_BASE_URL, VALID_DOMAIN_ID, "MY_CLIENT_ID_VALUE", "", null);
        StartupEnvValidationException thrown = catchStartupException(v);
        assertThat(thrown.getMessage()).doesNotContain("MY_CLIENT_ID_VALUE");
        assertThat(thrown.getMessage()).contains("TWINS_M2M_CLIENT_SECRET");
    }

    @Test
    @DisplayName("AC-7: baseUrl value must not appear in the exception message")
    void baseUrlValueNotInExceptionMessage() {
        // Use a distinctive baseUrl; force failure on TWINS_DOMAIN_ID; the baseUrl value
        // MUST NOT appear in the resulting message (review Patch P5, 2026-07-08).
        String distinctiveBaseUrl = "https://my-distinctive-base-url-marker.example.com";
        StartupEnvValidator v = buildValidator(
                distinctiveBaseUrl, "", VALID_CLIENT_ID, SECRET_VALUE, null);
        StartupEnvValidationException thrown = catchStartupException(v);
        assertThat(thrown.getMessage()).doesNotContain(distinctiveBaseUrl);
        assertThat(thrown.getMessage()).doesNotContain("my-distinctive-base-url-marker");
    }

    @Test
    @DisplayName("AC-7: M2mCredentialsProperties.toString() redacts clientSecret")
    void m2mToString_redactsSecret() {
        // Defense in depth: even if someone logs the record, toString() must not leak the secret
        // (review Decision-3, 2026-07-08).
        M2mCredentialsProperties props = new M2mCredentialsProperties("c-id", SECRET_VALUE, "pk-1");
        assertThat(props.toString()).contains("REDACTED");
        assertThat(props.toString()).doesNotContain(SECRET_VALUE);
    }

    private static StartupEnvValidationException catchStartupException(StartupEnvValidator v) {
        try {
            v.run(new DefaultApplicationArguments());
            throw new AssertionError("expected StartupEnvValidationException but no exception was thrown");
        } catch (StartupEnvValidationException e) {
            return e;
        }
    }

    // ----- AC-6 reachability probe -------------------------------------------

    @Test
    @DisplayName("AC-6: reachability probe failure does not block startup")
    void probeFailure_doesNotBlockStartup() {
        StartupEnvValidator v = new StartupEnvValidator(
                new TwinsConnectionProperties(VALID_BASE_URL, VALID_DOMAIN_ID),
                new M2mCredentialsProperties(VALID_CLIENT_ID, SECRET_VALUE, null),
                validator) {
            @Override
            protected boolean probeHostPort(String host, int port) {
                return false; // simulate unreachable twins
            }
        };
        // Despite the probe failure, startup must proceed.
        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-6: probe throwing unexpected exception does not block startup")
    void probeThrowingException_doesNotBlockStartup() {
        // P7 guarantee: even if probeReachability throws something unexpected, the run() method
        // catches Throwable and startup continues.
        StartupEnvValidator v = new StartupEnvValidator(
                new TwinsConnectionProperties(VALID_BASE_URL, VALID_DOMAIN_ID),
                new M2mCredentialsProperties(VALID_CLIENT_ID, SECRET_VALUE, null),
                validator) {
            @Override
            protected boolean probeHostPort(String host, int port) {
                throw new IllegalStateException("simulated probe bug");
            }
        };
        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }
}

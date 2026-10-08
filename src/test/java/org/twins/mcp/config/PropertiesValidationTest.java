package org.twins.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T5.5 — direct JSR-380 validation of the properties records. Confirms annotations on the
 * records work as expected independent of Spring binding.
 */
class PropertiesValidationTest {

    private static final String VALID_UUID = "00000000-0000-0000-0000-000000000001";

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> violatedFields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Nested
    @DisplayName("TwinsConnectionProperties")
    class TwinsConnection {

        @Test
        @DisplayName("happy path — no violations")
        void happyPath() {
            TwinsConnectionProperties props = new TwinsConnectionProperties(
                    "https://twins.example.com", VALID_UUID);
            assertThat(validator.validate(props)).isEmpty();
        }

        @Test
        @DisplayName("blank baseUrl → violation on baseUrl")
        void blankBaseUrl() {
            TwinsConnectionProperties props = new TwinsConnectionProperties("", VALID_UUID);
            assertThat(violatedFields(validator.validate(props))).containsExactly("baseUrl");
        }

        @Test
        @DisplayName("blank domainId → violation on domainId")
        void blankDomainId() {
            TwinsConnectionProperties props = new TwinsConnectionProperties(
                    "https://twins.example.com", "");
            assertThat(violatedFields(validator.validate(props))).contains("domainId");
        }

        @Test
        @DisplayName("AC-3: malformed URL → violation on baseUrl")
        void malformedUrl() {
            TwinsConnectionProperties props = new TwinsConnectionProperties("not-a-url", VALID_UUID);
            assertThat(violatedFields(validator.validate(props))).contains("baseUrl");
        }

        @Test
        @DisplayName("AC-1: ftp:// scheme rejected (http/https only)")
        void ftpSchemeRejected() {
            TwinsConnectionProperties props = new TwinsConnectionProperties("ftp://x", VALID_UUID);
            assertThat(violatedFields(validator.validate(props))).contains("baseUrl");
        }

        @Test
        @DisplayName("Decision-1: non-UUID domainId → violation on domainId")
        void nonUuidDomainIdRejected() {
            TwinsConnectionProperties props = new TwinsConnectionProperties(
                    "https://twins.example.com", "domain-1");
            assertThat(violatedFields(validator.validate(props))).contains("domainId");
        }

        @Test
        @DisplayName("Decision-1: UUID with CRLF → violation on domainId (CRLF injection blocked)")
        void crlfInDomainIdRejected() {
            // Even if the prefix looks UUID-like, embedded CRLF must be rejected.
            TwinsConnectionProperties props = new TwinsConnectionProperties(
                    "https://twins.example.com", "00000000-0000-0000-0000-000000000001\r\nX-Inject: 1");
            assertThat(violatedFields(validator.validate(props))).contains("domainId");
        }
    }

    @Nested
    @DisplayName("M2mCredentialsProperties")
    class M2mCredentials {

        @Test
        @DisplayName("happy path with optional publicKeyId set — no violations")
        void happyPathWithOptional() {
            M2mCredentialsProperties props = new M2mCredentialsProperties("c-id", "secret", "pk-1");
            assertThat(validator.validate(props)).isEmpty();
        }

        @Test
        @DisplayName("AC-4: optional publicKeyId null — no violations")
        void optionalPublicKeyIdNull() {
            M2mCredentialsProperties props = new M2mCredentialsProperties("c-id", "secret", null);
            assertThat(validator.validate(props)).isEmpty();
        }

        @Test
        @DisplayName("blank clientId → violation on clientId")
        void blankClientId() {
            M2mCredentialsProperties props = new M2mCredentialsProperties("", "secret", null);
            assertThat(violatedFields(validator.validate(props))).containsExactly("clientId");
        }

        @Test
        @DisplayName("blank clientSecret → violation on clientSecret")
        void blankClientSecret() {
            M2mCredentialsProperties props = new M2mCredentialsProperties("c-id", "", null);
            assertThat(violatedFields(validator.validate(props))).containsExactly("clientSecret");
        }

        @Test
        @DisplayName("Decision-3: toString() redacts clientSecret")
        void toStringRedactsSecret() {
            M2mCredentialsProperties props = new M2mCredentialsProperties("c-id", "topsecret", "pk-1");
            String repr = props.toString();
            assertThat(repr).contains("REDACTED");
            assertThat(repr).doesNotContain("topsecret");
        }
    }
}

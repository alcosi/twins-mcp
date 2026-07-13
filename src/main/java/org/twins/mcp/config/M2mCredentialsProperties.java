package org.twins.mcp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * M2M credentials — bound from {@code twins.m2m.*} in {@code application.yml}.
 *
 * <p>Per ARCH-5: {@code TWINS_M2M_CLIENT_ID} and {@code TWINS_M2M_CLIENT_SECRET} are required;
 * {@code TWINS_M2M_PUBLIC_KEY_ID} is optional (nullable) per AC-4. Downstream M2M call
 * (Story 1.4) simply omits the public key id when it is null.
 *
 * <p>Secret-discipline note (NFR-TM-003): {@link #clientSecret} is captured here. The
 * auto-generated {@code toString()} is overridden below to redact the secret so that any
 * accidental {@code log.debug("props: {}", m2m)} cannot leak it (review Decision-3, 2026-07-08).
 *
 * @param clientId     TWINS_M2M_CLIENT_ID — AuthM2MLoginRqDTOv1.clientId
 * @param clientSecret TWINS_M2M_CLIENT_SECRET — AuthM2MLoginRqDTOv1.clientSecret
 * @param publicKeyId  TWINS_M2M_PUBLIC_KEY_ID — optional, AuthM2MLoginRqDTOv1.publicKeyId
 */
@ConfigurationProperties("twins.m2m")
@Validated
public record M2mCredentialsProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        String publicKeyId) {

    /** Redacts {@link #clientSecret}; safe for log/debug output. */
    @Override
    public String toString() {
        return "M2mCredentialsProperties[clientId=" + clientId
                + ", clientSecret=REDACTED, publicKeyId=" + publicKeyId + "]";
    }
}

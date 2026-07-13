package org.twins.mcp.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Twins connection properties — bound from {@code twins.connection.*} in {@code application.yml}.
 *
 * <p>Both fields are required (AC-1, AC-5). Missing env vars produce an empty string via the
 * YAML placeholder default, which JSR-380 catches as a {@code @NotBlank} violation. The
 * {@code @URL} constraint on {@link #baseUrl} enforces AC-3 (malformed URL fail-fast) before
 * any HTTP request is attempted; {@code @Pattern} restricts the scheme to http/https per AC-1
 * (Hibernate Validator's {@code @URL} alone accepts ftp/file/jar — review Patch P2).
 *
 * <p>{@link #domainId} is restricted to UUID format (review Decision-1, 2026-07-08). Twins
 * domain IDs are UUIDs; restricting at the property layer prevents CRLF injection when the
 * value flows into the {@code DomainId} HTTP header (Story 1.4 interceptor).
 *
 * @param baseUrl  TWINS_BASE_URL — root URL for RestClient (http/https only)
 * @param domainId TWINS_DOMAIN_ID — UUID, emitted as the {@code DomainId} header (ARCH-7)
 */
@ConfigurationProperties("twins.connection")
@Validated
public record TwinsConnectionProperties(
        @NotBlank @URL
        @Pattern(regexp = "^https?://.+", message = "must start with http:// or https://")
        String baseUrl,
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a UUID (e.g. 00000000-0000-0000-0000-000000000001)")
        String domainId) {
}

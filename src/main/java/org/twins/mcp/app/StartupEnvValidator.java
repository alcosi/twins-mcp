package org.twins.mcp.app;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.twins.mcp.config.M2mCredentialsProperties;
import org.twins.mcp.config.TwinsConnectionProperties;

/**
 * Fail-fast startup validator for twins connection env vars (ARCH-5, AC-1..AC-7).
 *
 * <p>Runs after Spring binds {@link TwinsConnectionProperties} and {@link M2mCredentialsProperties}
 * (so YAML placeholders like {@code ${TWINS_BASE_URL:}} have already been resolved). Re-validates
 * both beans via JSR-380, composing a single ordered error message naming every missing or
 * malformed field by env var name — never by value (NFR-TM-003 / AC-7).
 *
 * <p>Also performs one best-effort TCP reachability probe against {@code TWINS_BASE_URL}
 * host:port (AC-6). Failure is a WARN — operators may legitimately start twins-mcp before
 * twins during dev. The result is logged on stderr as {@code twins.connection.reachable=true|false}.
 *
 * <p>If validation fails, throws {@link StartupEnvValidationException}; Spring Boot then aborts
 * startup with a non-zero exit code (AC-2). No HTTP request is attempted against twins until the
 * validator passes (AC-3).
 */
@Component
public class StartupEnvValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupEnvValidator.class);

    /** Probe timeout per AC-6 — "≤ 2 s". */
    static final int PROBE_TIMEOUT_MILLIS = 2000;

    private final TwinsConnectionProperties connection;
    private final M2mCredentialsProperties m2m;
    private final Validator validator;

    public StartupEnvValidator(
            TwinsConnectionProperties connection,
            M2mCredentialsProperties m2m,
            Validator validator) {
        this.connection = connection;
        this.m2m = m2m;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = collectValidationProblems();
        if (!problems.isEmpty()) {
            // Composed message references env var NAMES only. Never interpolates values.
            throw new StartupEnvValidationException(
                    "twins-mcp startup aborted — missing or malformed env var(s): "
                            + String.join(", ", problems)
                            + ". Required env vars: TWINS_BASE_URL (http/https URL), "
                            + "TWINS_DOMAIN_ID, TWINS_M2M_CLIENT_ID, TWINS_M2M_CLIENT_SECRET. "
                            + "Optional: TWINS_M2M_PUBLIC_KEY_ID.");
        }

        // AC-6: best-effort probe — wrap in Throwable so ANY unexpected failure stays a WARN,
        // never a startup blocker. (review Patch P7, 2026-07-08)
        boolean reachable;
        try {
            reachable = probeReachability(connection.baseUrl());
        } catch (Throwable t) {
            log.warn(
                    "twins.connection.reachable=false — probe threw {} ({}). Startup continues.",
                    t.getClass().getSimpleName(),
                    t.getMessage());
            reachable = false;
        }
        // MDC-style key=value suffix so log scans can grep twins.connection.reachable=...
        log.info("twins-mcp env validation passed. twins.connection.reachable={}", reachable);
    }

    /**
     * Returns the list of human-readable problem descriptors — one per JSR-380 violation. Each
     * descriptor names the env var; none interpolates a value. Empty list ⇒ validation passed.
     *
     * <p>Sorted by env-var name for deterministic message order across runs (review Patch P4).
     */
    private List<String> collectValidationProblems() {
        List<String> problems = new ArrayList<>();

        validator.validate(connection).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .forEach(v -> problems.add(envVarNameForConnection(v) + " (" + v.getMessage() + ")"));
        validator.validate(m2m).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .forEach(v -> problems.add(envVarNameForM2m(v) + " (" + v.getMessage() + ")"));
        return problems;
    }

    /** Maps a TwinsConnectionProperties field path to its env var name. */
    private static String envVarNameForConnection(ConstraintViolation<?> v) {
        return switch (v.getPropertyPath().toString()) {
            case "baseUrl" -> "TWINS_BASE_URL";
            case "domainId" -> "TWINS_DOMAIN_ID";
            default -> "twins.connection." + v.getPropertyPath();
        };
    }

    /** Maps an M2mCredentialsProperties field path to its env var name. */
    private static String envVarNameForM2m(ConstraintViolation<?> v) {
        return switch (v.getPropertyPath().toString()) {
            case "clientId" -> "TWINS_M2M_CLIENT_ID";
            case "clientSecret" -> "TWINS_M2M_CLIENT_SECRET";
            case "publicKeyId" -> "TWINS_M2M_PUBLIC_KEY_ID";
            default -> "twins.m2m." + v.getPropertyPath();
        };
    }

    /**
     * Best-effort TCP reachability probe against the host:port of {@code baseUrl} (AC-6).
     * Failure is logged as WARN, not propagated. {@code protected} so tests can override
     * with a no-op stub and avoid real network I/O.
     */
    protected boolean probeReachability(String baseUrl) {
        try {
            URI uri = new URI(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isBlank()) {
                log.warn("twins.connection.reachable=false — TWINS_BASE_URL has no host component");
                return false;
            }
            // IPv6 literals come back bracketed from URI.getHost(); strip the brackets so
            // InetSocketAddress accepts the raw form (review Patch P8, 2026-07-08).
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            if (port < 0) {
                // Default by scheme; https → 443, anything else → 80.
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return probeHostPort(host, port);
        } catch (URISyntaxException e) {
            // Should be unreachable: @URL already rejected malformed values before this method
            // is called. Still — never propagate; reachability is best-effort.
            log.warn("twins.connection.reachable=false — TWINS_BASE_URL parse error (message only, no value)");
            return false;
        }
    }

    /**
     * Single connect() with the configured timeout. Extracted so tests can override just the
     * network touch, keeping {@link #probeReachability(String)} logic intact.
     *
     * <p>Catches every failure mode that could surface from a hostile URI or environment:
     * {@link IOException} (network down / timeout), {@link IllegalArgumentException} (port out
     * of range, invalid host), {@link SecurityException} (SecurityManager denies connect),
     * {@link InterruptedException} wrapped as InterruptedIOException. AC-6's "best-effort WARN"
     * contract must hold for all probe failures (review Patch P6, 2026-07-08).
     */
    protected boolean probeHostPort(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            log.warn(
                    "twins.connection.reachable=false — TCP probe to {}:{} failed ({}). "
                            + "Startup continues; twins may not be up yet.",
                    host,
                    port,
                    e.getClass().getSimpleName());
            return false;
        }
    }
}

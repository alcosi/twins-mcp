package org.twins.mcp.config;

import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.twins.mcp.secrets.SecretsSanitiser;

/**
 * Spring-side hook that registers the static secret literal with {@link SecretsSanitiser} once
 * the application context is up (AC-3).
 *
 * <p>The {@link M2mCredentialsProperties#clientSecret()} value is captured here at
 * {@link ApplicationStartedEvent} (after Spring binds properties and after
 * {@link org.twins.mcp.app.StartupEnvValidator} confirms they are non-blank). Subsequent log
 * lines have the literal redacted case-insensitively.
 *
 * <p>Story 1.4 will register the live AuthToken value via
 * {@code SecretsSanitiser.getInstance().registerDynamic(token, "authtoken")} from
 * {@code TokenHolder} — no Spring bean needed there.
 */
@Component
public class LoggingConfig {

    private final M2mCredentialsProperties m2m;

    public LoggingConfig(M2mCredentialsProperties m2m) {
        this.m2m = m2m;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerStaticSecretLiteral() {
        String secret = m2m.clientSecret();
        if (secret != null && !secret.isBlank()) {
            SecretsSanitiser.getInstance().setSecretLiteral(secret);
        }
    }
}

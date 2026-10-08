package org.twins.mcp.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.logstash.logback.layout.LogstashLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Integration test for the full log pipeline: SLF4J → Logback Logger → ListAppender with
 * SanitisingLayout(LogstashLayout) → JSON output → redaction tags present (AC-1, AC-6).
 *
 * <p>This proves the Logback XML wiring approach works end-to-end without booting Spring.
 */
class SanitisingLayoutIntegrationTest {

    private LoggerContext loggerContext;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private SanitisingLayout sanitisingLayout;
    private SecretsSanitiser sanitiser;

    @BeforeEach
    void setUp() {
        sanitiser = SecretsSanitiser.getInstance();
        sanitiser.clearForTest();

        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = loggerContext.getLogger("org.twins.mcp.integration.TestLogger");

        // Build LogstashLayout (renders JSON).
        LogstashLayout jsonLayout = new LogstashLayout();
        jsonLayout.setContext(loggerContext);
        jsonLayout.start();

        // Wrap it in SanitisingLayout.
        sanitisingLayout = new SanitisingLayout();
        sanitisingLayout.setContext(loggerContext);
        sanitisingLayout.setDelegate(jsonLayout);
        sanitisingLayout.start();

        // Appender captures events; we render them via the layout manually.
        appender = new ListAppender<>();
        appender.setContext(loggerContext);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        sanitisingLayout.stop();
        logger.detachAppender(appender);
        sanitiser.clearForTest();
    }

    private String lastRendered() {
        assertThat(appender.list).isNotEmpty();
        return sanitisingLayout.doLayout(appender.list.get(appender.list.size() - 1));
    }

    @Test
    @DisplayName("Integration: log line containing a JWT — JSON output contains [REDACTED:jwt]")
    void logLineWithJwt_redacted() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        logger.info("Token received: {}", jwt);

        String rendered = lastRendered();
        assertThat(rendered).contains("[REDACTED:jwt]");
        assertThat(rendered).doesNotContain(jwt);
        // JSON output should still be valid (contain message key).
        assertThat(rendered).contains("\"message\"");
    }

    @Test
    @DisplayName("Integration: registered secret literal — JSON output contains [REDACTED:secret]")
    void logLineWithSecretLiteral_redacted() {
        sanitiser.setSecretLiteral("topsecret123");
        logger.info("Auth with topsecret123");

        String rendered = lastRendered();
        assertThat(rendered).contains("[REDACTED:secret]");
        assertThat(rendered).doesNotContain("topsecret123");
    }

    @Test
    @DisplayName("Integration: dynamic AuthToken — JSON output contains [REDACTED:authtoken]")
    void logLineWithDynamicAuthToken_redacted() {
        sanitiser.registerDynamic("live-auth-token-xyz", "authtoken");
        logger.info("X-Auth: live-auth-token-xyz");

        String rendered = lastRendered();
        assertThat(rendered).contains("[REDACTED:authtoken]");
        assertThat(rendered).doesNotContain("live-auth-token-xyz");
    }

    @Test
    @DisplayName("Integration: clean log line passes through unchanged (no false positives)")
    void cleanLogLine_passesThrough() {
        logger.info("Started Application v0.1.0");
        String rendered = lastRendered();
        assertThat(rendered).contains("Started Application v0.1.0");
        assertThat(rendered).doesNotContain("[REDACTED:");
    }

    @Test
    @DisplayName("Integration: event without args renders via LogstashLayout correctly")
    void directEventRender_laysOut() {
        // Construct an event directly to bypass any logger caching.
        LoggingEvent event = new LoggingEvent(
                "fqcn", logger, Level.INFO, "Plain message", null, null);
        String rendered = sanitisingLayout.doLayout(event);
        assertThat(rendered).contains("\"message\"");
        assertThat(rendered).contains("Plain message");
    }
}

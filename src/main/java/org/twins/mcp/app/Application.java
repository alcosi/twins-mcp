package org.twins.mcp.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.twins.mcp.config.M2mCredentialsProperties;
import org.twins.mcp.config.TwinsConnectionProperties;

/**
 * Application entry point.
 *
 * <p>twins-mcp is a backend-only MCP server that exposes twins REST data to LLM agents
 * via the Model Context Protocol over stdio. No servlet container starts; see
 * {@code application.yml} ({@code spring.main.web-application-type: none}).
 *
 * <p>{@link EnableConfigurationProperties} binds the twins connection / M2M credential records
 * (Story 1.2). {@link StartupEnvValidator} runs after binding to fail-fast on missing or
 * malformed env vars (ARCH-5, AC-2).
 */
@SpringBootApplication
@EnableConfigurationProperties({TwinsConnectionProperties.class, M2mCredentialsProperties.class})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

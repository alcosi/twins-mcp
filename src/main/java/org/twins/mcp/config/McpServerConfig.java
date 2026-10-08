package org.twins.mcp.config;

import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.twins.mcp.error.ToolInputInvalid;
import org.twins.mcp.error.TwinsPermissionDenied;
import org.twins.mcp.error.TwinsUnavailable;
import org.twins.mcp.response.ToolResponse;
import org.twins.mcp.secrets.SecretsSanitiser;
import org.twins.mcp.tool.ToolArgsValidator;
import org.twins.mcp.tool.ToolJsonSchemaGenerator;
import org.twins.mcp.tool.ToolRegistry;
import org.twins.mcp.tool.TwinsMcpTool;

/**
 * Bridges every allowlisted {@link TwinsMcpTool} into the Spring AI MCP server (Story 1.5 AC-8;
 * ARCH-SETUP-10). The {@code spring-ai-starter-mcp-server} auto-configures the {@code McpSyncServer}
 * bean + stdio transport; this config supplies a {@link McpSyncServerCustomizer} that adds one
 * {@link SyncToolSpecification} per registered tool.
 *
 * <p>The bridge is the single path from tool beans to the MCP surface, so the {@link ToolRegistry} /
 * {@link org.twins.mcp.tool.ToolAllowlist} gate stays authoritative — no stray annotation can bypass
 * it. Each tool's call handler: deserialises the MCP arguments map into the tool's {@code Args}
 * record, runs JSR-380 via {@link ToolArgsValidator}, invokes {@link TwinsMcpTool#call(Object)}, and
 * converts the {@link ToolResponse} into a {@link CallToolResult} (markdown {@code TextContent} +
 * structuredContent). Domain exceptions become {@code isError=true} results with a sanitised message
 * — Story 1.6's error-envelope mapper will refine this into typed envelope codes.
 */
@Configuration
public class McpServerConfig {

    @Bean
    McpSyncServerCustomizer twinsToolRegistrationCustomizer(
            ToolRegistry registry,
            ToolArgsValidator validator,
            ToolJsonSchemaGenerator schemaGenerator,
            ObjectMapper objectMapper) {
        return spec -> spec.tools(registry.all().stream()
                .map(tool -> toSpecification(tool, validator, schemaGenerator, objectMapper))
                .toList());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SyncToolSpecification toSpecification(
            TwinsMcpTool<?> tool,
            ToolArgsValidator validator,
            ToolJsonSchemaGenerator schemaGenerator,
            ObjectMapper mapper) {

        McpSchema.Tool mcpTool = McpSchema.Tool.builder(
                        tool.key().wireName(),
                        schemaGenerator.schemaFor(tool.argsType()))
                .description(tool.description())
                .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler = (exchange, request) -> {
            String wireName = tool.key().wireName();
            try {
                Object args = mapper.convertValue(request.arguments(), tool.argsType());
                validator.validate(args);
                TwinsMcpTool raw = (TwinsMcpTool) tool;
                ToolResponse response = raw.call(args);
                return success(response);
            } catch (ToolInputInvalid | TwinsPermissionDenied | TwinsUnavailable e) {
                return error(wireName, e);
            } catch (RuntimeException e) {
                return error(wireName, e);
            }
        };
        return new SyncToolSpecification(mcpTool, handler);
    }

    private static CallToolResult success(ToolResponse response) {
        return new CallToolResult(
                List.of(text(response.markdown())),
                Boolean.FALSE,
                response.structuredContent(),
                null);
    }

    private static CallToolResult error(String wireName, Throwable e) {
        String message = wireName + ": " + e.getClass().getSimpleName()
                + ((e.getMessage() != null) ? " — " + e.getMessage() : "");
        String sanitised = SecretsSanitiser.getInstance().sanitise(message);
        return new CallToolResult(
                List.of(text(sanitised)),
                Boolean.TRUE,
                null,
                null);
    }

    private static McpSchema.TextContent text(String content) {
        return McpSchema.TextContent.builder(content).build();
    }
}

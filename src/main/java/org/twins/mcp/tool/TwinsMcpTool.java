package org.twins.mcp.tool;

import org.twins.mcp.response.ToolResponse;

/**
 * Domain-oriented tool surface (ARCH-11; decision D10 — a tool ≠ a REST endpoint). A tool freely
 * composes N twins REST calls via {@link org.twins.mcp.client.TwinsRestClient} and returns a single
 * {@link ToolResponse}; it never exposes endpoint-bound methods.
 *
 * <p>Every concrete tool is a Spring {@code @Component} parameterised on its {@code Args} record.
 * The {@link ToolRegistry} discovers all beans; {@link ToolAllowlist} enforces the read-only surface
 * at startup; {@link ToolArgsValidator} runs JSR-380 on {@code Args} before {@link #call(Object)}.
 * {@link org.twins.mcp.config.McpServerConfig} bridges each bean into an MCP tool specification.
 *
 * @param <A> the tool's argument record type (JSR-380-annotated)
 */
public interface TwinsMcpTool<A> {

    /** Stable identity of this tool — must be a {@link ToolKey} constant (the allowlist gate). */
    ToolKey key();

    /** Human-readable description surfaced to the MCP client (LLM) as the tool's purpose. */
    String description();

    /** The {@code Args} record class, used to build the input JSON schema and deserialise calls. */
    Class<A> argsType();

    /**
     * Executes the tool against twins. Invoked only after {@link ToolArgsValidator} has accepted the
     * arguments. Must never receive raw client input unfiltered — JSR-380 has already run.
     *
     * <p>Throws domain exceptions ({@link org.twins.mcp.error.TwinsPermissionDenied},
     * {@link org.twins.mcp.error.TwinsUnavailable}, {@link org.twins.mcp.error.ToolInputInvalid});
     * Story 1.6's mapper converts them to error envelopes.
     */
    ToolResponse call(A args);
}

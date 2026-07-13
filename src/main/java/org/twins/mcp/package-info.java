/**
 * Root package for the twins-mcp server.
 *
 * <p>Sub-packages follow the architecture's layering:
 * <ul>
 *   <li>{@code app} — bootstrap, lifecycle, startup validation</li>
 *   <li>{@code config} — Spring {@code @Configuration} and {@code @ConfigurationProperties}</li>
 *   <li>{@code client} — RestClient, M2M auth, headers interceptor, endpoint wrappers</li>
 *   <li>{@code tool} — MCP tool beans, registry, allowlist</li>
 *   <li>{@code response} — hybrid response builder, cursor, size cap</li>
 *   <li>{@code error} — error envelope, domain exceptions</li>
 *   <li>{@code secrets} — Logback sanitiser filter</li>
 *   <li>{@code metrics} — Micrometer counters and shutdown emitter</li>
 * </ul>
 *
 * <p>Populated by later stories. Story 1.1 only delivers {@link org.twins.mcp.app.Application}.
 */
package org.twins.mcp;

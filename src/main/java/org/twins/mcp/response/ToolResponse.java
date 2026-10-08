package org.twins.mcp.response;

import java.util.Map;

/**
 * Hybrid tool response (ARCH-14; FR-TM-040): a markdown summary for the LLM + a structured-content
 * JSON map matching twins DTO field names, plus an optional opaque pagination cursor.
 *
 * <p>Build via {@link ToolResponseBuilder} — never hand-construct, so the size cap and pagination
 * contract are enforced uniformly (ARCH-16).
 *
 * @param markdown          5–30 line CommonMark summary (≤ 30 % of {@link #structuredContent} size)
 * @param structuredContent snake_case top-level keys, twins DTO field names verbatim (never null)
 * @param nextCursor        opaque cursor for the next page, or {@code null} when there is no next page
 */
public record ToolResponse(String markdown, Map<String, Object> structuredContent, String nextCursor) {

    public ToolResponse {
        structuredContent = (structuredContent == null) ? Map.of() : Map.copyOf(structuredContent);
    }
}

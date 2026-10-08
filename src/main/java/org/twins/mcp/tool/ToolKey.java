package org.twins.mcp.tool;

/**
 * Allowlist of every tool twins-mcp may expose (ARCH-9 — read-only surface gate). A tool whose key
 * is not present here CANNOT ship: the {@link ToolAllowlist} fails the context at startup.
 *
 * <p>The enum is the single source of truth for the tool surface. Adding a tool means appending a
 * constant here AND implementing a {@link TwinsMcpTool} whose {@link TwinsMcpTool#key()} returns it.
 *
 * <p>{@link #wireName()} is the MCP tool name exposed to clients ({@code lower_snake_case}).
 *
 * <p>Story 1.5 ships only the placeholder {@link #LIST_CLASSES} (implemented in Story 1.7). Future
 * tools, listed for visibility, are appended by their owning epics — do NOT add them ahead of
 * implementation (the allowlist must mirror what is actually built).
 */
public enum ToolKey {

    LIST_CLASSES("list_classes");

    // Reserved for Epic 2 (catalog drill-down):
    //   DESCRIBE_CLASS("describe_class"),
    //   DESCRIBE_CLASS_RELATIONS("describe_class_relations"),
    //   DESCRIBE_CLASS_STATUSES("describe_class_statuses"),
    // Reserved for Epic 3 (terminology):
    //   LIST_GLOSSARY_SECTIONS("list_glossary_sections"),
    //   GET_GLOSSARY_SECTION("get_glossary_section"),
    //   GET_GLOSSARY_TERM("get_glossary_term"),

    private final String wireName;

    ToolKey(String wireName) {
        this.wireName = wireName;
    }

    /** MCP tool name ({@code lower_snake_case}). */
    public String wireName() {
        return wireName;
    }
}

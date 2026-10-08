package org.twins.mcp.response;

import java.nio.charset.StandardCharsets;

/**
 * Safety net enforcing the 32 KiB response cap (ARCH-16; Story 1.5 AC-5). The tool is responsible
 * for paginating so a response fits; this enforcer fails the build loudly if it does not — the
 * builder NEVER silently truncates data.
 */
public final class SizeCapEnforcer {

    /** Maximum total size of a tool response (markdown + structuredContent JSON), in UTF-8 bytes. */
    public static final int CAP_BYTES = 32 * 1024;

    private SizeCapEnforcer() {
    }

    /**
     * @throws IllegalStateException if the combined UTF-8 size of {@code markdown} and
     *         {@code structuredContentJson} exceeds {@link #CAP_BYTES}.
     */
    public static void enforce(String markdown, String structuredContentJson) {
        long size = utf8Bytes(markdown) + utf8Bytes(structuredContentJson);
        if (size > CAP_BYTES) {
            throw new IllegalStateException(
                    "Tool response exceeds the " + (CAP_BYTES / 1024) + " KiB cap: "
                            + size + " bytes (markdown=" + utf8Bytes(markdown)
                            + ", structuredContent=" + utf8Bytes(structuredContentJson)
                            + "). Paginate before building the response (ARCH-16).");
        }
    }

    static long measure(String markdown, String structuredContentJson) {
        return utf8Bytes(markdown) + utf8Bytes(structuredContentJson);
    }

    private static long utf8Bytes(String s) {
        return (s == null) ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }
}

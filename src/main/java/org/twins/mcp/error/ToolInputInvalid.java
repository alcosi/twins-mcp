package org.twins.mcp.error;

import java.util.List;

/**
 * Raised when a tool's {@code Args} fail JSR-380 validation, or when a pagination cursor is replayed
 * against a changed filter (Story 1.5 AC-3 / AC-6 / AC-7). Carries the per-field violation list so
 * Story 1.6's {@code ErrorEnvelopeMapper} can surface them as envelope code {@code TOOL_INPUT_INVALID}.
 *
 * <p>Violation messages come from JSR-380 constraint metadata (field path + message); they never
 * carry token/secret values (NFR-TM-003).
 */
public class ToolInputInvalid extends RuntimeException {

    private final List<String> violations;

    public ToolInputInvalid(String message, List<String> violations) {
        super(message);
        this.violations = (violations == null) ? List.of() : List.copyOf(violations);
    }

    /** Per-field violation descriptions (e.g. {@code "pageSize must be less than or equal to 100"}). */
    public List<String> violations() {
        return violations;
    }
}

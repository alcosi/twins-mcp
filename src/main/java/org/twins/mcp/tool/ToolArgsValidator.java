package org.twins.mcp.tool;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.twins.mcp.error.ToolInputInvalid;

/**
 * Runs JSR-380 validation on a tool's {@code Args} record before {@link TwinsMcpTool#call(Object)}
 * is invoked (Story 1.5 AC-3). Constraint violations raise {@link ToolInputInvalid} — never reach
 * {@code call()}, never become a generic 500.
 *
 * <p>Page-size constraints (AC-7: max 100, min 1, default 20) are expressed as JSR-380 annotations on
 * each tool's {@code Args} record (e.g. {@code @Min(1) @Max(100) Integer pageSize}); this validator
 * is the single enforcement point.
 */
@Component
public class ToolArgsValidator {

    private final Validator validator;

    public ToolArgsValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Validates {@code args}; throws {@link ToolInputInvalid} with the sorted per-field violation
     * list if any constraint fails. A {@code null} args object (a tool that takes no input) is
     * treated as valid.
     */
    public <A> void validate(A args) {
        if (args == null) {
            return;
        }
        Set<ConstraintViolation<A>> violations = validator.validate(args);
        if (violations.isEmpty()) {
            return;
        }
        List<String> messages = violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .toList();
        throw new ToolInputInvalid("Tool input invalid: " + String.join("; ", messages), messages);
    }
}

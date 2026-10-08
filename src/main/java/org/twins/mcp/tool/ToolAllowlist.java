package org.twins.mcp.tool;

import jakarta.annotation.PostConstruct;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Read-only-surface gate (ARCH-9; Story 1.5 AC-2). At startup verifies every {@link TwinsMcpTool}
 * bean carries a non-null {@link ToolKey} that is in the active allowed set, and fails the Spring
 * context otherwise — naming the offending bean class and its key.
 *
 * <p>The allowed set defaults to the entire {@link ToolKey} enum (every declared tool is active).
 * The package-private constructor accepts an explicit set so the gate is testable: a tool returning
 * a key outside the set, or {@code null}, aborts startup.
 */
@Component
public class ToolAllowlist {

    private static final Logger log = LoggerFactory.getLogger(ToolAllowlist.class);

    private final List<TwinsMcpTool<?>> tools;
    private final Set<ToolKey> allowedKeys;

    public ToolAllowlist(List<TwinsMcpTool<?>> tools) {
        this(tools, EnumSet.allOf(ToolKey.class));
    }

    /** Testable constructor — restricts the active surface to {@code allowedKeys}. */
    ToolAllowlist(List<TwinsMcpTool<?>> tools, Set<ToolKey> allowedKeys) {
        this.tools = tools;
        this.allowedKeys = Set.copyOf(allowedKeys);
    }

    @PostConstruct
    void verify() {
        for (TwinsMcpTool<?> tool : tools) {
            ToolKey key = tool.key();
            if (key == null) {
                throw new IllegalStateException("Tool " + tool.getClass().getName()
                        + " returned a null key(); every TwinsMcpTool must return a ToolKey constant"
                        + " (ARCH-9 read-only surface gate)");
            }
            if (!allowedKeys.contains(key)) {
                throw new IllegalStateException("Tool " + tool.getClass().getName()
                        + " returned key " + key.name() + " which is not in the active allowlist "
                        + allowedKeys + " (ARCH-9 read-only surface gate)");
            }
        }
        log.info("Tool allowlist verified: {} tool(s) on the active surface", tools.size());
    }
}

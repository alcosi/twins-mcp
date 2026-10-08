package org.twins.mcp.tool;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link ToolKey} → {@link TwinsMcpTool} map from every {@link TwinsMcpTool} bean at
 * startup (Story 1.5 AC-1). Exposes lookup for the MCP dispatcher and for {@link ToolAllowlist}.
 *
 * <p>Fails fast on duplicate keys (two beans claiming the same {@link ToolKey}) — a configuration
 * error, not a recoverable condition.
 */
@Component
public class ToolRegistry {

    private final Map<ToolKey, TwinsMcpTool<?>> byKey;
    private final Map<String, ToolKey> wireNameToKey;

    public ToolRegistry(List<TwinsMcpTool<?>> tools) {
        Map<ToolKey, TwinsMcpTool<?>> map = new EnumMap<>(ToolKey.class);
        for (TwinsMcpTool<?> tool : tools) {
            ToolKey key = tool.key();
            if (key == null) {
                // EnumMap rejects null keys with an opaque NPE — fail with a clear message instead.
                throw new IllegalStateException(
                        "Tool " + tool.getClass().getName() + " returned a null key(); "
                                + "every TwinsMcpTool must return a ToolKey constant");
            }
            TwinsMcpTool<?> previous = map.put(key, tool);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate tool key " + key + " — registered by "
                                + previous.getClass().getName() + " and " + tool.getClass().getName());
            }
        }
        this.byKey = Map.copyOf(map);
        Map<String, ToolKey> names = new java.util.HashMap<>();
        for (ToolKey key : byKey.keySet()) {
            names.put(key.wireName(), key);
        }
        this.wireNameToKey = Map.copyOf(names);
    }

    public TwinsMcpTool<?> lookup(ToolKey key) {
        return byKey.get(key);
    }

    /** Resolves a tool by its MCP wire name ({@code lower_snake_case}); {@code null} if unknown. */
    public ToolKey keyByWireName(String wireName) {
        return wireNameToKey.get(wireName);
    }

    public Collection<TwinsMcpTool<?>> all() {
        return byKey.values();
    }

    public int size() {
        return byKey.size();
    }
}

package org.twins.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.twins.mcp.response.ToolResponse;

/**
 * Tests for {@link ToolRegistry} (AC-1) and {@link ToolAllowlist} (AC-2) — plain JUnit, no Spring
 * context. Fake {@link TwinsMcpTool} doubles drive discovery, duplicate detection, and the
 * read-only-surface gate.
 */
class ToolRegistryAllowlistTest {

    private static final class TestTool implements TwinsMcpTool<Object> {
        private final ToolKey key;

        TestTool(ToolKey key) {
            this.key = key;
        }

        @Override
        public ToolKey key() {
            return key;
        }

        @Override
        public String description() {
            return "test tool";
        }

        @Override
        public Class<Object> argsType() {
            return Object.class;
        }

        @Override
        public ToolResponse call(Object args) {
            return new ToolResponse("md", Map.of(), null);
        }
    }

    @Test
    @DisplayName("AC-1: registry discovers a tool and looks it up by key and wire name")
    void registryDiscoversAndLooksUp() {
        ToolRegistry registry = new ToolRegistry(List.<TwinsMcpTool<?>>of(new TestTool(ToolKey.LIST_CLASSES)));

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.lookup(ToolKey.LIST_CLASSES)).isNotNull();
        assertThat(registry.keyByWireName("list_classes")).isEqualTo(ToolKey.LIST_CLASSES);
    }

    @Test
    @DisplayName("AC-1: duplicate keys fail fast, naming both beans")
    void duplicateKeysFailFast() {
        List<TwinsMcpTool<?>> tools = List.<TwinsMcpTool<?>>of(
                new TestTool(ToolKey.LIST_CLASSES),
                new TestTool(ToolKey.LIST_CLASSES));

        assertThatThrownBy(() -> new ToolRegistry(tools))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate tool key");
    }

    @Test
    @DisplayName("AC-2: a tool returning a null key fails fast with a clear message")
    void nullKeyFailsFast() {
        List<TwinsMcpTool<?>> tools = List.<TwinsMcpTool<?>>of(new TestTool(null));

        assertThatThrownBy(() -> new ToolRegistry(tools))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null key");
    }

    @Test
    @DisplayName("AC-2: allowlist passes for an allowlisted key")
    void allowlistHappyPath() {
        ToolAllowlist allowlist = new ToolAllowlist(
                List.<TwinsMcpTool<?>>of(new TestTool(ToolKey.LIST_CLASSES)));
        allowlist.verify(); // no exception expected
    }

    @Test
    @DisplayName("AC-2: a tool whose key is not in the active allowlist aborts startup")
    void nonAllowlistedKeyAborts() {
        ToolAllowlist allowlist = new ToolAllowlist(
                List.<TwinsMcpTool<?>>of(new TestTool(ToolKey.LIST_CLASSES)),
                EnumSet.noneOf(ToolKey.class));

        assertThatThrownBy(allowlist::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIST_CLASSES")
                .hasMessageContaining("read-only surface gate");
    }
}

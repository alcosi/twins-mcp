package org.twins.mcp.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Tests for {@link ToolResponseBuilder} (AC-4 hybrid response, AC-5 size cap on build). */
class ToolResponseBuilderTest {

    private final ToolResponseBuilder builder = new ToolResponseBuilder(new ObjectMapper());

    @Test
    @DisplayName("AC-4: builds a hybrid response carrying markdown, structuredContent, and cursor")
    void buildsHybridResponse() {
        ToolResponse response = builder.response()
                .markdown("## Classes\n| key |\n|---|\n| user |")
                .structuredContent(Map.of("classes", "x", "nextCursor", "abc"))
                .nextCursor("abc")
                .build();

        assertThat(response.markdown()).contains("Classes");
        assertThat(response.structuredContent()).containsEntry("classes", "x");
        assertThat(response.nextCursor()).isEqualTo("abc");
    }

    @Test
    @DisplayName("AC-4: structuredContent is never null (empty map by default)")
    void structuredContentNonNull() {
        ToolResponse response = builder.response().markdown("nothing").build();
        assertThat(response.structuredContent()).isNotNull().isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("AC-5: build fails loudly when the rendered response exceeds 32 KiB")
    void overCapBuildFails() {
        String huge = "x".repeat(SizeCapEnforcer.CAP_BYTES + 100);
        assertThatThrownBy(() -> builder.response().markdown(huge).structuredContent(Map.of()).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 KiB cap");
    }

    @Test
    @DisplayName("put() adds individual structured-content entries")
    void putAddsEntries() {
        ToolResponse response = builder.response()
                .markdown("md")
                .put("count", 3)
                .put("items", "abc")
                .build();

        assertThat(response.structuredContent())
                .containsEntry("count", 3)
                .containsEntry("items", "abc");
    }
}

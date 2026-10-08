package org.twins.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ToolJsonSchemaGenerator} — record components + JSR-380 facets → JSON schema. */
class ToolJsonSchemaGeneratorTest {

    @SuppressWarnings("unused")
    record SampleArgs(
            @NotBlank String query,
            @Min(1) @Max(100) Integer pageSize,
            @Size(max = 50) String keyLike) {
    }

    private final ToolJsonSchemaGenerator generator = new ToolJsonSchemaGenerator();

    @Test
    @DisplayName("schema is an object with the record's properties")
    void schemaShape() {
        Map<String, Object> schema = generator.schemaFor(SampleArgs.class);

        assertThat(schema).containsEntry("type", "object");
        assertThat(schema).containsEntry("additionalProperties", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsOnlyKeys("query", "pageSize", "keyLike");
    }

    @Test
    @DisplayName("@NotBlank marks the field required")
    void requiredMarked() {
        Map<String, Object> schema = generator.schemaFor(SampleArgs.class);
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertThat(required).containsExactly("query");
    }

    @Test
    @DisplayName("@Min/@Max surface as minimum/maximum facets")
    void minMaxFacets() {
        Map<String, Object> schema = generator.schemaFor(SampleArgs.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> pageSize = (Map<String, Object>) properties.get("pageSize");

        assertThat(pageSize).containsEntry("type", "integer");
        assertThat(pageSize).containsEntry("minimum", 1L);
        assertThat(pageSize).containsEntry("maximum", 100L);
    }

    @Test
    @DisplayName("@Size(max) surfaces as maxLength for a string field")
    void sizeMaxLength() {
        Map<String, Object> schema = generator.schemaFor(SampleArgs.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> keyLike = (Map<String, Object>) properties.get("keyLike");

        assertThat(keyLike).containsEntry("type", "string");
        assertThat(keyLike).containsEntry("maxLength", 50);
    }
}

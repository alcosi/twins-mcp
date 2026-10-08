package org.twins.mcp.tool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Derives a JSON Schema ({@code Map<String,Object>} form) for a tool's {@code Args} record by
 * reflecting its components and translating common JSR-380 constraints into schema facets
 * (Story 1.5 — feeds {@link org.twins.mcp.config.McpServerConfig}'s {@code McpSchema.Tool}
 * {@code inputSchema}). Hand-rolled rather than bound to Spring AI's annotation scanner so the
 * domain {@link TwinsMcpTool} stays the single source.
 *
 * <p>Constraints are read from the record's backing <em>fields</em>: jakarta validation annotations
 * lack a {@code RECORD_COMPONENT} target, so the compiler records them on the field (and accessor),
 * not on the {@link RecordComponent}. Supported: {@code @NotNull}/{@code @NotBlank}/{@code @NotEmpty}
 * (→ required), {@code @Size} (→ {@code minLength}/{@code maxLength}), {@code @Min}/{@code @Max}
 * (→ {@code minimum}/{@code maximum}), {@code @Pattern} (→ {@code pattern}).
 */
@Component
public class ToolJsonSchemaGenerator {

    /** @return a JSON-schema object map for {@code argsType}, or an empty-object schema if unknown. */
    public Map<String, Object> schemaFor(Class<?> argsType) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new LinkedList<>();
        if (argsType != null && argsType.isRecord()) {
            for (RecordComponent rc : argsType.getRecordComponents()) {
                Field field = fieldOf(argsType, rc.getName());
                properties.put(rc.getName(), propertyFor(rc.getType(), field));
                if (field != null && isRequired(field)) {
                    required.add(rc.getName());
                }
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> propertyFor(Class<?> type, Field field) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", jsonType(type));
        if (field == null) {
            return prop;
        }
        String jsonType = jsonType(type);
        Size size = field.getAnnotation(Size.class);
        if (size != null && "string".equals(jsonType)) {
            if (size.min() > 0) {
                prop.put("minLength", size.min());
            }
            if (size.max() < Integer.MAX_VALUE) {
                prop.put("maxLength", size.max());
            }
        }
        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            prop.put("minimum", min.value());
        }
        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            prop.put("maximum", max.value());
        }
        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null && pattern.regexp() != null && !".*".equals(pattern.regexp())) {
            prop.put("pattern", pattern.regexp());
        }
        return prop;
    }

    private static boolean isRequired(Field field) {
        return field.isAnnotationPresent(NotNull.class)
                || field.isAnnotationPresent(NotBlank.class)
                || field.isAnnotationPresent(NotEmpty.class);
    }

    private static Field fieldOf(Class<?> argsType, String name) {
        try {
            return argsType.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String jsonType(Class<?> type) {
        if (type == Integer.class || type == int.class
                || type == Long.class || type == long.class) {
            return "integer";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        if (type == Double.class || type == double.class
                || type == Float.class || type == float.class) {
            return "number";
        }
        return "string";
    }
}

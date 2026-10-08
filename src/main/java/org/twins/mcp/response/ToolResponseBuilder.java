package org.twins.mcp.response;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Fluent builder for {@link ToolResponse} (ARCH-14; Story 1.5 AC-4 / AC-5). On {@link Builder#build()}
 * it serialises {@code structuredContent} to JSON and runs {@link SizeCapEnforcer} so an over-cap
 * response fails loudly rather than being silently truncated. Obtain a fresh builder per response
 * via {@link #response()}.
 *
 * <p>Example:
 * <pre>{@code
 *   toolResponseBuilder.response()
 *       .markdown(table)
 *       .structuredContent(Map.of("classes", list, "nextCursor", cursor))
 *       .nextCursor(cursor)
 *       .build();
 * }</pre>
 */
@Component
public class ToolResponseBuilder {

    private final ObjectMapper mapper;

    public ToolResponseBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** @return a fresh per-response builder. */
    public Builder response() {
        return new Builder(mapper);
    }

    public static final class Builder {

        private final ObjectMapper mapper;
        private String markdown = "";
        private final Map<String, Object> structuredContent = new LinkedHashMap<>();
        private String nextCursor;

        private Builder(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public Builder markdown(String markdown) {
            this.markdown = markdown;
            return this;
        }

        public Builder structuredContent(Map<String, Object> structuredContent) {
            if (structuredContent != null) {
                this.structuredContent.putAll(structuredContent);
            }
            return this;
        }

        public Builder put(String key, Object value) {
            this.structuredContent.put(key, value);
            return this;
        }

        public Builder nextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
            return this;
        }

        public ToolResponse build() {
            Map<String, Object> content = Map.copyOf(structuredContent);
            String json = serialise(content);
            SizeCapEnforcer.enforce(markdown, json);
            return new ToolResponse(markdown, content, nextCursor);
        }

        private String serialise(Map<String, Object> content) {
            try {
                return mapper.writeValueAsString(content);
            } catch (JacksonException e) {
                throw new IllegalStateException("structuredContent is not JSON-serialisable", e);
            }
        }
    }
}

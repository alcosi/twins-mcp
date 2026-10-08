package org.twins.mcp.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.twins.mcp.error.ToolInputInvalid;
import tools.jackson.databind.ObjectMapper;

/** Tests for {@link CursorCodec} (AC-6): round-trip, filterHash binding, malformed input. */
class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec(new ObjectMapper());

    @Test
    @DisplayName("encode then decode round-trips page, pageSize, and filterHash")
    void roundTrip() {
        String cursor = codec.encode(2, 20, "{\"query\":\"x\"}");

        CursorCodec.Cursor decoded = codec.decode(cursor);
        assertThat(decoded.page()).isEqualTo(2);
        assertThat(decoded.pageSize()).isEqualTo(20);
        assertThat(decoded.filterHash()).isNotBlank();
    }

    @Test
    @DisplayName("the same filter produces the same cursor hash; a changed filter changes it")
    void filterHashBindsToFilter() {
        String c1 = codec.encode(1, 20, "{\"query\":\"x\"}");
        String c2 = codec.encode(1, 20, "{\"query\":\"x\"}");
        String c3 = codec.encode(1, 20, "{\"query\":\"y\"}");

        assertThat(codec.decode(c1).filterHash()).isEqualTo(codec.decode(c2).filterHash());
        assertThat(codec.decode(c1).filterHash()).isNotEqualTo(codec.decode(c3).filterHash());
    }

    @Test
    @DisplayName("verifyFilter accepts a matching filter")
    void verifyFilterMatching() {
        String filter = "{\"query\":\"x\"}";
        codec.verifyFilter(codec.decode(codec.encode(1, 20, filter)), filter);
    }

    @Test
    @DisplayName("verifyFilter rejects a changed filter with ToolInputInvalid (AC-6)")
    void verifyFilterMismatchThrows() {
        String cursor = codec.encode(1, 20, "{\"query\":\"x\"}");
        assertThatThrownBy(() -> codec.verifyFilter(codec.decode(cursor), "{\"query\":\"y\"}"))
                .isInstanceOf(ToolInputInvalid.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("a null/blank cursor is rejected with ToolInputInvalid")
    void missingCursorRejected() {
        assertThatThrownBy(() -> codec.decode(null)).isInstanceOf(ToolInputInvalid.class);
        assertThatThrownBy(() -> codec.decode("  ")).isInstanceOf(ToolInputInvalid.class);
    }

    @Test
    @DisplayName("a malformed cursor is rejected with ToolInputInvalid")
    void malformedCursorRejected() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor!!!"))
                .isInstanceOf(ToolInputInvalid.class);
    }
}

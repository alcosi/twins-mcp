package org.twins.mcp.response;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link SizeCapEnforcer} (AC-5) at the 32 KiB boundary. */
class SizeCapEnforcerTest {

    private static final int CAP = SizeCapEnforcer.CAP_BYTES; // 32 * 1024

    @Test
    @DisplayName("a small response passes")
    void smallResponsePasses() {
        assertThatCode(() -> SizeCapEnforcer.enforce("markdown", "{}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a response exactly at the cap passes (boundary)")
    void atCapPasses() {
        // "{}" is 2 bytes; markdown fills the rest to exactly CAP.
        String markdown = "a".repeat(CAP - 2);
        assertThatCode(() -> SizeCapEnforcer.enforce(markdown, "{}")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a response one byte over the cap fails loudly")
    void overCapFails() {
        String markdown = "a".repeat(CAP - 1); // "{}" pushes total to CAP + 1
        assertThatThrownBy(() -> SizeCapEnforcer.enforce(markdown, "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 KiB cap");
    }

    @Test
    @DisplayName("null markdown is measured as zero")
    void nullMarkdownMeasuredZero() {
        assertThatCode(() -> SizeCapEnforcer.enforce(null, "{}")).doesNotThrowAnyException();
    }
}

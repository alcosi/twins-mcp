package org.twins.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.twins.mcp.error.ToolInputInvalid;

/**
 * Tests for {@link ToolArgsValidator} (AC-3 args validation, AC-7 page-size constraints). Uses a
 * real default-validator factory; no Spring context.
 */
class ToolArgsValidatorTest {

    /** Sample args record mirroring the constraints a real tool's Args would carry. */
    @SuppressWarnings("unused")
    record SampleArgs(@NotBlank String query, @Min(1) @Max(100) Integer pageSize) {
    }

    private ToolArgsValidator validator;

    @BeforeEach
    void setUp() {
        Validator jsr380 = Validation.buildDefaultValidatorFactory().getValidator();
        validator = new ToolArgsValidator(jsr380);
    }

    @Test
    @DisplayName("AC-3: valid args pass without exception")
    void validArgsPass() {
        assertThatCode(() -> validator.validate(new SampleArgs("class", 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-3: a constraint violation throws ToolInputInvalid and never reaches call()")
    void blankQueryThrows() {
        assertThatThrownBy(() -> validator.validate(new SampleArgs("", 20)))
                .isInstanceOf(ToolInputInvalid.class)
                .hasMessageContaining("query");
    }

    @Test
    @DisplayName("AC-7: pageSize > 100 is rejected (TOOL_INPUT_INVALID)")
    void pageSizeOverMaxRejected() {
        assertThatThrownBy(() -> validator.validate(new SampleArgs("class", 200)))
                .isInstanceOf(ToolInputInvalid.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    @DisplayName("AC-7: pageSize <= 0 is rejected")
    void pageSizeUnderMinRejected() {
        assertThatThrownBy(() -> validator.validate(new SampleArgs("class", 0)))
                .isInstanceOf(ToolInputInvalid.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    @DisplayName("AC-7: pageSize default is unconstrained by the validator (null is valid)")
    void pageSizeNullIsValid() {
        assertThatCode(() -> validator.validate(new SampleArgs("class", null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ToolInputInvalid carries the per-field violation list")
    void violationListExposed() {
        assertThatThrownBy(() -> validator.validate(new SampleArgs("", 999)))
                .isInstanceOfSatisfying(ToolInputInvalid.class, e ->
                        assertThat(e.violations()).hasSize(2));
    }

    @Test
    @DisplayName("a null args object (no-input tool) is a no-op")
    void nullArgsNoOp() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }
}

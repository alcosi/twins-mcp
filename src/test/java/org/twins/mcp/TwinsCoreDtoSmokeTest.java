package org.twins.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.twins.core.dto.rest.twinclass.TwinClassDTOv1;

/**
 * Class-load smoke test for {@code com.alcosi.twins:twins-core-dto:1.4.191} under Java 25 LTS
 * without {@code --enable-preview} (resolves OQ-ARCH-1).
 *
 * <p>Purpose: prove the DTO JAR has no linkage against APIs removed in Java 23/24/25.
 * The mechanism is class-loading — if the JAR references a removed API, the JVM throws
 * {@link NoSuchMethodError} / {@link NoClassDefFoundError} when {@code TwinClassDTOv1}
 * is resolved. The minimal instantiation below is enough to force that resolution.
 *
 * <p>If this test fails, the DTO JAR must be re-pinned (see decision log D33).
 */
class TwinsCoreDtoSmokeTest {

    @Test
    void twinClassDtoClassLoadsUnderJava25() {
        // Class-load + default-constructor invocation is the entire assertion.
        // Field-state checks would test the DTO's contract, which is out of scope here.
        TwinClassDTOv1 dto = new TwinClassDTOv1();
        assertThat(dto).isNotNull();
    }
}

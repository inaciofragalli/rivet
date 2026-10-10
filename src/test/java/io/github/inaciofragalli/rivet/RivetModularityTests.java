package io.github.inaciofragalli.rivet;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class RivetModularityTests {

    @Test
    void shouldVerifyModuleBoundaries() {
        ApplicationModules.of(RivetApplication.class).verify();
    }
}
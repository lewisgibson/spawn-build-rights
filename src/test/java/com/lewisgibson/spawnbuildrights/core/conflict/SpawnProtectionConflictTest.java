package com.lewisgibson.spawnbuildrights.core.conflict;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SpawnProtectionConflict")
class SpawnProtectionConflictTest {

    @Test
    @DisplayName("no conflict when vanilla spawn-protection is disabled")
    void noConflictWhenDisabled() {
        assertThat(SpawnProtectionConflict.conflicts(0)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 16, 100})
    @DisplayName("conflict when vanilla spawn-protection is enabled")
    void conflictWhenEnabled(int radius) {
        assertThat(SpawnProtectionConflict.conflicts(radius)).isTrue();
    }

    @Test
    @DisplayName("a negative radius is treated as no conflict")
    void negativeRadiusIsNoConflict() {
        assertThat(SpawnProtectionConflict.conflicts(-1)).isFalse();
    }
}

package com.lewisgibson.spawnbuildrights.core.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RegionShape")
class RegionShapeTest {

    @ParameterizedTest
    @CsvSource({
        "square, SQUARE",
        "SQUARE, SQUARE",
        "'  square  ', SQUARE",
        "circle, CIRCLE",
        "Circle, CIRCLE",
        "round, CIRCLE",
    })
    @DisplayName("fromToken parses known tokens case-insensitively and trimmed")
    void fromTokenParsesKnownTokens(String input, RegionShape expected) {
        assertThat(RegionShape.fromToken(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("fromToken rejects null")
    void fromTokenRejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> RegionShape.fromToken(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "triangle", "sq", "hexagon"})
    @DisplayName("fromToken rejects unknown tokens")
    void fromTokenRejectsUnknown(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> RegionShape.fromToken(input));
    }

    @Test
    @DisplayName("token round-trips through fromToken")
    void tokenRoundTrips() {
        for (RegionShape shape : RegionShape.values()) {
            assertThat(RegionShape.fromToken(shape.token())).isEqualTo(shape);
        }
    }
}

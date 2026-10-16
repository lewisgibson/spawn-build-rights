package com.lewisgibson.spawnbuildrights.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Placeholders")
class PlaceholdersTest {

    @Test
    @DisplayName("substitutes a single placeholder")
    void substitutesSingle() {
        assertThat(Placeholders.apply("hi %name%", Map.of("name", "Steve"))).isEqualTo("hi Steve");
    }

    @Test
    @DisplayName("substitutes multiple and repeated placeholders")
    void substitutesMultipleAndRepeated() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("a", "1");
        values.put("b", "2");
        assertThat(Placeholders.apply("%a%-%b%-%a%", values)).isEqualTo("1-2-1");
    }

    @Test
    @DisplayName("leaves unknown placeholders verbatim")
    void leavesUnknownPlaceholders() {
        assertThat(Placeholders.apply("%known% %other%", Map.of("known", "X")))
                .isEqualTo("X %other%");
    }

    @Test
    @DisplayName("returns the template unchanged when there is nothing to substitute")
    void emptyValuesReturnsTemplate() {
        assertThat(Placeholders.apply("no tokens here", Map.of())).isEqualTo("no tokens here");
    }

    @Test
    @DisplayName("inserts a value that itself contains a percent sign")
    void valueContainingPercentIsInserted() {
        assertThat(Placeholders.apply("progress: %pct%", Map.of("pct", "50%")))
                .isEqualTo("progress: 50%");
    }

    @Test
    @DisplayName("rejects a null template")
    void rejectsNullTemplate() {
        assertThatNullPointerException().isThrownBy(() -> Placeholders.apply(null, Map.of()));
    }

    @Test
    @DisplayName("rejects a null values map")
    void rejectsNullValues() {
        assertThatNullPointerException().isThrownBy(() -> Placeholders.apply("x", null));
    }

    @Test
    @DisplayName("playtime gives the requirement, the playtime and what is left, in words")
    void playtimeInWords() {
        assertThat(Placeholders.playtime(1_440_000L, 2_592_000L))
                .containsEntry("required", "36 hours")
                .containsEntry("played", "20 hours")
                .containsEntry("remaining", "16 hours")
                .hasSize(3);
    }

    @Test
    @DisplayName("playtime never shows a negative remaining time")
    void playtimeRemainingFloorsAtZero() {
        assertThat(Placeholders.playtime(3_000_000L, 2_592_000L))
                .containsEntry("remaining", "0 seconds");
    }
}

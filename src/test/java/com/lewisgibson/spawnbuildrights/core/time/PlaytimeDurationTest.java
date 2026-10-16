package com.lewisgibson.spawnbuildrights.core.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PlaytimeDuration")
class PlaytimeDurationTest {

    @Test
    @DisplayName("tick constants are correct")
    void tickConstants() {
        assertThat(PlaytimeDuration.TICKS_PER_SECOND).isEqualTo(20L);
        assertThat(PlaytimeDuration.TICKS_PER_MINUTE).isEqualTo(1_200L);
        assertThat(PlaytimeDuration.TICKS_PER_HOUR).isEqualTo(72_000L);
        assertThat(PlaytimeDuration.TICKS_PER_DAY).isEqualTo(1_728_000L);
    }

    @ParameterizedTest
    @CsvSource({
        "36h, 2592000",
        "36H, 2592000",
        "1d, 1728000",
        "90m, 108000",
        "45s, 900",
        "1d12h, 2592000",
        "'1d 12h', 2592000",
        "2d3h4m5s, 3676900",
        "'  2h  ', 144000",
    })
    @DisplayName("parseTicks parses single and compound durations")
    void parseTicksParses(String input, long expected) {
        assertThat(PlaytimeDuration.parseTicks(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("36h and 1d12h are the same number of ticks")
    void equivalentDurationsParseEqually() {
        assertThat(PlaytimeDuration.parseTicks("36h"))
                .isEqualTo(PlaytimeDuration.parseTicks("1d12h"));
    }

    @Test
    @DisplayName("parseTicks rejects null")
    void parseRejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.parseTicks(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "36", "36x", "abc", "h", "36hh", "-5h", "3.5h", "36h!"})
    @DisplayName("parseTicks rejects malformed input")
    void parseRejectsMalformed(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.parseTicks(input));
    }

    @Test
    @DisplayName("parseTicks throws on overflow rather than wrapping")
    void parseThrowsOnOverflow() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> PlaytimeDuration.parseTicks("9223372036854775807s"));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0s",
        "19, 0s",
        "900, 45s",
        "144000, 2h",
        "864000, 12h",
        "1728000, 24h",
        "2592000, 36h",
        "3676900, 51h4m5s",
        "12095980, 167h59m59s",
        "12096000, 7d",
        "14040000, 8d3h",
        "14041220, 8d3h1m1s",
    })
    @DisplayName("format renders the exact compact form, in hours below a week and days from one")
    void formatRenders(long ticks, String expected) {
        assertThat(PlaytimeDuration.format(ticks)).isEqualTo(expected);
    }

    @Test
    @DisplayName("format normalises 90 minutes into 1h30m")
    void formatNormalisesMinutes() {
        assertThat(PlaytimeDuration.format(108_000L)).isEqualTo("1h30m");
    }

    @Test
    @DisplayName("format and display reject negative ticks")
    void formatRejectsNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.format(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.display(-1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"36h", "51h4m5s", "45s", "0s", "12h", "7d", "8d3h1m1s", "1h30m"})
    @DisplayName("format is the inverse of parseTicks for canonical forms")
    void formatRoundTrips(String canonical) {
        assertThat(PlaytimeDuration.format(PlaytimeDuration.parseTicks(canonical)))
                .isEqualTo(canonical);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 20, 2_592_000L, 3_676_900L, 12_095_980L, 14_041_220L, 1L << 40})
    @DisplayName("what format writes to config.yml always parses back to the same whole seconds")
    void formatWritesWhatTheParserReads(long ticks) {
        assertThat(PlaytimeDuration.parseTicks(PlaytimeDuration.format(ticks)))
                .isEqualTo(ticks - ticks % PlaytimeDuration.TICKS_PER_SECOND);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0s",
        "19, 0s",
        "800, 40s",
        "54200, 45m 10s",
        "54000, 45m",
        "2592000, 36h",
        "2425260, 33h 41m",
        "2425280, 33h 41m",
        "2412000, 33h 30m",
        "2232000, 31h",
        "2160020, 30h",
        "12095980, 167h 59m",
        "12096000, 7d",
        "14040000, 8d 3h",
        "14041220, 8d 3h",
        "12168000, 7d 1h",
    })
    @DisplayName("display shows the largest unit and the next one, in hours below a week")
    void displayRenders(long ticks, String expected) {
        assertThat(PlaytimeDuration.display(ticks)).isEqualTo(expected);
    }

    @Test
    @DisplayName(
            "display drops the next unit when it is zero, rather than skipping to a smaller one")
    void displayNeverSkipsAUnit() {
        // 36h 0m 5s: the minutes are zero, so the seconds are not promoted into their place.
        assertThat(PlaytimeDuration.display(2_592_100L)).isEqualTo("36h");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 800, 54_200L, 2_425_260L, 14_041_220L})
    @DisplayName("what display shows also parses, to the value without the dropped units")
    void displayParses(long ticks) {
        long shown = PlaytimeDuration.parseTicks(PlaytimeDuration.display(ticks));
        assertThat(shown).isLessThanOrEqualTo(ticks);
        assertThat(PlaytimeDuration.display(shown)).isEqualTo(PlaytimeDuration.display(ticks));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0 seconds",
        "20, 1 second",
        "800, 40 seconds",
        "1200, 1 minute",
        "54200, 45 minutes and 10 seconds",
        "72000, 1 hour",
        "73200, 1 hour and 1 minute",
        "2592000, 36 hours",
        "2425260, 33 hours and 41 minutes",
        "2592100, 36 hours",
        "12096000, 7 days",
        "1728000, 24 hours",
        "13896000, 8 days and 1 hour",
        "14040000, 8 days and 3 hours",
    })
    @DisplayName("words names the same units as display, in words, joined by and")
    void wordsRenders(long ticks, String expected) {
        assertThat(PlaytimeDuration.words(ticks)).isEqualTo(expected);
    }

    @Test
    @DisplayName("words rejects negative ticks")
    void wordsRejectsNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.words(-1));
    }
}

package com.lewisgibson.spawnbuildrights.core.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GrantOverride")
class GrantOverrideTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    @DisplayName("holds a by-hand decision")
    void holdsDecision() {
        GrantOverride override =
                new GrantOverride(PLAYER, "Steve", OverrideState.REVOKED, "console", "grief", AT);
        assertThat(override.player()).isEqualTo(PLAYER);
        assertThat(override.playerName()).isEqualTo("Steve");
        assertThat(override.state()).isEqualTo(OverrideState.REVOKED);
        assertThat(override.actor()).isEqualTo("console");
        assertThat(override.reason()).isEqualTo("grief");
        assertThat(override.at()).isEqualTo(AT);
    }

    @Test
    @DisplayName("is never NONE - a player with no override has no row")
    void rejectsNone() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        PLAYER, "Steve", OverrideState.NONE, "console", "", AT));
    }

    @Test
    @DisplayName("rejects every null field")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        null, "Steve", OverrideState.GRANTED, "console", "", AT));
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        PLAYER, null, OverrideState.GRANTED, "console", "", AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantOverride(PLAYER, "Steve", null, "console", "", AT));
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        PLAYER, "Steve", OverrideState.GRANTED, null, "", AT));
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        PLAYER,
                                        "Steve",
                                        OverrideState.GRANTED,
                                        "console",
                                        null,
                                        AT));
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        PLAYER,
                                        "Steve",
                                        OverrideState.GRANTED,
                                        "console",
                                        "",
                                        null));
    }

    @Test
    @DisplayName("holds a reason up to the store's column width and no longer")
    void limitsReason() {
        String longest = "a".repeat(GrantOverride.MAX_REASON_LENGTH);
        assertThat(
                        new GrantOverride(
                                        PLAYER,
                                        "Steve",
                                        OverrideState.GRANTED,
                                        "console",
                                        longest,
                                        AT)
                                .reason())
                .isEqualTo(longest);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GrantOverride(
                                        PLAYER,
                                        "Steve",
                                        OverrideState.GRANTED,
                                        "console",
                                        longest + "a",
                                        AT));
    }

    @Test
    @DisplayName("truncates a reason without splitting a surrogate pair")
    void truncatesReason() {
        String max = "a".repeat(GrantOverride.MAX_REASON_LENGTH);
        assertThat(GrantOverride.truncateReason("short")).isEqualTo("short");
        assertThat(GrantOverride.truncateReason(max)).isEqualTo(max);
        assertThat(GrantOverride.truncateReason(max + "b")).isEqualTo(max);
        String pair = "\uD83D\uDE00";
        String straddling = "a".repeat(GrantOverride.MAX_REASON_LENGTH - 1) + pair;
        assertThat(GrantOverride.truncateReason(straddling))
                .isEqualTo("a".repeat(GrantOverride.MAX_REASON_LENGTH - 1));
    }
}

package com.lewisgibson.spawnbuildrights.core.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GrantEvent")
class GrantEventTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    @DisplayName("holds one audit entry")
    void holdsEntry() {
        GrantEvent event =
                new GrantEvent(
                        PLAYER, "Steve", GrantEvent.Kind.EARNED, "playtime", "", 2_592_000L, AT);
        assertThat(event.player()).isEqualTo(PLAYER);
        assertThat(event.playerName()).isEqualTo("Steve");
        assertThat(event.kind()).isEqualTo(GrantEvent.Kind.EARNED);
        assertThat(event.actor()).isEqualTo("playtime");
        assertThat(event.reason()).isEmpty();
        assertThat(event.playtimeTicks()).isEqualTo(2_592_000L);
        assertThat(event.at()).isEqualTo(AT);
    }

    @Test
    @DisplayName("allows -1 for an unknown playtime and nothing lower")
    void playtimeLowerBound() {
        assertThat(
                        new GrantEvent(
                                        PLAYER,
                                        "Steve",
                                        GrantEvent.Kind.RESET,
                                        "console",
                                        "",
                                        -1,
                                        AT)
                                .playtimeTicks())
                .isEqualTo(-1);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GrantEvent(
                                        PLAYER,
                                        "Steve",
                                        GrantEvent.Kind.RESET,
                                        "console",
                                        "",
                                        -2,
                                        AT));
    }

    @Test
    @DisplayName("rejects every null field")
    void rejectsNulls() {
        GrantEvent.Kind kind = GrantEvent.Kind.GRANTED;
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantEvent(null, "Steve", kind, "console", "", 0, AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantEvent(PLAYER, null, kind, "console", "", 0, AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantEvent(PLAYER, "Steve", null, "console", "", 0, AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantEvent(PLAYER, "Steve", kind, null, "", 0, AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantEvent(PLAYER, "Steve", kind, "console", null, 0, AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantEvent(PLAYER, "Steve", kind, "console", "", 0, null));
    }

    @Test
    @DisplayName("rejects a reason longer than the store's column width")
    void limitsReason() {
        GrantEvent.Kind kind = GrantEvent.Kind.GRANTED;
        String longest = "a".repeat(GrantOverride.MAX_REASON_LENGTH);
        assertThat(new GrantEvent(PLAYER, "Steve", kind, "console", longest, 0, AT).reason())
                .isEqualTo(longest);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GrantEvent(
                                        PLAYER, "Steve", kind, "console", longest + "a", 0, AT));
    }
}

package com.lewisgibson.spawnbuildrights.core.grant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry in the audit log: a by-hand grant, revocation or reset, or the moment a player was
 * first seen to have earned build access through playtime.
 *
 * @param player the player's unique id
 * @param playerName the player's name at the time
 * @param kind what happened
 * @param actor who did it (a player name, {@code console}, or {@code playtime} for an earned event)
 * @param reason the reason given, or an empty string
 * @param playtimeTicks the player's playtime at the time, when known, else {@link #NO_PLAYTIME}
 * @param at when it happened
 */
public record GrantEvent(
        UUID player,
        String playerName,
        Kind kind,
        String actor,
        String reason,
        long playtimeTicks,
        Instant at) {

    /** The kinds of event the log records. */
    public enum Kind {
        /** {@code /sbr grant}. */
        GRANTED,
        /** {@code /sbr revoke}. */
        REVOKED,
        /** {@code /sbr reset}: the override was removed and playtime decides again. */
        RESET,
        /** The player was first observed at or past the playtime threshold. */
        EARNED
    }

    /** The {@code playtimeTicks} value for an event with no playtime to record. */
    public static final long NO_PLAYTIME = -1;

    /**
     * @throws IllegalArgumentException if the playtime is below {@link #NO_PLAYTIME} or the reason
     *     is longer than {@link GrantOverride#MAX_REASON_LENGTH}
     */
    public GrantEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        if (reason.length() > GrantOverride.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "reason must be at most " + GrantOverride.MAX_REASON_LENGTH + " characters");
        }
        if (playtimeTicks < NO_PLAYTIME) {
            throw new IllegalArgumentException(
                    "playtimeTicks must be >= " + NO_PLAYTIME + ", was " + playtimeTicks);
        }
    }
}

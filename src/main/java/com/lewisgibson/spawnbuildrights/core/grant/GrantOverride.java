package com.lewisgibson.spawnbuildrights.core.grant;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A by-hand decision about one player, kept in the plugin's store: granted or revoked, by whom, why
 * and when. A player with no override has no row; {@link OverrideState#NONE} is never stored.
 *
 * @param player the player's unique id
 * @param playerName the player's name at the time, for reading the store without a lookup
 * @param state {@link OverrideState#GRANTED} or {@link OverrideState#REVOKED}
 * @param actor who made the decision (a player name or {@code console})
 * @param reason the reason given, or an empty string
 * @param at when the decision was made
 */
public record GrantOverride(
        UUID player,
        String playerName,
        OverrideState state,
        String actor,
        String reason,
        Instant at) {

    /** The longest reason the store can hold, matching its {@code VARCHAR(255)} column. */
    public static final int MAX_REASON_LENGTH = 255;

    /**
     * @param reason free text typed by an admin
     * @return the reason cut to {@link #MAX_REASON_LENGTH} characters, never splitting a surrogate
     *     pair
     */
    public static String truncateReason(String reason) {
        if (reason.length() <= MAX_REASON_LENGTH) {
            return reason;
        }
        int end = MAX_REASON_LENGTH;
        if (Character.isHighSurrogate(reason.charAt(end - 1))) {
            end--;
        }
        return reason.substring(0, end);
    }

    /**
     * @throws IllegalArgumentException if the state is {@link OverrideState#NONE} or the reason is
     *     longer than {@link #MAX_REASON_LENGTH}
     */
    public GrantOverride {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        if (state == OverrideState.NONE) {
            throw new IllegalArgumentException("an override is GRANTED or REVOKED, never NONE");
        }
    }
}

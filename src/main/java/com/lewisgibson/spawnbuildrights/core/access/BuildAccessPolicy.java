package com.lewisgibson.spawnbuildrights.core.access;

import java.util.Objects;

/**
 * Decides whether a player may change blocks inside the spawn region, from the state of their build
 * permission node, the plugin's own stored override, and their accumulated playtime.
 *
 * <p>The rule is: a revocation anywhere denies, then a grant anywhere allows, and only when nothing
 * has been set by hand does playtime decide. Deny beats grant so that a revocation is never
 * silently overridden by a group node or an older grant, and grant beats the clock so that a
 * decision an operator has made by hand is never undone by it.
 *
 * <p>This class is intentionally free of any server-platform types so it can be unit-tested in
 * isolation and reused unchanged by a future non-Bukkit platform adapter. The caller is responsible
 * for first establishing that the affected block is inside the region and for supplying the three
 * inputs.
 */
public final class BuildAccessPolicy {

    private final long requiredPlaytimeTicks;

    /**
     * @param requiredPlaytimeTicks the minimum accumulated playtime, in game ticks (20 per second),
     *     a player must have to build in the region; must be {@code >= 0}
     * @throws IllegalArgumentException if {@code requiredPlaytimeTicks} is negative
     */
    public BuildAccessPolicy(long requiredPlaytimeTicks) {
        if (requiredPlaytimeTicks < 0) {
            throw new IllegalArgumentException(
                    "requiredPlaytimeTicks must be >= 0, was " + requiredPlaytimeTicks);
        }
        this.requiredPlaytimeTicks = requiredPlaytimeTicks;
    }

    /**
     * Evaluates build access for a player.
     *
     * @param playtimeTicks the player's accumulated playtime in ticks
     * @param permission the state of the player's build permission node
     * @param override the state of the player's stored override
     * @return the access decision
     */
    public AccessResult evaluate(
            long playtimeTicks, PermissionState permission, OverrideState override) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(override, "override");
        if (permission == PermissionState.REVOKED) {
            return AccessResult.denied(DecisionReason.PERMISSION_REVOKED);
        }
        if (override == OverrideState.REVOKED) {
            return AccessResult.denied(DecisionReason.OVERRIDE_REVOKED);
        }
        if (permission == PermissionState.GRANTED) {
            return AccessResult.allowed(DecisionReason.PERMISSION_GRANTED);
        }
        if (override == OverrideState.GRANTED) {
            return AccessResult.allowed(DecisionReason.OVERRIDE_GRANTED);
        }
        if (playtimeTicks >= requiredPlaytimeTicks) {
            return AccessResult.allowed(DecisionReason.THRESHOLD_MET);
        }
        return AccessResult.denied(DecisionReason.BELOW_THRESHOLD);
    }
}

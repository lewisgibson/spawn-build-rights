package com.lewisgibson.spawnbuildrights.core.settings;

import com.lewisgibson.spawnbuildrights.core.message.Messages;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import java.util.Objects;

/**
 * The fully-resolved, immutable plugin configuration.
 *
 * <p>Produced by the platform adapter from the on-disk config (with the region centre resolved to
 * the world spawn when not explicitly set), and consumed by the pure decision logic. Being
 * platform-free, it can be constructed directly in tests.
 *
 * @param region the protected spawn region
 * @param requiredPlaytimeTicks accumulated playtime, in ticks, required to build in the region
 * @param buildPermission the permission node that records a by-hand grant or revocation
 * @param warnOnVanillaSpawnProtection whether to log a startup warning if vanilla spawn-protection
 *     is on
 * @param alertOperatorsOnJoin whether to privately message operators on join about a conflict
 * @param messages the player-facing message templates
 * @param storage where the store lives
 * @param protection environmental protection inside the same spawn region
 */
public record SpawnBuildRightsSettings(
        SpawnRegion region,
        long requiredPlaytimeTicks,
        String buildPermission,
        boolean warnOnVanillaSpawnProtection,
        boolean alertOperatorsOnJoin,
        Messages messages,
        StorageSettings storage,
        EnvironmentalProtection protection) {

    public SpawnBuildRightsSettings {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(buildPermission, "buildPermission");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(protection, "protection");
        if (requiredPlaytimeTicks < 0) {
            throw new IllegalArgumentException(
                    "requiredPlaytimeTicks must be >= 0, was " + requiredPlaytimeTicks);
        }
    }

    /**
     * @param newRegion the region to use instead
     * @return these settings with only the region replaced
     */
    public SpawnBuildRightsSettings withRegion(SpawnRegion newRegion) {
        return new SpawnBuildRightsSettings(
                newRegion,
                requiredPlaytimeTicks,
                buildPermission,
                warnOnVanillaSpawnProtection,
                alertOperatorsOnJoin,
                messages,
                storage,
                protection);
    }
}

package com.lewisgibson.spawnbuildrights.bukkit;

import com.lewisgibson.spawnbuildrights.core.access.BuildAccessPolicy;
import com.lewisgibson.spawnbuildrights.core.grant.GrantService;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;

/**
 * The live runtime state derived from configuration, swapped atomically on reload so listeners
 * always see a consistent settings, policy and grants triple.
 *
 * @param settings the resolved settings
 * @param policy the build-access policy derived from {@code settings}
 * @param grants the by-hand decisions and the registry the hot path reads
 */
public record PluginRuntime(
        SpawnBuildRightsSettings settings, BuildAccessPolicy policy, GrantService grants) {

    static PluginRuntime from(SpawnBuildRightsSettings settings, GrantService grants) {
        return new PluginRuntime(
                settings, new BuildAccessPolicy(settings.requiredPlaytimeTicks()), grants);
    }
}

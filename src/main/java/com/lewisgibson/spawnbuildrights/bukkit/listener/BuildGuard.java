package com.lewisgibson.spawnbuildrights.bukkit.listener;

import com.lewisgibson.spawnbuildrights.bukkit.PluginRuntime;
import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import com.lewisgibson.spawnbuildrights.bukkit.permission.BuildPermission;
import com.lewisgibson.spawnbuildrights.bukkit.playtime.Playtime;
import com.lewisgibson.spawnbuildrights.core.access.AccessResult;
import com.lewisgibson.spawnbuildrights.core.access.DecisionReason;
import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.access.PermissionState;
import com.lewisgibson.spawnbuildrights.core.message.Placeholders;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/** Applies the same region and build decision to blocks and displayed items. */
final class BuildGuard {
    private final SpawnBuildRightsPlugin plugin;

    BuildGuard(SpawnBuildRightsPlugin plugin) {
        this.plugin = plugin;
    }

    void check(Player player, Location location, Cancellable event) {
        check(plugin.runtime(), player, location, event);
    }

    void check(PluginRuntime runtime, Player player, Location location, Cancellable event) {
        SpawnBuildRightsSettings settings = runtime.settings();
        SpawnRegion region = settings.region();

        // Every location here is a block's or an entity's, which always has a world.
        if (!region.contains(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ())) {
            return;
        }

        PermissionState permission = BuildPermission.of(player, settings.buildPermission());
        OverrideState override = runtime.grants().registry().state(player.getUniqueId());
        long playtime = Playtime.ticks(player);
        AccessResult result = runtime.policy().evaluate(playtime, permission, override);
        if (result.allowed()) {
            if (result.reason() == DecisionReason.THRESHOLD_MET) {
                runtime.grants().recordEarned(player.getUniqueId(), player.getName(), playtime);
            }
            return;
        }

        event.setCancelled(true);
        plugin.messenger()
                .sendActionBar(
                        player,
                        settings.messages().about(result),
                        Placeholders.playtime(playtime, settings.requiredPlaytimeTicks()));
    }
}

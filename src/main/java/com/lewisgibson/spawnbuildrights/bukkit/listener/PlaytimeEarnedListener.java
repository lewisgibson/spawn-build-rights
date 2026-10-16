package com.lewisgibson.spawnbuildrights.bukkit.listener;

import com.lewisgibson.spawnbuildrights.bukkit.PluginRuntime;
import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import com.lewisgibson.spawnbuildrights.bukkit.permission.BuildPermission;
import com.lewisgibson.spawnbuildrights.bukkit.playtime.Playtime;
import com.lewisgibson.spawnbuildrights.core.access.AccessResult;
import com.lewisgibson.spawnbuildrights.core.access.DecisionReason;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Records the moment a player is first seen to have earned build access, on join. The protection
 * listener records it too when an allowed action happens, so the audit log names the earliest
 * observation whether or not the player ever builds at spawn.
 */
public final class PlaytimeEarnedListener implements Listener {

    private final SpawnBuildRightsPlugin plugin;

    /**
     * @param plugin the plugin whose live runtime snapshot each join reads
     */
    public PlaytimeEarnedListener(SpawnBuildRightsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Checks once per player: after the first record the registry lookup ends the handler. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PluginRuntime runtime = plugin.runtime();
        if (runtime.grants().registry().hasEarned(player.getUniqueId())) {
            return;
        }
        long playtime = Playtime.ticks(player);
        AccessResult result =
                runtime.policy()
                        .evaluate(
                                playtime,
                                BuildPermission.of(player, runtime.settings().buildPermission()),
                                runtime.grants().registry().state(player.getUniqueId()));
        if (result.reason() == DecisionReason.THRESHOLD_MET) {
            runtime.grants().recordEarned(player.getUniqueId(), player.getName(), playtime);
        }
    }
}

package com.lewisgibson.spawnbuildrights.bukkit.listener;

import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import com.lewisgibson.spawnbuildrights.core.conflict.SpawnProtectionConflict;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Privately warns operators on join when vanilla spawn-protection is enabled, since it silently
 * overrides this plugin and blocks players who have earned build access.
 */
public final class OperatorAlertListener implements Listener {

    private final SpawnBuildRightsPlugin plugin;

    /**
     * @param plugin the plugin whose live runtime snapshot each join reads
     */
    public OperatorAlertListener(SpawnBuildRightsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Warns only operators and admins, and only while the vanilla radius is actually set. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (!settings.alertOperatorsOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission(SpawnBuildRightsPlugin.ADMIN_PERMISSION)) {
            return;
        }
        int spawnRadius = plugin.getServer().getSpawnRadius();
        if (!SpawnProtectionConflict.conflicts(spawnRadius)) {
            return;
        }
        plugin.messenger()
                .send(
                        player,
                        settings.messages().operatorConflictWarning(),
                        Map.of("radius", Integer.toString(spawnRadius)));
    }
}

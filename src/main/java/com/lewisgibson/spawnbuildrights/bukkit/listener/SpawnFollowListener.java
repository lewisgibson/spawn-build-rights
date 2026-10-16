package com.lewisgibson.spawnbuildrights.bukkit.listener;

import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.SpawnChangeEvent;

/**
 * Keeps a region whose centre is left blank on the world spawn when the spawn moves, which the
 * server reports for {@code /setworldspawn} and for a plugin setting the spawn alike. Without it
 * the region would stay at the old spawn until the next reload.
 */
public final class SpawnFollowListener implements Listener {

    private final SpawnBuildRightsPlugin plugin;

    /**
     * @param plugin the plugin whose region follows the spawn
     */
    public SpawnFollowListener(SpawnBuildRightsPlugin plugin) {
        this.plugin = plugin;
    }

    /** A spawn moved in some world; the plugin decides whether its region follows. */
    @EventHandler
    public void onSpawnChange(SpawnChangeEvent event) {
        plugin.followWorldSpawn(event.getWorld());
    }
}

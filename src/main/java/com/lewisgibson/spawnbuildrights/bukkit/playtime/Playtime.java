package com.lewisgibson.spawnbuildrights.bukkit.playtime;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/** Reads a player's accumulated vanilla playtime. */
public final class Playtime {

    private Playtime() {}

    /**
     * @param player the online player to read
     * @return the player's total accumulated playtime in ticks (20 per second). The Bukkit
     *     statistic constant is historically named {@code PLAY_ONE_MINUTE} but maps to the vanilla
     *     {@code minecraft:play_time} statistic, which is the true accumulated total across
     *     sessions.
     */
    public static long ticks(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }
}

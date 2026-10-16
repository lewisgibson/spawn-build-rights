package com.lewisgibson.spawnbuildrights.fixture;

import java.util.function.Consumer;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Actual blaze projectiles and public Paper block ticks, only in the disposable test world. */
final class FireScenarios implements Listener {
    private final JavaPlugin plugin;
    private final Consumer<String> result;
    private int ignitions;
    private boolean cancelledIgnition;
    private boolean insideBurn;
    private boolean outsideBurn;
    private boolean insideSpread;
    private boolean outsideSpread;

    FireScenarios(JavaPlugin plugin, Consumer<String> result) {
        this.plugin = plugin;
        this.result = result;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void ignition(BlockIgniteEvent event) {
        if (event.getIgnitingEntity() instanceof Blaze
                && event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
            ignitions++;
            cancelledIgnition = event.isCancelled();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void burn(BlockBurnEvent event) {
        if (event.getBlock().getX() == 16 && event.isCancelled()) {
            insideBurn = true;
        }
        if (event.getBlock().getX() == 18 && !event.isCancelled()) {
            outsideBurn = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void spread(BlockSpreadEvent event) {
        if (event.getNewState().getType() != Material.FIRE) {
            return;
        }
        if (event.getBlock().getX() == 16 && event.isCancelled()) {
            insideSpread = true;
        }
        if (event.getBlock().getX() == 18 && !event.isCancelled()) {
            outsideSpread = true;
        }
    }

    String blaze(World world, int targetX) {
        ignitions = 0;
        cancelledIgnition = false;
        world.getBlockAt(targetX - 1, 80, 0).setType(Material.STONE);
        world.getBlockAt(targetX, 79, 0).setType(Material.NETHERRACK);
        Blaze blaze =
                world.spawn(
                        new Location(world, targetX + 3, 84, 4),
                        Blaze.class,
                        mob -> {
                            mob.setAI(false);
                            mob.setGravity(false);
                            mob.setRemoveWhenFarAway(false);
                        });
        SmallFireball fireball =
                world.spawn(
                        new Location(world, targetX + 3.5, 80.5, 0.5),
                        SmallFireball.class,
                        entity -> {
                            entity.setShooter(blaze);
                            entity.setIsIncendiary(true);
                            entity.setDirection(new Vector(-1, 0, 0));
                            entity.setVelocity(new Vector(-0.5, 0, 0));
                        });
        result.accept("pending");
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            plugin.getLogger()
                                    .info(
                                            "Blaze fireball at "
                                                    + fireball.getLocation()
                                                    + "; removed="
                                                    + fireball.isDead()
                                                    + "; shooter removed="
                                                    + blaze.isDead()
                                                    + "; incendiary="
                                                    + fireball.isIncendiary());
                            result.accept(
                                    "ignited="
                                            + (ignitions > 0)
                                            + " cancelled="
                                            + cancelledIgnition
                                            + " block="
                                            + world.getBlockAt(targetX, 80, 0).getType());
                            fireball.remove();
                            blaze.remove();
                        },
                        40);
        return "pending";
    }

    String boundary(World world) {
        insideBurn = outsideBurn = insideSpread = outsideSpread = false;
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.ALLOW_FIRE_TICKS_AWAY_FROM_PLAYER, true);
        world.setStorm(false);
        world.getBlockAt(17, 79, 0).setType(Material.NETHERRACK);
        world.getBlockAt(16, 80, 0).setType(Material.WHITE_WOOL);
        world.getBlockAt(15, 81, 0).setType(Material.WHITE_WOOL);
        boolean outsideChanged = false;
        boolean outsideFire = false;
        // Exercise the real fire block's tick, never manufacture the Bukkit events.
        // Replenish only outside fuel; protected targets are never reset over a failed protection.
        for (int attempts = 0;
                attempts < 2048
                        && !(insideBurn
                                && outsideBurn
                                && insideSpread
                                && outsideSpread
                                && outsideChanged
                                && outsideFire);
                attempts++) {
            world.getBlockAt(18, 80, 0).setType(Material.WHITE_WOOL, false);
            world.getBlockAt(19, 81, 0).setType(Material.WHITE_WOOL, false);
            world.getBlockAt(18, 81, 0).setType(Material.AIR, false);
            world.getBlockAt(17, 80, 0).setType(Material.FIRE, false);
            world.getBlockAt(17, 80, 0).tick();
            outsideChanged |= world.getBlockAt(18, 80, 0).getType() != Material.WHITE_WOOL;
            outsideFire |= world.getBlockAt(18, 81, 0).getType() == Material.FIRE;
        }
        return "insideBurn="
                + insideBurn
                + " outsideBurn="
                + outsideBurn
                + " insideSpread="
                + insideSpread
                + " outsideSpread="
                + outsideSpread
                + " protectedWood="
                + (world.getBlockAt(16, 80, 0).getType() == Material.WHITE_WOOL
                        && world.getBlockAt(15, 81, 0).getType() == Material.WHITE_WOOL)
                + " protectedAir="
                + (world.getBlockAt(16, 81, 0).getType() == Material.AIR)
                + " outsideChanged="
                + outsideChanged
                + " outsideFire="
                + outsideFire;
    }
}

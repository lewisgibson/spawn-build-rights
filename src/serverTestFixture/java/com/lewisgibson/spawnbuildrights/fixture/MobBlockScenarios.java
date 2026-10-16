package com.lewisgibson.spawnbuildrights.fixture;

import com.destroystokyo.paper.entity.ai.Goal;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real vanilla mob goals and TNT entities, driven only in the disposable test world. */
final class MobBlockScenarios implements Listener {
    private final JavaPlugin plugin;
    private final Consumer<String> result;
    private boolean attempted;
    private boolean cancelled;
    private boolean exploded;
    private boolean primed;

    MobBlockScenarios(JavaPlugin plugin, Consumer<String> result) {
        this.plugin = plugin;
        this.result = result;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void endermanChange(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman) {
            attempted = true;
            cancelled = event.isCancelled();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void prime(TNTPrimeEvent event) {
        attempted = true;
        cancelled = event.isCancelled();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void spawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof TNTPrimed) {
            primed = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void explode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof TNTPrimed) {
            exploded = true;
        }
    }

    String enderman(World world, int targetX, boolean place) {
        attempted = cancelled = false;
        world.getBlockAt(targetX, 79, 0).setType(Material.STONE);
        world.getBlockAt(targetX, 80, 0).setType(place ? Material.AIR : Material.DIRT);
        Enderman enderman =
                world.spawn(
                        new Location(world, targetX + 1.5, 80, 0.5),
                        Enderman.class,
                        entity -> {
                            entity.setAI(false);
                            entity.setGravity(false);
                            entity.setCarriedBlock(place ? Material.DIRT.createBlockData() : null);
                        });
        String goalName = place ? "enderman_leave_block" : "enderman_take_block";
        Goal<Enderman> goal =
                plugin.getServer().getMobGoals().getAllGoals(enderman).stream()
                        .filter(
                                candidate ->
                                        candidate
                                                .getKey()
                                                .getNamespacedKey()
                                                .getKey()
                                                .equals(goalName))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Missing vanilla goal: " + goalName));
        // Public Paper goal wrappers execute vanilla AI, including its random target selection
        // and real EntityChangeBlockEvent. Never fabricate an event or reset a damaged target.
        boolean activated = false;
        for (int attempt = 0; attempt < 100_000 && !activated; attempt++) {
            activated = goal.shouldActivate();
        }
        if (!activated) {
            throw new IllegalStateException("Vanilla goal did not activate: " + goalName);
        }
        goal.start();
        for (int attempt = 0; attempt < 2048 && !attempted; attempt++) {
            goal.tick();
        }
        goal.stop();
        String outcome =
                "attempted="
                        + attempted
                        + " cancelled="
                        + cancelled
                        + " block="
                        + world.getBlockAt(targetX, 80, 0).getType()
                        + " carrying="
                        + (enderman.getCarriedBlock() != null);
        enderman.remove();
        return outcome;
    }

    String tnt(World world, int sourceX) {
        exploded = false;
        world.getBlockAt(16, 80, 0).setType(Material.WHITE_WOOL);
        world.getBlockAt(18, 80, 0).setType(Material.WHITE_WOOL);
        world.spawn(
                new Location(world, sourceX + 0.5, 80.5, 1.5),
                TNTPrimed.class,
                entity -> {
                    entity.setGravity(false);
                    entity.setVelocity(new Vector());
                    entity.setFuseTicks(2);
                });
        result.accept("pending");
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () ->
                                result.accept(
                                        "exploded="
                                                + exploded
                                                + " inside="
                                                + world.getBlockAt(16, 80, 0).getType()
                                                + " outside="
                                                + world.getBlockAt(18, 80, 0).getType()),
                        20);
        return "pending";
    }

    String projectileTnt(World world, int targetX) {
        attempted = cancelled = primed = false;
        world.getBlockAt(targetX, 80, 0).setType(Material.TNT);
        Arrow arrow =
                world.spawn(
                        new Location(world, targetX + 3.5, 80.5, 0.5),
                        Arrow.class,
                        entity -> {
                            entity.setGravity(false);
                            entity.setFireTicks(200);
                            entity.setVelocity(new Vector(-0.5, 0, 0));
                        });
        result.accept("pending");
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            result.accept(
                                    "attempted="
                                            + attempted
                                            + " cancelled="
                                            + cancelled
                                            + " block="
                                            + world.getBlockAt(targetX, 80, 0).getType()
                                            + " primed="
                                            + primed);
                            arrow.remove();
                        },
                        20);
        return "pending";
    }
}

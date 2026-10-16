package com.lewisgibson.spawnbuildrights.fixture;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.Vector;

/**
 * Real falling blocks, fluids, pistons, dispensers and saplings at the region's edge, x 16 being
 * the last block inside and x 17 the first outside. Each scenario sets up in one tick and reports
 * what the world looks like once the server has had time to run it.
 */
final class OutsideScenarios {
    private static final int FLOOR = 80;
    private static final int ROW = 84;
    // The region's top in the outside-changes test (PaperServerTest: min-y 60, max-y 90). A block
    // above it is outside the region, so growth may place it there even at x <= 16.
    private static final int REGION_MAX_Y = 90;

    private final JavaPlugin plugin;
    private final Consumer<String> result;

    OutsideScenarios(JavaPlugin plugin, Consumer<String> result) {
        this.plugin = plugin;
        this.result = result;
    }

    /**
     * Sand that starts falling outside the region and is carried over it, as a cannon or the End's
     * exit portal would, beside a real sand block that falls inside it and one that falls outside.
     */
    String falling(World world) {
        fill(world, 12, FLOOR, -1, 20, FLOOR, 1, Material.STONE);
        world.getBlockAt(15, ROW, 0).setType(Material.SAND);
        world.getBlockAt(19, ROW, 0).setType(Material.SAND);
        FallingBlock carried =
                world.spawnFallingBlock(
                        new Location(world, 18.5, ROW, -0.5), Material.SAND.createBlockData());
        carried.teleport(new Location(world, 16.5, ROW, -0.5));
        return later(
                40,
                () ->
                        "carried="
                                + world.getBlockAt(16, FLOOR + 1, -1).getType()
                                + " inside="
                                + world.getBlockAt(15, FLOOR + 1, 0).getType()
                                + " outside="
                                + world.getBlockAt(19, FLOOR + 1, 0).getType()
                                + " drops="
                                + drops(world));
    }

    /** Every item on the ground, as its type and which side of the boundary it lies, or none. */
    private static String drops(World world) {
        String drops =
                world.getEntitiesByClass(Item.class).stream()
                        .map(
                                item ->
                                        item.getItemStack().getType()
                                                + (item.getLocation().getBlockX() <= 16
                                                        ? "@inside"
                                                        : "@outside"))
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
        return drops.isEmpty() ? "none" : drops;
    }

    /**
     * Lava poured beside the region, and water poured inside it, on either side of a wall. The lava
     * may not cross in; the water flows inside and on out of the region as normal.
     */
    String fluid(World world) {
        fill(world, 10, FLOOR, -6, 24, FLOOR, 6, Material.STONE);
        fill(world, 10, FLOOR + 1, 0, 24, FLOOR + 2, 0, Material.STONE);
        // The water runs in a closed channel, so it cannot turn towards the floor's nearest edge
        // (water heads for a drop within four blocks rather than spreading evenly).
        fill(world, 9, FLOOR + 1, -4, 24, FLOOR + 1, -4, Material.STONE);
        fill(world, 9, FLOOR + 1, -2, 24, FLOOR + 1, -2, Material.STONE);
        world.getBlockAt(9, FLOOR + 1, -3).setType(Material.STONE, false);
        world.getBlockAt(18, FLOOR + 1, 3).setType(Material.LAVA);
        world.getBlockAt(12, FLOOR + 1, -3).setType(Material.WATER);
        return later(
                100,
                () ->
                        "lavaOutside="
                                + world.getBlockAt(17, FLOOR + 1, 3).getType()
                                + " lavaInside="
                                + world.getBlockAt(16, FLOOR + 1, 3).getType()
                                + " waterInside="
                                + world.getBlockAt(11, FLOOR + 1, -3).getType()
                                + " waterOut="
                                + world.getBlockAt(17, FLOOR + 1, -3).getType());
    }

    /**
     * Pistons outside the region: one pushing a block in, one whose head alone would extend in, a
     * sticky one pulling a block out of it, and the same push and pull further out as controls. A
     * piston inside the region pushing a block within it is the builder's own and works.
     */
    String pistons(World world) {
        piston(world, 10, 7, Material.PISTON);
        world.getBlockAt(9, ROW, 7).setType(Material.STONE);
        world.getBlockAt(11, ROW, 7).setType(Material.REDSTONE_BLOCK);
        piston(world, 18, 5, Material.PISTON);
        world.getBlockAt(17, ROW, 5).setType(Material.STONE);
        piston(world, 17, 3, Material.PISTON);
        piston(world, 24, 5, Material.PISTON);
        world.getBlockAt(23, ROW, 5).setType(Material.STONE);
        piston(world, 18, -5, Material.STICKY_PISTON);
        piston(world, 26, -5, Material.STICKY_PISTON);
        for (int[] power : new int[][] {{19, 5}, {18, 3}, {25, 5}, {19, -5}, {27, -5}}) {
            world.getBlockAt(power[0], ROW, power[1]).setType(Material.REDSTONE_BLOCK);
        }
        // Once the sticky heads are out, put a block in front of each and let them pull it back.
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            world.getBlockAt(16, ROW, -5).setType(Material.STONE);
                            world.getBlockAt(24, ROW, -5).setType(Material.STONE);
                            world.getBlockAt(19, ROW, -5).setType(Material.AIR);
                            world.getBlockAt(27, ROW, -5).setType(Material.AIR);
                        },
                        10);
        return later(
                25,
                () ->
                        "pushIn="
                                + world.getBlockAt(16, ROW, 5).getType()
                                + "/"
                                + world.getBlockAt(17, ROW, 5).getType()
                                + " pushInPiston="
                                + pistonState(world, 18, 5)
                                + " headIn="
                                + world.getBlockAt(16, ROW, 3).getType()
                                + " pushOutside="
                                + world.getBlockAt(22, ROW, 5).getType()
                                + " pullOut="
                                + world.getBlockAt(16, ROW, -5).getType()
                                + " pullPiston="
                                + pistonState(world, 18, -5)
                                + "/"
                                + world.getBlockAt(17, ROW, -5).getType()
                                + " pullOutside="
                                + world.getBlockAt(25, ROW, -5).getType()
                                + " insidePush="
                                + world.getBlockAt(8, ROW, 7).getType()
                                + "/"
                                + world.getBlockAt(9, ROW, 7).getType());
    }

    /** Whether the piston at x, z is extended, or what is there instead of a piston. */
    private static String pistonState(World world, int x, int z) {
        return world.getBlockAt(x, ROW, z).getBlockData() instanceof Piston piston
                ? (piston.isExtended() ? "extended" : "retracted")
                : world.getBlockAt(x, ROW, z).getType().name();
    }

    /**
     * Arrows fired across the boundary at a wall deep inside the region: by a dispenser outside it,
     * by a dispenser inside it, and by a skeleton outside it. The dispensers outside are two and
     * four blocks from the edge, so their arrows are launched outside and fly in. Reports how many
     * arrows from each shooter are inside the region once they have landed, and whether each
     * dispenser fired.
     */
    String projectiles(World world) {
        fill(world, 4, FLOOR, -3, 24, FLOOR, 3, Material.STONE);
        fill(world, 4, FLOOR + 1, -3, 4, FLOOR + 9, 3, Material.STONE);
        Block near = dispenser(world, 18, -2, Material.ARROW);
        Block far = dispenser(world, 20, 2, Material.ARROW);
        Block own = dispenser(world, 14, 0, Material.ARROW);
        world.getBlockAt(19, ROW, -2).setType(Material.REDSTONE_BLOCK);
        world.getBlockAt(21, ROW, 2).setType(Material.REDSTONE_BLOCK);
        world.getBlockAt(15, ROW, 0).setType(Material.REDSTONE_BLOCK);
        Skeleton skeleton =
                world.spawn(
                        new Location(world, 22.5, FLOOR + 1, 0.5, 90, 0),
                        Skeleton.class,
                        mob -> mob.setAI(false));
        skeleton.launchProjectile(Arrow.class, new Vector(-1.6, 0.1, 0));
        return later(
                60,
                () ->
                        "fired="
                                + (empty(near) && empty(far) && empty(own))
                                + " outsideDispensersInside="
                                + arrowsInside(world, near, far)
                                + " outsideDispensersLeft="
                                + arrowsFrom(world, near, far)
                                + " ownInside="
                                + arrowsInside(world, own)
                                + " skeletonInside="
                                + world.getEntitiesByClass(Arrow.class).stream()
                                        .filter(arrow -> arrow.getShooter() instanceof Skeleton)
                                        .filter(arrow -> arrow.getLocation().getBlockX() <= 16)
                                        .count());
    }

    private static long arrowsFrom(World world, Block... dispensers) {
        return world.getEntitiesByClass(Arrow.class).stream()
                .filter(arrow -> firedBy(arrow, dispensers))
                .count();
    }

    private static long arrowsInside(World world, Block... dispensers) {
        return world.getEntitiesByClass(Arrow.class).stream()
                .filter(arrow -> firedBy(arrow, dispensers))
                .filter(arrow -> arrow.getLocation().getBlockX() <= 16)
                .count();
    }

    private static boolean firedBy(Arrow arrow, Block... dispensers) {
        return arrow.getShooter() instanceof BlockProjectileSource source
                && java.util.Arrays.asList(dispensers).contains(source.getBlock());
    }

    private static boolean empty(Block dispenser) {
        return dispenser.getState() instanceof Dispenser state && state.getInventory().isEmpty();
    }

    /**
     * A red mushroom on mycelium one block outside the region, bone-mealed until it grows into a
     * huge mushroom. Its cap would reach into the region; it grows without those blocks.
     */
    String mushroom(World world) {
        fill(world, 12, FLOOR + 1, -8, 24, FLOOR + 12, -2, Material.AIR);
        fill(world, 12, FLOOR, -8, 24, FLOOR, -2, Material.MYCELIUM);
        Block mushroom = world.getBlockAt(18, FLOOR + 1, -5);
        mushroom.setType(Material.RED_MUSHROOM);
        for (int attempt = 0;
                attempt < 60 && mushroom.getType() == Material.RED_MUSHROOM;
                attempt++) {
            mushroom.applyBoneMeal(BlockFace.UP);
        }
        int inside = 0;
        int outside = 0;
        // Where any block inside the region is, so a failure says what grew there.
        StringBuilder where = new StringBuilder();
        for (int x = 12; x <= 24; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 12; y++) {
                for (int z = -8; z <= -2; z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.RED_MUSHROOM_BLOCK || type == Material.MUSHROOM_STEM) {
                        if (x <= 16 && y <= REGION_MAX_Y) {
                            inside++;
                            where.append(' ').append(x).append(',').append(y).append(',').append(z);
                        } else {
                            outside++;
                        }
                    }
                }
            }
        }
        return "grown="
                + (mushroom.getType() == Material.MUSHROOM_STEM)
                + " inside="
                + inside
                + " outside="
                + (outside > 0)
                + (inside > 0 ? " at" + where : "");
    }

    /** A dispenser of lava facing into the region from just outside it, and one further out. */
    String dispensers(World world) {
        dispenser(world, 17);
        dispenser(world, 24);
        world.getBlockAt(18, ROW, 0).setType(Material.REDSTONE_BLOCK);
        world.getBlockAt(25, ROW, 0).setType(Material.REDSTONE_BLOCK);
        return later(
                8,
                () ->
                        "inside="
                                + world.getBlockAt(16, ROW, 0).getType()
                                + " kept="
                                + holdsLava(world, 17)
                                + " outside="
                                + world.getBlockAt(23, ROW, 0).getType()
                                + " used="
                                + !holdsLava(world, 24));
    }

    /**
     * An oak sapling one block outside the region, bone-mealed until it grows. Its canopy would
     * reach two blocks into the region; every block of it must stay outside.
     */
    String tree(World world) {
        // The world is kept between runs, and the run with growth off leaves its tree standing
        // inside the region; clear the counted volume first, as the mushroom scenario does, or a
        // rerun counts last run's leaves.
        fill(world, 12, FLOOR + 1, -4, 24, FLOOR + 12, 4, Material.AIR);
        fill(world, 12, FLOOR, -4, 22, FLOOR, 4, Material.GRASS_BLOCK);
        Block sapling = world.getBlockAt(18, FLOOR + 1, 0);
        sapling.setType(Material.OAK_SAPLING);
        for (int attempt = 0;
                attempt < 40 && sapling.getType() == Material.OAK_SAPLING;
                attempt++) {
            sapling.applyBoneMeal(BlockFace.UP);
        }
        int inside = 0;
        int outside = 0;
        for (int x = 12; x <= 24; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 12; y++) {
                for (int z = -4; z <= 4; z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.OAK_LOG || type == Material.OAK_LEAVES) {
                        if (x <= 16 && y <= REGION_MAX_Y) {
                            inside++;
                        } else {
                            outside++;
                        }
                    }
                }
            }
        }
        return "grown="
                + (sapling.getType() == Material.OAK_LOG)
                + " inside="
                + inside
                + " outside="
                + (outside > 0);
    }

    private static void piston(World world, int x, int z, Material type) {
        Directional data = (Directional) type.createBlockData();
        data.setFacing(BlockFace.WEST);
        world.getBlockAt(x, ROW, z).setBlockData(data);
    }

    private static void dispenser(World world, int x) {
        dispenser(world, x, 0, Material.LAVA_BUCKET);
    }

    private static Block dispenser(World world, int x, int z, Material item) {
        Directional data = (Directional) Material.DISPENSER.createBlockData();
        data.setFacing(BlockFace.WEST);
        Block block = world.getBlockAt(x, ROW, z);
        block.setBlockData(data);
        ((Dispenser) block.getState()).getInventory().addItem(new ItemStack(item));
        return block;
    }

    private static boolean holdsLava(World world, int x) {
        return world.getBlockAt(x, ROW, 0).getState() instanceof Dispenser dispenser
                && dispenser.getInventory().contains(Material.LAVA_BUCKET);
    }

    private String later(long ticks, Supplier<String> report) {
        result.accept("pending");
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            String outcome = report.get();
                            plugin.getLogger().info("Outside scenario: " + outcome);
                            result.accept(outcome);
                        },
                        ticks);
        return "pending";
    }

    private static void fill(
            World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }
}

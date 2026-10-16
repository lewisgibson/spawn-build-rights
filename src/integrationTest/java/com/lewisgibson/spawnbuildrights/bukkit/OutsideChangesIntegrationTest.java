package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.FallingBlockMock;

/**
 * Changes that start outside the region and would land inside it: falling blocks, fluid flow,
 * pistons, dispensers and growth. Each is stopped at the boundary while the same change starting
 * inside the region, or staying outside it, goes ahead, and each has its own switch.
 */
class OutsideChangesIntegrationTest {
    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;
    private World end;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        end = server.addSimpleWorld("world_the_end");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        plugin.getConfig().set("region.center-x", 0);
        plugin.getConfig().set("region.center-z", 0);
        plugin.getConfig().set("region.radius", 16);
        plugin.getConfig().set("region.min-y", 60);
        plugin.getConfig().set("region.max-y", 90);
        reload();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void fallingBlocksFromOutsideCannotLandInside() {
        // Launched in from beside the region, and arriving from the End's exit portal.
        assertThat(land(new Location(world, 17.5, 80, 0.5), 16).isCancelled()).isTrue();
        assertThat(land(new Location(end, 0.5, 80, 0.5), 0).isCancelled()).isTrue();
        // A builder's own sand settles, and sand outside lands outside.
        assertThat(land(new Location(world, 16.5, 80, 0.5), 16).isCancelled()).isFalse();
        assertThat(land(new Location(world, 18.5, 80, 0.5), 17).isCancelled()).isFalse();
        // Starting to fall fires before the falling block has an origin, at the block itself, and
        // is never stopped, wherever the block is.
        FallingBlock starting = falling(null, Material.SAND);
        assertThat(call(landing(starting, 16)).isCancelled()).isFalse();

        plugin.getConfig().set("protection.falling-blocks", false);
        reload();
        assertThat(land(new Location(world, 17.5, 80, 0.5), 16).isCancelled()).isFalse();
    }

    @Test
    void aFallingBlockFromOutsideDropsAsItsItemWhereItWouldHaveLanded() {
        assertThat(land(new Location(world, 17.5, 80, 0.5), 16).isCancelled()).isTrue();
        assertThat(items()).containsExactly(Material.SAND);

        // An anvil drops an anvil, a falling block that does not drop, or one whose drop was
        // cancelled, drops nothing, and neither does anything with entity drops off.
        clearItems();
        FallingBlock anvil = falling(new Location(world, 17.5, 80, 0.5), Material.ANVIL);
        assertThat(call(landing(anvil, 16)).isCancelled()).isTrue();
        assertThat(items()).containsExactly(Material.ANVIL);
        clearItems();
        FallingBlock silent = falling(new Location(world, 17.5, 80, 0.5), Material.SAND);
        silent.setDropItem(false);
        assertThat(call(landing(silent, 16)).isCancelled()).isTrue();
        FallingBlock broken = falling(new Location(world, 17.5, 80, 0.5), Material.SAND);
        broken.setCancelDrop(true);
        assertThat(call(landing(broken, 16)).isCancelled()).isTrue();
        world.setGameRule(GameRules.ENTITY_DROPS, false);
        assertThat(land(new Location(world, 17.5, 80, 0.5), 16).isCancelled()).isTrue();
        assertThat(items()).isEmpty();
        world.setGameRule(GameRules.ENTITY_DROPS, true);

        // A block that has no item, such as fire set falling by a command, drops nothing.
        FallingBlock fire = falling(new Location(world, 17.5, 80, 0.5), Material.FIRE);
        assertThat(call(landing(fire, 16)).isCancelled()).isTrue();
        assertThat(items()).isEmpty();

        // A landing that is allowed drops nothing extra.
        assertThat(land(new Location(world, 16.5, 80, 0.5), 16).isCancelled()).isFalse();
        assertThat(items()).isEmpty();
    }

    @Test
    void aProjectileFromADispenserOutsideIsRemovedAsItEnters() {
        // Launched from beside the region straight into it: the launch itself is cancelled.
        Arrow straightIn = arrow(new Location(world, 17.2, 70.5, 0.5), new Vector(-1.1, 0, 0));
        straightIn.setShooter(dispenserAt(18));
        assertThat(call(new ProjectileLaunchEvent(straightIn)).isCancelled()).isTrue();

        // Launched away from the region, or from a dispenser inside it: it flies.
        Arrow away = arrow(new Location(world, 18.8, 70.5, 0.5), new Vector(1.1, 0, 0));
        away.setShooter(dispenserAt(18));
        assertThat(call(new ProjectileLaunchEvent(away)).isCancelled()).isFalse();
        Arrow own = arrow(new Location(world, 14.2, 70.5, 0.5), new Vector(-1.1, 0, 0));
        own.setShooter(dispenserAt(15));
        assertThat(call(new ProjectileLaunchEvent(own)).isCancelled()).isFalse();

        // Hitting a block or an entity inside the region from a dispenser outside: removed.
        Arrow hitBlock = arrow(new Location(world, 16.5, 70.5, 0.5), new Vector());
        hitBlock.setShooter(dispenserAt(20));
        assertThat(call(hit(hitBlock, null, world.getBlockAt(15, 70, 0))).isCancelled()).isTrue();
        assertThat(hitBlock.isValid()).isFalse();
        Arrow hitEntity = arrow(new Location(world, 16.5, 70.5, 0.5), new Vector());
        hitEntity.setShooter(dispenserAt(20));
        Cow cow = world.spawn(new Location(world, 15.5, 70, 0.5), Cow.class);
        assertThat(call(hit(hitEntity, cow, null)).isCancelled()).isTrue();

        // A hit outside the region, a dispenser inside it, a mob's arrow or one with no shooter
        // all hit as normal.
        Arrow outsideHit = arrow(new Location(world, 18.5, 70.5, 0.5), new Vector());
        outsideHit.setShooter(dispenserAt(20));
        assertThat(call(hit(outsideHit, null, world.getBlockAt(17, 70, 0))).isCancelled())
                .isFalse();
        assertThat(call(hit(outsideHit, null, null)).isCancelled()).isFalse();
        Arrow inner = arrow(new Location(world, 14.5, 70.5, 0.5), new Vector());
        inner.setShooter(dispenserAt(15));
        assertThat(call(hit(inner, null, world.getBlockAt(10, 70, 0))).isCancelled()).isFalse();
        Skeleton skeleton = world.spawn(new Location(world, 20.5, 70, 0.5), Skeleton.class);
        Arrow shot = arrow(new Location(world, 17.2, 70.5, 0.5), new Vector(-1.1, 0, 0));
        shot.setShooter(skeleton);
        assertThat(call(new ProjectileLaunchEvent(shot)).isCancelled()).isFalse();
        assertThat(call(hit(shot, null, world.getBlockAt(15, 70, 0))).isCancelled()).isFalse();
        Arrow stray = arrow(new Location(world, 16.5, 70.5, 0.5), new Vector());
        assertThat(call(hit(stray, null, world.getBlockAt(15, 70, 0))).isCancelled()).isFalse();

        plugin.getConfig().set("protection.dispensers", false);
        reload();
        Arrow off = arrow(new Location(world, 17.2, 70.5, 0.5), new Vector(-1.1, 0, 0));
        off.setShooter(dispenserAt(18));
        assertThat(call(new ProjectileLaunchEvent(off)).isCancelled()).isFalse();
        assertThat(call(hit(off, null, world.getBlockAt(15, 70, 0))).isCancelled()).isFalse();
    }

    @Test
    void fluidFromOutsideCannotFlowIn() {
        assertThat(flow(17, 16).isCancelled()).isTrue();
        assertThat(flow(16, 15).isCancelled()).isFalse();
        assertThat(flow(16, 17).isCancelled()).isFalse();
        assertThat(flow(18, 17).isCancelled()).isFalse();

        plugin.getConfig().set("protection.fluid-flow", false);
        reload();
        assertThat(flow(17, 16).isCancelled()).isFalse();
    }

    @Test
    void pistonsOutsideCannotReachIn() {
        // Pushing a block in from outside, and extending the head itself into the region.
        assertThat(extend(18, List.of(17)).isCancelled()).isTrue();
        assertThat(extend(17, List.of()).isCancelled()).isTrue();
        // Pushing a block that is already inside further in.
        assertThat(extend(18, List.of(17, 16)).isCancelled()).isTrue();
        // A piston well outside, and a piston inside the region pushing out.
        assertThat(extend(20, List.of(19)).isCancelled()).isFalse();
        Block inner = piston(16, BlockFace.EAST);
        assertThat(
                        call(new BlockPistonExtendEvent(inner, blocks(17), BlockFace.EAST))
                                .isCancelled())
                .isFalse();

        // A sticky piston pulling a block out of the region, or pulling one in.
        assertThat(retract(18, BlockFace.WEST, List.of(16)).isCancelled()).isTrue();
        assertThat(retract(15, BlockFace.WEST, List.of(13)).isCancelled()).isFalse();
        assertThat(retract(14, BlockFace.EAST, List.of(16)).isCancelled()).isFalse();
        Block east = piston(-18, BlockFace.EAST);
        assertThat(
                        call(new BlockPistonRetractEvent(east, blocks(-16), BlockFace.WEST))
                                .isCancelled())
                .isTrue();
        assertThat(retract(20, BlockFace.WEST, List.of(18)).isCancelled()).isFalse();
        // A retracting head that is inside the region is not itself a reach in.
        assertThat(retract(17, BlockFace.WEST, List.of()).isCancelled()).isFalse();

        plugin.getConfig().set("protection.pistons", false);
        reload();
        assertThat(extend(18, List.of(17)).isCancelled()).isFalse();
    }

    @Test
    void aPistonWithoutAFacingIsLeftAlone() {
        Block block = world.getBlockAt(18, 70, 0);
        block.setType(Material.STONE);
        assertThat(
                        call(new BlockPistonExtendEvent(block, blocks(17), BlockFace.WEST))
                                .isCancelled())
                .isFalse();
    }

    @Test
    void dispensersOutsideCannotDispenseIn() {
        assertThat(dispense(Material.DISPENSER, 17, BlockFace.WEST).isCancelled()).isTrue();
        assertThat(dispense(Material.DISPENSER, 17, BlockFace.EAST).isCancelled()).isFalse();
        assertThat(dispense(Material.DISPENSER, 16, BlockFace.WEST).isCancelled()).isFalse();
        assertThat(dispense(Material.DROPPER, 17, BlockFace.WEST).isCancelled()).isFalse();

        plugin.getConfig().set("protection.dispensers", false);
        reload();
        assertThat(dispense(Material.DISPENSER, 17, BlockFace.WEST).isCancelled()).isFalse();
    }

    @Test
    void growthOutsideKeepsOnlyItsOutsideBlocks() {
        BlockState inside = world.getBlockAt(16, 75, 0).getState();
        BlockState outside = world.getBlockAt(18, 75, 0).getState();
        List<BlockState> tree = new ArrayList<>(List.of(inside, outside));
        StructureGrowEvent grow =
                call(
                        new StructureGrowEvent(
                                new Location(world, 17, 70, 0), TreeType.TREE, true, null, tree));
        assertThat(grow.getBlocks()).containsExactly(outside);

        List<BlockState> own = new ArrayList<>(List.of(inside, outside));
        call(
                new StructureGrowEvent(
                        new Location(world, 16, 70, 0), TreeType.TREE, true, null, own));
        assertThat(own).containsExactly(inside, outside);

        List<BlockState> meal = new ArrayList<>(List.of(inside, outside));
        call(new BlockFertilizeEvent(world.getBlockAt(17, 70, 0), null, meal));
        assertThat(meal).containsExactly(outside);

        plugin.getConfig().set("protection.growth", false);
        reload();
        List<BlockState> off = new ArrayList<>(List.of(inside, outside));
        call(new BlockFertilizeEvent(world.getBlockAt(17, 70, 0), null, off));
        assertThat(off).containsExactly(inside, outside);
    }

    private EntityChangeBlockEvent land(Location origin, int x) {
        return call(landing(falling(origin, Material.SAND), x));
    }

    /**
     * A falling block that started at origin, or one not yet in the world when origin is null.
     * MockBukkit does not record an entity's origin, so the mock reports the one the server would
     * have.
     */
    private FallingBlock falling(Location origin, Material type) {
        FallingBlockMock falling =
                new FallingBlockMock(server, UUID.randomUUID()) {
                    @Override
                    public Location getOrigin() {
                        return origin == null ? null : origin.clone();
                    }
                };
        falling.setLocation(origin == null ? new Location(world, 0.5, 70, 0.5) : origin.clone());
        falling.setBlockData(type.createBlockData());
        return falling;
    }

    private EntityChangeBlockEvent landing(FallingBlock falling, int x) {
        ((FallingBlockMock) falling).setLocation(new Location(world, x + 0.5, 70, 0.5));
        return new EntityChangeBlockEvent(
                falling, world.getBlockAt(x, 70, 0), falling.getBlockData());
    }

    private List<Material> items() {
        return world.getEntitiesByClass(Item.class).stream()
                .map(item -> item.getItemStack().getType())
                .toList();
    }

    private void clearItems() {
        world.getEntitiesByClass(Item.class).forEach(Item::remove);
    }

    private static ProjectileHitEvent hit(Projectile projectile, Entity entity, Block block) {
        return new ProjectileHitEvent(projectile, entity, block, null);
    }

    private Arrow arrow(Location at, Vector velocity) {
        Arrow arrow = world.spawn(at, Arrow.class);
        arrow.setVelocity(velocity);
        return arrow;
    }

    /** A dispenser at x as the shooter of a projectile, the way the server records one. */
    private BlockProjectileSource dispenserAt(int x) {
        Block block = world.getBlockAt(x, 70, 0);
        block.setType(Material.DISPENSER);
        return new BlockProjectileSource() {
            @Override
            public Block getBlock() {
                return block;
            }

            @Override
            public <T extends Projectile> T launchProjectile(
                    Class<? extends T> projectile,
                    Vector velocity,
                    java.util.function.Consumer<? super T> function) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private BlockFromToEvent flow(int fromX, int toX) {
        return call(
                new BlockFromToEvent(world.getBlockAt(fromX, 70, 0), world.getBlockAt(toX, 70, 0)));
    }

    private BlockPistonExtendEvent extend(int pistonX, List<Integer> moved) {
        Block piston = piston(pistonX, BlockFace.WEST);
        return call(
                new BlockPistonExtendEvent(
                        piston,
                        blocks(moved.stream().mapToInt(Integer::intValue).toArray()),
                        BlockFace.WEST));
    }

    private BlockPistonRetractEvent retract(int pistonX, BlockFace facing, List<Integer> pulled) {
        Block piston = piston(pistonX, facing);
        return call(
                new BlockPistonRetractEvent(
                        piston,
                        blocks(pulled.stream().mapToInt(Integer::intValue).toArray()),
                        facing.getOppositeFace()));
    }

    private Block piston(int x, BlockFace facing) {
        Block block = world.getBlockAt(x, 70, 0);
        Directional data = (Directional) Material.STICKY_PISTON.createBlockData();
        data.setFacing(facing);
        block.setBlockData(data);
        return block;
    }

    private List<Block> blocks(int... xs) {
        List<Block> blocks = new ArrayList<>();
        for (int x : xs) {
            blocks.add(world.getBlockAt(x, 70, 0));
        }
        return blocks;
    }

    private BlockDispenseEvent dispense(Material type, int x, BlockFace facing) {
        Block block = world.getBlockAt(x, 70, 0);
        Directional data = (Directional) type.createBlockData();
        data.setFacing(facing);
        block.setBlockData(data);
        return call(
                new BlockDispenseEvent(block, new ItemStack(Material.LAVA_BUCKET), new Vector()));
    }

    private <T extends org.bukkit.event.Event> T call(T event) {
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void reload() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }
}

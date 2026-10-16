package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Functions;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.sign.Side;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.FallingBlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The edges of each protection: the player events that need build rights, and for every
 * environmental protection the cases it must leave alone - outside the region, switched off, or not
 * the kind of change it covers.
 */
@DisplayName("SpawnBuildRights protection boundaries (integration)")
class ProtectionBoundariesIntegrationTest {

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;
    private PlayerMock newcomer;
    private PlayerMock builder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        plugin.getConfig().set("region.center-x", 0);
        plugin.getConfig().set("region.center-z", 0);
        plugin.getConfig().set("region.radius", 16);
        reload();
        newcomer = server.addPlayer("Newcomer");
        builder = server.addPlayer("Builder");
        builder.addAttachment(plugin, "spawnbuildrights.build", true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("placing, editing a sign and scooping a liquid need build rights")
    void blockEditsNeedBuildRights() {
        for (PlayerMock player : List.of(newcomer, builder)) {
            boolean denied = player == newcomer;
            Block block = world.getBlockAt(0, 70, 0);
            assertThat(
                            call(new BlockPlaceEvent(
                                            block,
                                            block.getState(),
                                            block.getRelative(BlockFace.DOWN),
                                            new ItemStack(Material.STONE),
                                            player,
                                            true,
                                            EquipmentSlot.HAND))
                                    .isCancelled())
                    .isEqualTo(denied);
            assertThat(
                            call(new SignChangeEvent(
                                            block,
                                            player,
                                            List.of(Component.text("hi")),
                                            Side.FRONT))
                                    .isCancelled())
                    .isEqualTo(denied);
            assertThat(
                            call(new PlayerBucketFillEvent(
                                            player,
                                            block,
                                            block,
                                            BlockFace.UP,
                                            Material.BUCKET,
                                            new ItemStack(Material.BUCKET),
                                            EquipmentSlot.HAND))
                                    .isCancelled())
                    .isEqualTo(denied);
        }
    }

    @Test
    @DisplayName("hanging and entity placement need build rights, and a dispenser's is let through")
    void placementNeedsBuildRights() {
        Block block = world.getBlockAt(0, 70, 0);
        ItemFrame frame = world.spawn(new Location(world, 0, 70, 0), ItemFrame.class);
        Boat boat = world.spawn(new Location(world, 0, 70, 0), Boat.class);
        assertThat(hang(frame, newcomer, block).isCancelled()).isTrue();
        assertThat(hang(frame, builder, block).isCancelled()).isFalse();
        assertThat(hang(frame, null, block).isCancelled()).isFalse();
        assertThat(place(boat, newcomer, block).isCancelled()).isTrue();
        assertThat(place(boat, builder, block).isCancelled()).isFalse();
        assertThat(place(boat, null, block).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a left click on a sign with a dye is not dyeing it")
    void leftClickIsNotDyeing() {
        Block sign = world.getBlockAt(0, 70, 0);
        sign.setType(Material.OAK_SIGN);
        PlayerInteractEvent click =
                call(
                        new PlayerInteractEvent(
                                newcomer,
                                Action.LEFT_CLICK_BLOCK,
                                new ItemStack(Material.RED_DYE),
                                sign,
                                BlockFace.UP,
                                EquipmentSlot.HAND));
        assertThat(click.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("hostile trampling is stopped only inside, only for hostile mobs, only when on")
    void tramplingBoundaries() {
        Block inside = world.getBlockAt(0, 70, 0);
        inside.setType(Material.FARMLAND);
        Block outside = world.getBlockAt(17, 70, 0);
        outside.setType(Material.FARMLAND);
        Zombie zombie = world.spawn(new Location(world, 0, 71, 0), Zombie.class);
        Cow cow = world.spawn(new Location(world, 0, 71, 0), Cow.class);
        assertThat(call(new EntityInteractEvent(zombie, inside)).isCancelled()).isTrue();
        assertThat(call(new EntityInteractEvent(zombie, outside)).isCancelled()).isFalse();
        assertThat(call(new EntityInteractEvent(cow, inside)).isCancelled()).isFalse();
        plugin.getConfig().set("protection.hostile-mob-changes", false);
        reload();
        assertThat(call(new EntityInteractEvent(zombie, inside)).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("an ender dragon's block change is a hostile change; a mob's arrow off is not")
    void dragonAndMobProjectiles() {
        EnderDragon dragon = world.spawn(new Location(world, 0, 80, 0), EnderDragon.class);
        assertThat(change(dragon, 0).isCancelled()).isTrue();
        Arrow arrow = world.spawn(new Location(world, 18, 70, 0), Arrow.class);
        arrow.setShooter(world.spawn(new Location(world, 20, 70, 0), Cow.class));
        assertThat(change(arrow, 0).isCancelled()).isFalse();
        plugin.getConfig().set("protection.hostile-mob-changes", false);
        reload();
        assertThat(change(dragon, 0).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("lava lighting a mob's fire is still a mob's fire with fire-damage off")
    void mobIgnitionWithFireDamageOff() {
        plugin.getConfig().set("protection.fire-damage", false);
        reload();
        Wither wither = world.spawn(new Location(world, 20, 70, 0), Wither.class);
        BlockIgniteEvent event =
                call(
                        new BlockIgniteEvent(
                                world.getBlockAt(0, 70, 0),
                                BlockIgniteEvent.IgniteCause.LAVA,
                                wither));
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a builder's fire charge in the off hand lights a fire")
    void builderFireChargeInOffHand() {
        builder.getInventory().setItemInOffHand(new ItemStack(Material.FIRE_CHARGE));
        BlockIgniteEvent event =
                call(
                        new BlockIgniteEvent(
                                world.getBlockAt(0, 70, 0),
                                BlockIgniteEvent.IgniteCause.FIREBALL,
                                builder));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a falling block of air from outside drops nothing")
    void fallingAirDropsNothing() {
        FallingBlockMock falling =
                new FallingBlockMock(server, UUID.randomUUID()) {
                    @Override
                    public Location getOrigin() {
                        return new Location(world, 17.5, 80, 0.5);
                    }
                };
        falling.setLocation(new Location(world, 0.5, 70, 0.5));
        falling.setBlockData(Material.AIR.createBlockData());
        EntityChangeBlockEvent landing =
                call(
                        new EntityChangeBlockEvent(
                                falling, world.getBlockAt(0, 70, 0), falling.getBlockData()));
        assertThat(landing.isCancelled()).isTrue();
        assertThat(world.getEntitiesByClass(org.bukkit.entity.Item.class)).isEmpty();
    }

    @Test
    @DisplayName("a dispensed snowball is watched on through the ticks it stays outside")
    void aSnowballIsWatchedUntilItEnters() {
        Snowball snowball = world.spawn(new Location(world, 24.5, 70.5, 0.5), Snowball.class);
        snowball.setVelocity(new Vector(-0.5, 0, 0));
        snowball.setShooter(dispenserAt(26));
        assertThat(call(new ProjectileLaunchEvent(snowball)).isCancelled()).isFalse();
        server.getScheduler().performTicks(3);
        assertThat(snowball.isValid()).isTrue();
        snowball.teleport(new Location(world, 17.3, 70.5, 0.5));
        server.getScheduler().performTicks(1);
        assertThat(snowball.isValid()).isFalse();
    }

    @Test
    @DisplayName("a dispensed projectile hitting an entity outside the region hits it")
    void hittingAnEntityOutside() {
        Snowball snowball = world.spawn(new Location(world, 19.5, 70.5, 0.5), Snowball.class);
        snowball.setShooter(dispenserAt(26));
        Cow cow = world.spawn(new Location(world, 18.5, 70, 0.5), Cow.class);
        assertThat(call(new ProjectileHitEvent(snowball, cow, null, null)).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("display damage: block explosions protected, ordinary causes and switched off not")
    void displayDamageBoundaries() {
        ArmorStand stand = world.spawn(new Location(world, 0, 70, 0), ArmorStand.class);
        DamageSource none = DamageSource.builder(DamageType.EXPLOSION).build();
        assertThat(damage(stand, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, none).isCancelled())
                .isTrue();
        DamageSource fall = DamageSource.builder(DamageType.FALL).build();
        assertThat(damage(stand, EntityDamageEvent.DamageCause.FALL, fall).isCancelled()).isFalse();
        plugin.getConfig().set("protection.display-damage", false);
        reload();
        assertThat(damage(stand, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, none).isCancelled())
                .isFalse();
    }

    @Test
    @DisplayName("hanging displays: outside the region, switched off, and a remover with no source")
    void hangingBoundaries() {
        ItemFrame outside = world.spawn(new Location(world, 17, 70, 0), ItemFrame.class);
        HangingBreakEvent outsideExplosion =
                call(new HangingBreakEvent(outside, HangingBreakEvent.RemoveCause.EXPLOSION));
        assertThat(outsideExplosion.isCancelled()).isFalse();

        ItemFrame frame = world.spawn(new Location(world, 0, 70, 0), ItemFrame.class);
        DamageSource sourceless = DamageSource.builder(DamageType.GENERIC).build();
        // No causing entity: the remover itself is judged, here a player without build rights.
        assertThat(call(new HangingBreakByEntityEvent(frame, newcomer, sourceless)).isCancelled())
                .isTrue();
        Zombie zombie = world.spawn(new Location(world, 1, 70, 0), Zombie.class);
        assertThat(call(new HangingBreakByEntityEvent(frame, zombie, sourceless)).isCancelled())
                .isTrue();

        plugin.getConfig().set("protection.display-damage", false);
        reload();
        assertThat(
                        call(new HangingBreakEvent(frame, HangingBreakEvent.RemoveCause.EXPLOSION))
                                .isCancelled())
                .isFalse();
        assertThat(call(new HangingBreakByEntityEvent(frame, zombie, sourceless)).isCancelled())
                .isFalse();
    }

    @Test
    @DisplayName("a portal exit with no destination, or one with no world, is left alone")
    void portalExitWithoutADestination() {
        Projectile arrow = world.spawn(new Location(world, 0, 70, 0), Arrow.class);
        Vector velocity = new Vector();
        EntityPortalExitEvent noDestination =
                call(
                        new EntityPortalExitEvent(
                                arrow, arrow.getLocation(), null, velocity, velocity));
        assertThat(noDestination.isCancelled()).isFalse();
        EntityPortalExitEvent noWorld =
                call(
                        new EntityPortalExitEvent(
                                arrow,
                                arrow.getLocation(),
                                new Location(null, 0, 70, 0),
                                velocity,
                                velocity));
        assertThat(noWorld.isCancelled()).isFalse();
        assertThat(arrow.isValid()).isTrue();
    }

    private HangingPlaceEvent hang(ItemFrame frame, PlayerMock player, Block block) {
        return call(new HangingPlaceEvent(frame, player, block, BlockFace.UP, EquipmentSlot.HAND));
    }

    private EntityPlaceEvent place(Entity entity, PlayerMock player, Block block) {
        return call(new EntityPlaceEvent(entity, player, block, BlockFace.UP, EquipmentSlot.HAND));
    }

    private EntityChangeBlockEvent change(Entity entity, int x) {
        return call(
                new EntityChangeBlockEvent(
                        entity, world.getBlockAt(x, 70, 0), Material.AIR.createBlockData()));
    }

    // Only the constructor taking DamageModifier maps is not marked for removal.
    @SuppressWarnings("deprecation")
    private EntityDamageEvent damage(
            Entity entity, EntityDamageEvent.DamageCause cause, DamageSource source) {
        return call(
                new EntityDamageEvent(
                        entity,
                        cause,
                        source,
                        new EnumMap<>(Map.of(EntityDamageEvent.DamageModifier.BASE, 10.0)),
                        new EnumMap<>(
                                Map.of(
                                        EntityDamageEvent.DamageModifier.BASE,
                                        Functions.constant(-0.0)))));
    }

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

    private <T extends Event & Cancellable> T call(T event) {
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void reload() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }
}

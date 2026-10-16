package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Functions;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class EnvironmentalProtectionIntegrationTest {
    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
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
    void protectsAffectedBlocksRatherThanExplosionCentre() {
        Block inside = world.getBlockAt(16, 60, 0);
        List<Block> outside =
                List.of(
                        world.getBlockAt(17, 60, 0),
                        world.getBlockAt(0, 59, 0),
                        world.getBlockAt(0, 91, 0),
                        server.addSimpleWorld("other").getBlockAt(0, 60, 0));
        List<Block> blocks = new ArrayList<>(outside);
        blocks.add(inside);
        Creeper creeper = world.spawn(new Location(world, 18, 60, 0), Creeper.class);
        EntityExplodeEvent explosion =
                new EntityExplodeEvent(
                        creeper, creeper.getLocation(), blocks, 0.5f, ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(explosion);
        assertThat(explosion.blockList()).containsExactlyElementsOf(outside);
        assertThat(explosion.isCancelled()).isFalse();
        assertThat(explosion.getYield()).isEqualTo(0.5f);

        BlockExplodeEvent anchor =
                new BlockExplodeEvent(
                        outside.getFirst(),
                        outside.getFirst().getState(),
                        new ArrayList<>(List.of(inside, outside.getFirst())),
                        1.0f,
                        ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(anchor);
        assertThat(anchor.blockList()).containsExactly(outside.getFirst());
    }

    @Test
    void hostileDamageUsesTargetBlockAndLeavesUsefulMobBehaviourAlone() {
        Wither wither = world.spawn(new Location(world, 18, 70, 0), Wither.class);
        assertThat(change(wither, 16).isCancelled()).isTrue();
        assertThat(change(wither, 17).isCancelled()).isFalse();
        assertThat(change(world.spawn(new Location(world, 0, 70, 0), Sheep.class), 0).isCancelled())
                .isFalse();
        assertThat(
                        change(world.spawn(new Location(world, 0, 70, 0), Villager.class), 0)
                                .isCancelled())
                .isFalse();
    }

    @Test
    void endermanPickupAndPlacementUseTheTargetBlockBounds() {
        Enderman enderman = world.spawn(new Location(world, 18, 70, 0), Enderman.class);
        for (Material destination : List.of(Material.AIR, Material.DIRT)) {
            for (int target : List.of(16, 17)) {
                EntityChangeBlockEvent event =
                        new EntityChangeBlockEvent(
                                enderman,
                                world.getBlockAt(target, 70, 0),
                                destination.createBlockData());
                server.getPluginManager().callEvent(event);
                assertThat(event.isCancelled()).isEqualTo(target == 16);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TNTPrimeEvent.PrimeCause.class)
    void tntPrimingUsesItsOwnCauseWithoutDisablingOrdinaryContraptions(
            TNTPrimeEvent.PrimeCause cause) {
        Block target = world.getBlockAt(16, 70, 0);
        Entity projectile = world.spawn(new Location(world, 18, 70, 0), SmallFireball.class);
        boolean protectedCause =
                cause == TNTPrimeEvent.PrimeCause.PROJECTILE
                        || cause == TNTPrimeEvent.PrimeCause.FIRE
                        || cause == TNTPrimeEvent.PrimeCause.EXPLOSION;
        TNTPrimeEvent event = new TNTPrimeEvent(target, cause, projectile, null);
        server.getPluginManager().callEvent(event);
        assertThat(event.isCancelled()).isEqualTo(protectedCause);
        TNTPrimeEvent outside =
                new TNTPrimeEvent(world.getBlockAt(17, 70, 0), cause, projectile, null);
        server.getPluginManager().callEvent(outside);
        assertThat(outside.isCancelled()).isFalse();

        String setting =
                switch (cause) {
                    case PROJECTILE -> "hostile-mob-changes";
                    case FIRE -> "fire-damage";
                    case EXPLOSION -> "explosions";
                    default -> "display-damage";
                };
        // Turning off only the corresponding shield must restore that cause.
        plugin.getConfig().set("protection." + setting, false);
        reload();
        TNTPrimeEvent disabled = new TNTPrimeEvent(target, cause, projectile, null);
        server.getPluginManager().callEvent(disabled);
        assertThat(disabled.isCancelled()).isFalse();
    }

    @Test
    void directTntIgnitionRequiresBuildRights() {
        PlayerMock player = server.addPlayer();
        Block target = world.getBlockAt(16, 70, 0);
        TNTPrimeEvent denied =
                new TNTPrimeEvent(target, TNTPrimeEvent.PrimeCause.PLAYER, player, null);
        server.getPluginManager().callEvent(denied);
        assertThat(denied.isCancelled()).isTrue();
        player.addAttachment(plugin, "spawnbuildrights.build", true);
        TNTPrimeEvent allowed =
                new TNTPrimeEvent(target, TNTPrimeEvent.PrimeCause.PLAYER, player, null);
        server.getPluginManager().callEvent(allowed);
        assertThat(allowed.isCancelled()).isFalse();
    }

    @Test
    void reloadCanDisableEnvironmentalProtectionWithoutDisablingBuildRights() {
        plugin.getConfig().set("protection.explosions", false);
        plugin.getConfig().set("protection.hostile-mob-changes", false);
        plugin.getConfig().set("protection.display-damage", false);
        reload();
        Wither wither = world.spawn(new Location(world, 0, 70, 0), Wither.class);
        assertThat(change(wither, 0).isCancelled()).isFalse();
        Block block = world.getBlockAt(0, 70, 0);
        EntityExplodeEvent explosion =
                new EntityExplodeEvent(
                        wither,
                        wither.getLocation(),
                        new ArrayList<>(List.of(block)),
                        1,
                        ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(explosion);
        assertThat(explosion.blockList()).containsExactly(block);
        ArmorStand stand = world.spawn(new Location(world, 0, 70, 0), ArmorStand.class);
        assertThat(
                        damage(
                                        stand,
                                        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                                        DamageSource.builder(DamageType.EXPLOSION).build())
                                .isCancelled())
                .isFalse();
        assertThat(manipulate(server.addPlayer(), stand).isCancelled()).isTrue();
    }

    @Test
    void ignitionUsesTargetAndPreservesPermittedPlayerFires() {
        SmallFireball fireball = world.spawn(new Location(world, 20, 70, 0), SmallFireball.class);
        assertThat(ignite(world.getBlockAt(16, 70, 0), fireball).isCancelled()).isTrue();
        assertThat(ignite(world.getBlockAt(17, 70, 0), fireball).isCancelled()).isFalse();
        Wither mob = world.spawn(new Location(world, 20, 70, 0), Wither.class);
        assertThat(ignite(world.getBlockAt(16, 70, 0), mob).isCancelled()).isTrue();
        PlayerMock player = server.addPlayer();
        assertThat(ignite(world.getBlockAt(16, 70, 0), player).isCancelled()).isTrue();
        player.addAttachment(plugin, "spawnbuildrights.build", true);
        assertThat(ignite(world.getBlockAt(16, 70, 0), player).isCancelled()).isFalse();
        // Paper attributes an owned projectile's ignition to its shooter, not the projectile.
        for (BlockIgniteEvent.IgniteCause cause :
                List.of(
                        BlockIgniteEvent.IgniteCause.FIREBALL,
                        BlockIgniteEvent.IgniteCause.ARROW)) {
            BlockIgniteEvent projectile =
                    new BlockIgniteEvent(world.getBlockAt(16, 70, 0), cause, player);
            server.getPluginManager().callEvent(projectile);
            assertThat(projectile.isCancelled()).isTrue();
        }
        // A fire charge used by hand is a fire lit directly, like flint and steel, so a builder
        // holding one lights it and anyone else is stopped by build rights alone.
        player.getInventory().setItemInMainHand(new ItemStack(Material.FIRE_CHARGE));
        BlockIgniteEvent charge =
                new BlockIgniteEvent(
                        world.getBlockAt(16, 70, 0), BlockIgniteEvent.IgniteCause.FIREBALL, player);
        server.getPluginManager().callEvent(charge);
        assertThat(charge.isCancelled()).isFalse();
        PlayerMock newcomer = server.addPlayer();
        newcomer.getInventory().setItemInOffHand(new ItemStack(Material.FIRE_CHARGE));
        BlockIgniteEvent newcomerCharge =
                new BlockIgniteEvent(
                        world.getBlockAt(16, 70, 0),
                        BlockIgniteEvent.IgniteCause.FIREBALL,
                        newcomer);
        server.getPluginManager().callEvent(newcomerCharge);
        assertThat(newcomerCharge.isCancelled()).isTrue();

        plugin.getConfig().set("protection.hostile-mob-changes", false);
        reload();
        assertThat(ignite(world.getBlockAt(16, 70, 0), fireball).isCancelled()).isFalse();
    }

    @Test
    void lavaLightningAndExplosionsLightNoFireInside() {
        for (BlockIgniteEvent.IgniteCause cause :
                List.of(
                        BlockIgniteEvent.IgniteCause.LAVA,
                        BlockIgniteEvent.IgniteCause.LIGHTNING,
                        BlockIgniteEvent.IgniteCause.EXPLOSION)) {
            assertThat(natural(world.getBlockAt(16, 70, 0), cause).isCancelled())
                    .as(cause + " inside")
                    .isTrue();
            assertThat(natural(world.getBlockAt(17, 70, 0), cause).isCancelled())
                    .as(cause + " outside")
                    .isFalse();
        }
        // Lava and lightning follow fire-damage, an explosion's fire follows explosions.
        plugin.getConfig().set("protection.fire-damage", false);
        reload();
        assertThat(natural(world.getBlockAt(16, 70, 0), BlockIgniteEvent.IgniteCause.LAVA))
                .extracting(BlockIgniteEvent::isCancelled)
                .isEqualTo(false);
        assertThat(natural(world.getBlockAt(16, 70, 0), BlockIgniteEvent.IgniteCause.EXPLOSION))
                .extracting(BlockIgniteEvent::isCancelled)
                .isEqualTo(true);
        plugin.getConfig().set("protection.explosions", false);
        reload();
        assertThat(natural(world.getBlockAt(16, 70, 0), BlockIgniteEvent.IgniteCause.EXPLOSION))
                .extracting(BlockIgniteEvent::isCancelled)
                .isEqualTo(false);
        // A ghast's fireball exploding is still a mob's fire while hostile-mob-changes is on.
        BlockIgniteEvent fireballExplosion =
                new BlockIgniteEvent(
                        world.getBlockAt(16, 70, 0),
                        BlockIgniteEvent.IgniteCause.EXPLOSION,
                        world.spawn(new Location(world, 20, 70, 0), SmallFireball.class));
        server.getPluginManager().callEvent(fireballExplosion);
        assertThat(fireballExplosion.isCancelled()).isTrue();
    }

    private BlockIgniteEvent natural(Block block, BlockIgniteEvent.IgniteCause cause) {
        BlockIgniteEvent event = new BlockIgniteEvent(block, cause, (Entity) null);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aHostileMobsProjectileChangesNoBlockInside() {
        Skeleton skeleton = world.spawn(new Location(world, 20, 70, 0), Skeleton.class);
        Arrow arrow = world.spawn(new Location(world, 17, 70, 0), Arrow.class);
        arrow.setShooter(skeleton);
        assertThat(change(arrow, 16).isCancelled()).isTrue();
        assertThat(change(arrow, 17).isCancelled()).isFalse();
        Arrow unowned = world.spawn(new Location(world, 17, 70, 0), Arrow.class);
        assertThat(change(unowned, 16).isCancelled()).isFalse();
    }

    @Test
    void fireDamageUsesDestinationBoundsWithoutStoppingOtherSpread() {
        Block source = world.getBlockAt(18, 70, 0);
        for (Block target : List.of(world.getBlockAt(16, 60, 0), world.getBlockAt(0, 90, 0))) {
            assertThat(burn(target, source).isCancelled()).isTrue();
            assertThat(spread(target, source, Material.FIRE).isCancelled()).isTrue();
            assertThat(spread(target, source, Material.SOUL_FIRE).isCancelled()).isTrue();
            assertThat(spread(target, source, Material.GRASS_BLOCK).isCancelled()).isFalse();
        }
        for (Block target :
                List.of(
                        world.getBlockAt(17, 70, 0),
                        world.getBlockAt(0, 59, 0),
                        world.getBlockAt(0, 91, 0),
                        server.addSimpleWorld("other").getBlockAt(0, 70, 0))) {
            assertThat(burn(target, source).isCancelled()).isFalse();
            assertThat(spread(target, source, Material.FIRE).isCancelled()).isFalse();
        }
        plugin.getConfig().set("protection.fire-damage", false);
        reload();
        assertThat(burn(world.getBlockAt(16, 70, 0), source).isCancelled()).isFalse();
        assertThat(spread(world.getBlockAt(16, 70, 0), source, Material.FIRE).isCancelled())
                .isFalse();
    }

    private BlockIgniteEvent ignite(Block block, Entity source) {
        BlockIgniteEvent event =
                new BlockIgniteEvent(
                        block,
                        source instanceof Player
                                ? BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                                : BlockIgniteEvent.IgniteCause.FIREBALL,
                        source);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private BlockBurnEvent burn(Block block, Block source) {
        BlockBurnEvent event = new BlockBurnEvent(block, source);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private BlockSpreadEvent spread(Block block, Block source, Material material) {
        BlockState state = block.getState();
        state.setType(material);
        BlockSpreadEvent event = new BlockSpreadEvent(block, source, state);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @ParameterizedTest
    @EnumSource(value = PlayerItemFrameChangeEvent.ItemFrameChangeAction.class)
    void frameAddRemoveAndRotateAllNeedBuildRights(
            PlayerItemFrameChangeEvent.ItemFrameChangeAction action) {
        PlayerMock player = server.addPlayer();
        ItemFrame frame = world.spawn(new Location(world, 0, 70, 0), ItemFrame.class);
        PlayerItemFrameChangeEvent event =
                new PlayerItemFrameChangeEvent(
                        player, frame, new ItemStack(Material.DIAMOND), action);
        server.getPluginManager().callEvent(event);
        assertThat(event.isCancelled()).isTrue();
        player.addAttachment(plugin, "spawnbuildrights.build", true);
        PlayerItemFrameChangeEvent allowed =
                new PlayerItemFrameChangeEvent(
                        player, frame, new ItemStack(Material.DIAMOND), action);
        server.getPluginManager().callEvent(allowed);
        assertThat(allowed.isCancelled()).isFalse();
    }

    @Test
    void standEquipmentAndPlayerProjectilesNeedBuildRights() {
        PlayerMock player = server.addPlayer();
        ArmorStand stand = world.spawn(new Location(world, 0, 70, 0), ArmorStand.class);
        assertThat(manipulate(player, stand).isCancelled()).isTrue();
        DamageSource arrow =
                DamageSource.builder(DamageType.ARROW).withCausingEntity(player).build();
        assertThat(damage(stand, EntityDamageEvent.DamageCause.PROJECTILE, arrow).isCancelled())
                .isTrue();
        player.addAttachment(plugin, "spawnbuildrights.build", true);
        assertThat(manipulate(player, stand).isCancelled()).isFalse();
        assertThat(damage(stand, EntityDamageEvent.DamageCause.PROJECTILE, arrow).isCancelled())
                .isFalse();
        // Even a builder's explosion should not destroy other displays at spawn.
        assertThat(
                        damage(
                                        stand,
                                        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                                        DamageSource.builder(DamageType.EXPLOSION)
                                                .withCausingEntity(player)
                                                .build())
                                .isCancelled())
                .isTrue();
    }

    @Test
    void protectsHangingDisplaysWithoutKeepingUnsupportedFramesFloating() {
        ItemFrame frame = world.spawn(new Location(world, 0, 70, 0), ItemFrame.class);
        HangingBreakEvent explosion =
                new HangingBreakEvent(frame, HangingBreakEvent.RemoveCause.EXPLOSION);
        server.getPluginManager().callEvent(explosion);
        assertThat(explosion.isCancelled()).isTrue();
        HangingBreakEvent physics =
                new HangingBreakEvent(frame, HangingBreakEvent.RemoveCause.PHYSICS);
        server.getPluginManager().callEvent(physics);
        assertThat(physics.isCancelled()).isFalse();
        PlayerMock player = server.addPlayer();
        HangingBreakByEntityEvent removal =
                new HangingBreakByEntityEvent(
                        frame,
                        player,
                        DamageSource.builder(DamageType.PLAYER_ATTACK)
                                .withCausingEntity(player)
                                .build());
        server.getPluginManager().callEvent(removal);
        assertThat(removal.isCancelled()).isTrue();
    }

    @Test
    void mobProjectilesCannotEmptyOrBreakFrames() {
        ItemFrame frame = world.spawn(new Location(world, 0, 70, 0), ItemFrame.class);
        Wither mob = world.spawn(new Location(world, 18, 70, 0), Wither.class);
        DamageSource projectile =
                DamageSource.builder(DamageType.WITHER_SKULL).withCausingEntity(mob).build();
        assertThat(
                        damage(frame, EntityDamageEvent.DamageCause.PROJECTILE, projectile)
                                .isCancelled())
                .isTrue();
        HangingBreakByEntityEvent remove = new HangingBreakByEntityEvent(frame, mob, projectile);
        server.getPluginManager().callEvent(remove);
        assertThat(remove.isCancelled()).isTrue();
    }

    @Test
    void hostileTramplingIsStoppedButPressurePlatesStillWork() {
        Wither mob = world.spawn(new Location(world, 0, 70, 0), Wither.class);
        Block ground = world.getBlockAt(0, 70, 0);
        for (Material material :
                List.of(Material.FARMLAND, Material.TURTLE_EGG, Material.STONE_PRESSURE_PLATE)) {
            ground.setType(material);
            EntityInteractEvent interaction = new EntityInteractEvent(mob, ground);
            server.getPluginManager().callEvent(interaction);
            assertThat(interaction.isCancelled())
                    .isEqualTo(material != Material.STONE_PRESSURE_PLATE);
        }
    }

    @Test
    void normalCombatAndChestAccessRemainAvailable() {
        Cow cow = world.spawn(new Location(world, 0, 70, 0), Cow.class);
        PlayerMock player = server.addPlayer();
        DamageSource explosion = DamageSource.builder(DamageType.EXPLOSION).build();
        assertThat(
                        damage(cow, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, explosion)
                                .isCancelled())
                .isFalse();
        assertThat(
                        damage(player, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, explosion)
                                .isCancelled())
                .isFalse();
        InventoryOpenEvent open =
                new InventoryOpenEvent(player.openInventory(server.createInventory(null, 27)));
        server.getPluginManager().callEvent(open);
        assertThat(open.isCancelled()).isFalse();
        ArmorStand outside = world.spawn(new Location(world, 17, 70, 0), ArmorStand.class);
        assertThat(
                        damage(outside, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, explosion)
                                .isCancelled())
                .isFalse();
    }

    private void reload() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private EntityChangeBlockEvent change(Entity mob, int x) {
        EntityChangeBlockEvent event =
                new EntityChangeBlockEvent(
                        mob, world.getBlockAt(x, 70, 0), Material.AIR.createBlockData());
        server.getPluginManager().callEvent(event);
        return event;
    }

    // Only the constructor taking DamageModifier maps is not marked for removal.
    @SuppressWarnings("deprecation")
    private EntityDamageEvent damage(
            Entity entity, EntityDamageEvent.DamageCause cause, DamageSource source) {
        EntityDamageEvent event =
                new EntityDamageEvent(
                        entity,
                        cause,
                        source,
                        new EnumMap<>(Map.of(EntityDamageEvent.DamageModifier.BASE, 10.0)),
                        new EnumMap<>(
                                Map.of(
                                        EntityDamageEvent.DamageModifier.BASE,
                                        Functions.constant(-0.0))));
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerArmorStandManipulateEvent manipulate(PlayerMock player, ArmorStand stand) {
        PlayerArmorStandManipulateEvent event =
                new PlayerArmorStandManipulateEvent(
                        player,
                        stand,
                        new ItemStack(Material.AIR),
                        new ItemStack(Material.DIAMOND_HELMET),
                        EquipmentSlot.HEAD,
                        EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }
}

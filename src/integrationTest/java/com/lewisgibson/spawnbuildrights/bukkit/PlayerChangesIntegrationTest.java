package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The block changes a player makes without placing or breaking a block: each needs build rights
 * inside the region, is left alone outside it, and a builder makes it as normal. Using a block, as
 * opposed to changing it, stays open to everyone.
 */
@DisplayName("SpawnBuildRights player block changes (integration)")
class PlayerChangesIntegrationTest {

    private static final String BUILD = "spawnbuildrights.build";

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
        plugin.saveConfig();
        plugin.reloadSettings();
        newcomer = server.addPlayer("Newcomer");
        builder = server.addPlayer("Builder");
        builder.addAttachment(plugin, BUILD, true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Trampling farmland, eating a cake, an eye in a portal frame, brushing suspicious sand. */
    @ParameterizedTest
    @EnumSource(
            value = Material.class,
            names = {"FARMLAND", "CAKE", "END_PORTAL_FRAME", "SUSPICIOUS_SAND"})
    void aPlayerChangingABlockNeedsBuildRights(Material material) {
        assertThat(change(newcomer, material, 16).isCancelled()).isTrue();
        assertThat(newcomer.nextActionBar()).isNotNull();
        assertThat(change(newcomer, material, 17).isCancelled()).isFalse();
        assertThat(change(builder, material, 16).isCancelled()).isFalse();
    }

    /** Touching redstone ore, a tilting dripleaf, a composter, picking glow berries. */
    @ParameterizedTest
    @EnumSource(
            value = Material.class,
            names = {
                "REDSTONE_ORE",
                "DEEPSLATE_REDSTONE_ORE",
                "BIG_DRIPLEAF",
                "COMPOSTER",
                "CAVE_VINES",
                "CAVE_VINES_PLANT"
            })
    void usingABlockStaysOpen(Material material) {
        assertThat(change(newcomer, material, 0).isCancelled()).isFalse();
        assertThat(newcomer.nextActionBar()).isNull();
    }

    @Test
    @DisplayName("a block a player's own projectile breaks follows that player's build rights")
    void aPlayersProjectileFollowsTheirBuildRights() {
        assertThat(change(arrowFrom(newcomer), Material.DECORATED_POT, 16).isCancelled()).isTrue();
        assertThat(change(arrowFrom(newcomer), Material.DECORATED_POT, 17).isCancelled()).isFalse();
        assertThat(change(arrowFrom(builder), Material.DECORATED_POT, 16).isCancelled()).isFalse();
    }

    @Test
    @DisplayName(
            "bone meal on a block in the region needs build rights; dispensed bone meal does not")
    void boneMeal() {
        assertThat(fertilize(newcomer, 16).isCancelled()).isTrue();
        assertThat(fertilize(newcomer, 17).isCancelled()).isFalse();
        assertThat(fertilize(builder, 16).isCancelled()).isFalse();
        assertThat(fertilize(null, 16).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("planting in or emptying a flower pot needs build rights")
    void flowerPots() {
        for (boolean placing : List.of(true, false)) {
            assertThat(pot(newcomer, 16, placing).isCancelled()).isTrue();
            assertThat(pot(newcomer, 17, placing).isCancelled()).isFalse();
            assertThat(pot(builder, 16, placing).isCancelled()).isFalse();
        }
    }

    @Test
    @DisplayName("carving a pumpkin needs build rights; taking honeycomb from a hive does not")
    void shears() {
        assertThat(shear(newcomer, Material.PUMPKIN, 16).isCancelled()).isTrue();
        assertThat(shear(builder, Material.PUMPKIN, 16).isCancelled()).isFalse();
        assertThat(shear(newcomer, Material.BEEHIVE, 16).isCancelled()).isFalse();
        assertThat(shear(newcomer, Material.BEE_NEST, 16).isCancelled()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = Material.class,
            names = {"RED_DYE", "INK_SAC", "GLOW_INK_SAC", "HONEYCOMB"})
    void dyeingGlowingOrWaxingASignNeedsBuildRights(Material item) {
        assertThat(click(newcomer, Material.OAK_SIGN, item, 16).isCancelled()).isTrue();
        assertThat(click(newcomer, Material.OAK_SIGN, item, 17).isCancelled()).isFalse();
        assertThat(click(builder, Material.OAK_SIGN, item, 16).isCancelled()).isFalse();
        assertThat(click(newcomer, Material.OAK_HANGING_SIGN, item, 16).isCancelled()).isTrue();
    }

    @Test
    @DisplayName("reading a sign, or clicking other blocks with a dye, stays open")
    void otherClicksStayOpen() {
        assertThat(click(newcomer, Material.OAK_SIGN, Material.STICK, 16).isCancelled()).isFalse();
        assertThat(click(newcomer, Material.OAK_SIGN, null, 16).isCancelled()).isFalse();
        assertThat(click(newcomer, Material.CHEST, Material.RED_DYE, 16).isCancelled()).isFalse();
        PlayerInteractEvent leftClick =
                call(
                        new PlayerInteractEvent(
                                newcomer,
                                Action.LEFT_CLICK_BLOCK,
                                new ItemStack(Material.RED_DYE),
                                block(Material.OAK_SIGN, 16),
                                BlockFace.UP,
                                EquipmentSlot.HAND));
        assertThat(leftClick.isCancelled()).isFalse();
    }

    private EntityChangeBlockEvent change(Entity entity, Material material, int x) {
        return call(
                new EntityChangeBlockEvent(
                        entity, block(material, x), Material.AIR.createBlockData()));
    }

    private Arrow arrowFrom(PlayerMock shooter) {
        Arrow arrow = world.spawn(new Location(world, 20, 70, 0), Arrow.class);
        arrow.setShooter(shooter);
        return arrow;
    }

    private BlockFertilizeEvent fertilize(PlayerMock player, int x) {
        List<BlockState> grown = new ArrayList<>(List.of(world.getBlockAt(x, 71, 0).getState()));
        return call(new BlockFertilizeEvent(block(Material.MOSS_BLOCK, x), player, grown));
    }

    private PlayerFlowerPotManipulateEvent pot(PlayerMock player, int x, boolean placing) {
        return call(
                new PlayerFlowerPotManipulateEvent(
                        player,
                        block(Material.FLOWER_POT, x),
                        new ItemStack(Material.POPPY),
                        placing));
    }

    private PlayerShearBlockEvent shear(PlayerMock player, Material material, int x) {
        return call(
                new PlayerShearBlockEvent(
                        player,
                        block(material, x),
                        new ItemStack(Material.SHEARS),
                        EquipmentSlot.HAND,
                        new ArrayList<>()));
    }

    private PlayerInteractEvent click(PlayerMock player, Material clicked, Material item, int x) {
        return call(
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_BLOCK,
                        item == null ? null : new ItemStack(item),
                        block(clicked, x),
                        BlockFace.NORTH,
                        EquipmentSlot.HAND));
    }

    private Block block(Material material, int x) {
        Block block = world.getBlockAt(x, 70, 0);
        block.setType(material);
        return block;
    }

    private <T extends Event & Cancellable> T call(T event) {
        server.getPluginManager().callEvent(event);
        return event;
    }
}

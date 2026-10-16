package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end tests booting the plugin on a mocked Paper server and firing real Bukkit events, to
 * verify the adapter wires the pure domain logic to Bukkit correctly.
 */
@DisplayName("SpawnBuildRights (integration)")
class SpawnBuildRightsIntegrationTest {

    private static final long THIRTY_SIX_HOURS = 2_592_000L;
    private static final String BUILD = "spawnbuildrights.build";

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("enables and registers its command")
    void enablesCleanly() {
        assertThat(plugin.isEnabled()).isTrue();
        assertThat(plugin.getCommand("spawnbuildrights")).isNotNull();
    }

    @Test
    @DisplayName("cancels a break inside the region for a below-threshold player")
    void cancelsBreakForNewPlayer() {
        PlayerMock player = playerWithPlaytime(THIRTY_SIX_HOURS - 1);

        BlockBreakEvent event = breakAtRegionCentre(player);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("tells a denied player on the action bar, never in chat, with the time left")
    void deniesOnTheActionBar() {
        PlayerMock player = playerWithPlaytime(20L * 60 * 60 * 20);

        breakAtRegionCentre(player);

        // Players read hours below a week: 36h required, and 20h played leaves 16h.
        assertThat(plain(player.nextActionBar()))
                .isEqualTo("You can build at spawn after another 16 hours of play");
        assertThat(player.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName(
            "shows a player the two largest units of the time left, never seconds of a long wait")
    void denialShowsTwoUnits() {
        // 2h 18m 7s played of 36h leaves 33h 41m 53s, shown as 33h 41m.
        PlayerMock player = playerWithPlaytime(20L * (2 * 3600 + 18 * 60 + 7));

        breakAtRegionCentre(player);

        assertThat(plain(player.nextActionBar()))
                .isEqualTo("You can build at spawn after another 33 hours and 41 minutes of play");
    }

    @Test
    @DisplayName("a blank denial line refuses silently")
    void blankDenialIsSilent() {
        plugin.editConfig(config -> config.set("messages.build-denied", ""));
        PlayerMock player = playerWithPlaytime(0);

        assertThat(breakAtRegionCentre(player).isCancelled()).isTrue();
        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    @DisplayName("allows a break inside the region for an eligible player")
    void allowsBreakForEligiblePlayer() {
        PlayerMock player = playerWithPlaytime(THIRTY_SIX_HOURS);

        BlockBreakEvent event = breakAtRegionCentre(player);

        assertThat(event.isCancelled()).isFalse();
        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    @DisplayName("ignores a break well outside the region")
    void ignoresBreakOutsideRegion() {
        PlayerMock player = playerWithPlaytime(0);

        Location spawn = server.getWorld("world").getSpawnLocation();
        Block far =
                server.getWorld("world")
                        .getBlockAt(
                                spawn.getBlockX() + 500,
                                spawn.getBlockY(),
                                spawn.getBlockZ() + 500);
        BlockBreakEvent event = new BlockBreakEvent(far, player);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("allows a below-threshold player whose build node is set true")
    void allowsGrantedPlayer() {
        PlayerMock player = playerWithPlaytime(0);
        player.addAttachment(plugin, BUILD, true);

        BlockBreakEvent event = breakAtRegionCentre(player);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("allows an operator with no playtime, through the node's op default")
    void allowsOperatorByDefault() {
        PlayerMock player = playerWithPlaytime(0);
        player.setOp(true);

        BlockBreakEvent event = breakAtRegionCentre(player);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("denies a player past the threshold whose build node is set false")
    void deniesRevokedPlayer() {
        PlayerMock player = playerWithPlaytime(THIRTY_SIX_HOURS * 10);
        player.addAttachment(plugin, BUILD, false);

        BlockBreakEvent event = breakAtRegionCentre(player);

        assertThat(event.isCancelled()).isTrue();
        assertThat(plain(player.nextActionBar())).containsIgnoringCase("removed");
    }

    @Test
    @DisplayName("applies a changed threshold on reload, with no restart")
    void reloadAppliesNewThreshold() {
        PlayerMock player = playerWithPlaytime(2L * 60 * 60 * 20);
        assertThat(breakAtRegionCentre(player).isCancelled()).isTrue();

        plugin.getConfig().set("required-playtime", "1h");
        plugin.saveConfig();
        plugin.reloadSettings();

        assertThat(breakAtRegionCentre(player).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a config that omits the messages still sends the shipped denial line")
    void omittedMessagesFallBackToShipped() throws Exception {
        java.nio.file.Files.writeString(
                new java.io.File(plugin.getDataFolder(), "config.yml").toPath(),
                "required-playtime: 36h\n");
        plugin.reloadSettings();

        PlayerMock player = playerWithPlaytime(0);
        breakAtRegionCentre(player);

        assertThat(plain(player.nextActionBar())).contains("another 36 hours of play");
    }

    @Test
    @DisplayName("still protects the default world when region.world names an unloaded world")
    void fallsBackToDefaultWorldOnBadConfig() {
        plugin.getConfig().set("region.world", "does_not_exist");
        plugin.saveConfig();
        plugin.reloadSettings();

        PlayerMock player = playerWithPlaytime(0);

        BlockBreakEvent event = breakAtRegionCentre(player);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("cancels emptying a lava bucket inside the region for a below-threshold player")
    void cancelsLavaBucketForNewPlayer() {
        PlayerMock player = playerWithPlaytime(0);

        Block clicked = regionCentreBlock();
        PlayerBucketEmptyEvent event =
                new PlayerBucketEmptyEvent(
                        player,
                        clicked.getRelative(BlockFace.UP),
                        clicked,
                        BlockFace.UP,
                        Material.LAVA_BUCKET,
                        new ItemStack(Material.LAVA_BUCKET),
                        EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("editConfig saves the change and applies it in one step")
    void editConfigSavesAndApplies() {
        plugin.editConfig(config -> config.set("required-playtime", "1h"));

        assertThat(plugin.runtime().settings().requiredPlaytimeTicks()).isEqualTo(72_000L);
        assertThat(
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                                        new java.io.File(plugin.getDataFolder(), "config.yml"))
                                .getString("required-playtime"))
                .isEqualTo("1h");
    }

    @Test
    @DisplayName("concurrent edits and reloads, as Folia region threads issue them, never corrupt")
    void concurrentEditsAndReloadsStayConsistent() throws Exception {
        int threads = 12;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int radius = 10 + t;
            results.add(
                    pool.submit(
                            () -> {
                                go.await();
                                for (int i = 0; i < 25; i++) {
                                    if (radius % 2 == 0) {
                                        plugin.editConfig(
                                                c -> {
                                                    c.set("region.radius", radius);
                                                    c.set(
                                                            "storage.sqlite.file",
                                                            "d" + radius + ".db");
                                                });
                                    } else {
                                        plugin.reloadSettings();
                                    }
                                }
                                return null;
                            }));
        }
        go.countDown();
        for (java.util.concurrent.Future<?> result : results) {
            result.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdown();

        int live = plugin.runtime().settings().region().radius();
        int saved =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                                new java.io.File(plugin.getDataFolder(), "config.yml"))
                        .getInt("region.radius");
        assertThat(live).isEqualTo(saved).isBetween(10, 21);
        assertThat(plugin.runtime().settings().storage().sqliteFile())
                .isEqualTo("d" + saved + ".db");
    }

    private PlayerMock playerWithPlaytime(long ticks) {
        PlayerMock player = server.addPlayer();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) ticks);
        return player;
    }

    private BlockBreakEvent breakAtRegionCentre(PlayerMock player) {
        BlockBreakEvent event = new BlockBreakEvent(regionCentreBlock(), player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Block regionCentreBlock() {
        Location spawn = server.getWorld("world").getSpawnLocation();
        return server.getWorld("world")
                .getBlockAt(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
    }

    private static String plain(Component component) {
        assertThat(component).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

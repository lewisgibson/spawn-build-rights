package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import com.lewisgibson.spawnbuildrights.storage.JdbcGrantStore;
import com.lewisgibson.spawnbuildrights.storage.SqliteConnectionSource;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The by-hand side end to end on a mocked server: the commands, the registry they update, the
 * SQLite file they write, and what survives a restart.
 */
@DisplayName("SpawnBuildRights grants (integration)")
class GrantCommandIntegrationTest {

    private static final long THIRTY_SIX_HOURS = 2_592_000L;

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private ConsoleCommandSenderMock console;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        console = server.getConsoleSender();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("opens a SQLite file under the data folder with nothing configured")
    void opensSqliteByDefault() {
        assertThat(new File(plugin.getDataFolder(), "data.db")).exists();
    }

    @Test
    @DisplayName("/sbr revoke stops a player past the threshold, and /sbr reset lets them back")
    void revokeThenReset() {
        PlayerMock player = playerWithPlaytime("Steve", THIRTY_SIX_HOURS * 10);

        server.dispatchCommand(console, "sbr revoke Steve took the diamonds");
        assertThat(plain(console.nextComponentMessage())).contains("Steve cannot build");
        BlockBreakEvent event = breakAtRegionCentre(player);
        assertThat(event.isCancelled()).isTrue();
        assertThat(plain(player.nextActionBar())).containsIgnoringCase("removed");

        server.dispatchCommand(console, "sbr reset Steve");
        assertThat(plain(console.nextComponentMessage())).contains("back to playtime");
        assertThat(breakAtRegionCentre(player).isCancelled()).isFalse();

        server.dispatchCommand(console, "sbr reset Steve");
        assertThat(plain(console.nextComponentMessage())).contains("had no override");
    }

    @Test
    @DisplayName("/sbr grant lets a brand-new player build")
    void grantAllowsNewPlayer() {
        PlayerMock player = playerWithPlaytime("Alex", 0);
        server.dispatchCommand(console, "sbr grant Alex");
        assertThat(plain(console.nextComponentMessage())).contains("Alex can build");
        assertThat(breakAtRegionCentre(player).isCancelled()).isFalse();
        assertThat(plugin.runtime().grants().registry().state(player.getUniqueId()))
                .isEqualTo(OverrideState.GRANTED);
    }

    @Test
    @DisplayName("a revocation in the store beats a granted permission node")
    void revocationBeatsNode() {
        PlayerMock player = playerWithPlaytime("Steve", 0);
        player.addAttachment(plugin, "spawnbuildrights.build", true);
        server.dispatchCommand(console, "sbr revoke Steve");
        assertThat(breakAtRegionCentre(player).isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a player nobody has seen cannot be granted or revoked")
    void unknownPlayer() {
        server.dispatchCommand(console, "sbr grant Nobody");
        assertThat(plain(console.nextComponentMessage())).contains("No player called 'Nobody'");
    }

    @Test
    @DisplayName("a non-admin cannot use the by-hand commands")
    void nonAdminRefused() {
        PlayerMock player = playerWithPlaytime("Steve", 0);
        player.addAttachment(plugin, "spawnbuildrights.info", true);
        player.performCommand("sbr revoke Steve");
        assertThat(plain(player.nextComponentMessage())).contains("permission");
        assertThat(plugin.runtime().grants().registry().state(player.getUniqueId()))
                .isEqualTo(OverrideState.NONE);
    }

    @Test
    @DisplayName("/sbr history shows the override and the audit log, newest first")
    void history() {
        playerWithPlaytime("Steve", 0);
        server.dispatchCommand(console, "sbr grant Steve helped build it");
        console.nextComponentMessage();

        // Straight after the grant, with no wait: the command itself must see its own write.
        server.dispatchCommand(console, "sbr history Steve");
        assertThat(plain(console.nextComponentMessage()))
                .contains("Steve override")
                .contains("granted by console")
                .contains("(helped build it)");
        assertThat(plain(console.nextComponentMessage())).contains("granted by console");
        assertThat(console.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("an earned event is recorded once, on the first allowed action")
    void earnedRecordedOnce() {
        PlayerMock player = playerWithPlaytime("Steve", THIRTY_SIX_HOURS);
        breakAtRegionCentre(player);
        breakAtRegionCentre(player);
        assertThat(plugin.awaitStoreWrites(Duration.ofSeconds(5))).isTrue();

        assertThat(plugin.runtime().grants().history(player.getUniqueId(), 10))
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.kind()).isEqualTo(GrantEvent.Kind.EARNED);
                            assertThat(event.playtimeTicks()).isEqualTo(THIRTY_SIX_HOURS);
                        });
    }

    @Test
    @DisplayName("overrides and the audit log are on disk, readable by a fresh connection")
    void writtenThrough() {
        PlayerMock player = playerWithPlaytime("Steve", 0);
        UUID id = player.getUniqueId();
        File file = new File(plugin.getDataFolder(), "data.db");
        server.dispatchCommand(console, "sbr revoke Steve grief");
        assertThat(plugin.awaitStoreWrites(Duration.ofSeconds(5))).isTrue();

        // A second, independent connection to the same file: what the next boot would read.
        // (MockBukkit deletes the data folder on unmock, so a literal restart cannot be staged.)
        JdbcGrantStore reopened = new JdbcGrantStore(new SqliteConnectionSource(file.toPath()));
        reopened.open();
        assertThat(reopened.loadOverrides().get(id).state()).isEqualTo(OverrideState.REVOKED);
        assertThat(reopened.history(id, 10))
                .singleElement()
                .extracting(GrantEvent::kind)
                .isEqualTo(GrantEvent.Kind.REVOKED);
        reopened.close();
    }

    @Test
    @DisplayName("reload keeps the store when its settings are unchanged")
    void reloadKeepsStore() {
        PlayerMock player = playerWithPlaytime("Steve", 0);
        server.dispatchCommand(console, "sbr grant Steve");
        plugin.reloadSettings();
        assertThat(plugin.runtime().grants().registry().state(player.getUniqueId()))
                .isEqualTo(OverrideState.GRANTED);
    }

    @Test
    @DisplayName(
            "reload reads decisions another server wrote to a shared store, and agrees with it")
    void reloadReadsAnotherServersDecisions() {
        PlayerMock player = playerWithPlaytime("Steve", 0);
        UUID id = player.getUniqueId();
        // Server B, sharing the same database: here, a second connection to the same file.
        JdbcGrantStore serverB =
                new JdbcGrantStore(
                        new SqliteConnectionSource(
                                new File(plugin.getDataFolder(), "data.db").toPath()));
        serverB.open();
        java.time.Instant now = java.time.Instant.now();
        serverB.putOverride(
                new com.lewisgibson.spawnbuildrights.core.grant.GrantOverride(
                        id, "Steve", OverrideState.GRANTED, "ServerB", "trusted", now),
                new GrantEvent(
                        id, "Steve", GrantEvent.Kind.GRANTED, "ServerB", "trusted", -1, now));
        serverB.close();
        assertThat(plugin.runtime().grants().registry().state(id)).isEqualTo(OverrideState.NONE);

        server.dispatchCommand(console, "sbr reload");
        console.nextComponentMessage();

        assertThat(plugin.runtime().grants().registry().state(id)).isEqualTo(OverrideState.GRANTED);
        assertThat(breakAtRegionCentre(player).isCancelled()).isFalse();
        server.dispatchCommand(console, "sbr history Steve");
        assertThat(plain(console.nextComponentMessage())).contains("granted by ServerB");
        assertThat(plain(console.nextComponentMessage())).contains("granted by ServerB");
    }

    @Test
    @DisplayName("/sbr info shows admins the running version and every protection switch")
    void infoShowsProtectionToAdmins() {
        plugin.getConfig().set("protection.pistons", false);
        plugin.getConfig().set("protection.portal-arrivals.fireworks", false);
        plugin.saveConfig();
        plugin.reloadSettings();

        server.dispatchCommand(console, "sbr info");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo("SpawnBuildRights " + plugin.getPluginMeta().getVersion());
        assertThat(plain(console.nextComponentMessage()))
                .matches("Spawn region: square, 16 blocks around -?\\d+, -?\\d+ in world");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo("Build rights after: 36h of playtime");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo(
                        "Protections on: explosions, hostile-mob-changes, display-damage,"
                                + " fire-damage, falling-blocks, fluid-flow, dispensers, growth");
        assertThat(plain(console.nextComponentMessage())).isEqualTo("Protections off: pistons");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo(
                        "Portal arrivals defused: explosives, wither-skulls, fireballs,"
                                + " projectiles, falling-blocks");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo("Portal arrivals passed: fireworks");
        assertThat(console.nextComponentMessage()).isNull();

        plugin.getConfig().set("protection.pistons", true);
        plugin.getConfig().set("protection.portal-arrivals", false);
        plugin.saveConfig();
        plugin.reloadSettings();
        server.dispatchCommand(console, "sbr");
        for (int skip = 0; skip < 3; skip++) {
            console.nextComponentMessage();
        }
        assertThat(plain(console.nextComponentMessage())).contains("pistons");
        assertThat(plain(console.nextComponentMessage())).isEqualTo("Protections off: none");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo("Portal arrivals defused: none");
    }

    @Test
    @DisplayName("/sbr tells a player still earning build rights how long is left, in one sentence")
    void infoTellsAnEarningPlayerOneSentence() {
        PlayerMock player = playerWithPlaytime("Steve", 20L * (2 * 3600 + 14 * 60));
        player.addAttachment(plugin, "spawnbuildrights.info", true);
        assertThat(infoLines(player))
                .containsExactly(
                        "You can build at spawn after another 33 hours and 46 minutes of play");
    }

    @Test
    @DisplayName("/sbr tells a player who has earned build rights so, in one sentence")
    void infoTellsAnEarnedPlayerOneSentence() {
        PlayerMock player = playerWithPlaytime("Steve", THIRTY_SIX_HOURS + 20L * 60);
        player.addAttachment(plugin, "spawnbuildrights.info", true);
        assertThat(infoLines(player)).containsExactly("You can build at spawn");
    }

    @Test
    @DisplayName("/sbr tells a revoked player so, in one sentence, whatever their playtime")
    void infoTellsARevokedPlayerOneSentence() {
        PlayerMock player = playerWithPlaytime("Steve", THIRTY_SIX_HOURS + 20L * 60);
        player.addAttachment(plugin, "spawnbuildrights.info", true);
        server.dispatchCommand(console, "sbr revoke Steve took the diamonds");
        console.nextComponentMessage();
        assertThat(infoLines(player))
                .containsExactly("Your permission to build at spawn has been removed");
    }

    @Test
    @DisplayName("/sbr shows an admin player a set build node, a manual decision and playtime")
    void infoShowsAdminsNodeDecisionAndEarned() {
        PlayerMock player = playerWithPlaytime("Steve", THIRTY_SIX_HOURS + 20L * 60);
        player.addAttachment(plugin, "spawnbuildrights.admin", true);
        player.addAttachment(plugin, "spawnbuildrights.build", false);
        server.dispatchCommand(console, "sbr revoke Steve took the diamonds");
        console.nextComponentMessage();

        List<String> lines = infoLines(player);

        assertThat(lines.getFirst()).startsWith("SpawnBuildRights ");
        assertThat(lines).contains("Build rights after: 36h of playtime");
        assertThat(lines).contains("Your playtime: 36h 1m (earned)");
        assertThat(lines)
                .anyMatch(
                        line ->
                                line.startsWith("Manual decision: revoked by console at ")
                                        && line.endsWith("(took the diamonds)"));
        assertThat(lines).contains("Permission spawnbuildrights.build: false");
        assertThat(lines.getLast()).isEqualTo("You can build at spawn: no");
    }

    @Test
    @DisplayName("/sbr shows an admin player with no build node set no permission line")
    void infoShowsAdminsNoPermissionLineWhenUnset() {
        PlayerMock player = playerWithPlaytime("Steve", 20L * 60);
        player.addAttachment(plugin, "spawnbuildrights.admin", true);

        List<String> lines = infoLines(player);

        assertThat(lines).noneMatch(line -> line.startsWith("Permission "));
        assertThat(lines).contains("Manual decision: none");
    }

    private List<String> infoLines(PlayerMock player) {
        player.performCommand("sbr info");
        List<String> lines = new ArrayList<>();
        Component line;
        while ((line = player.nextComponentMessage()) != null) {
            lines.add(plain(line));
        }
        return lines;
    }

    @Test
    @DisplayName("the command is hidden from and refused to a player without the info node")
    void commandHiddenFromPlayers() {
        PlayerMock player = playerWithPlaytime("Steve", 0);
        assertThat(player.hasPermission("spawnbuildrights.info")).isFalse();
        assertThat(plugin.getCommand("spawnbuildrights").testPermissionSilent(player)).isFalse();
        player.performCommand("sbr info");
        Component line;
        while ((line = player.nextComponentMessage()) != null) {
            assertThat(plain(line)).doesNotStartWith("You can build at spawn");
        }

        PlayerMock admin = playerWithPlaytime("Alex", 0);
        admin.addAttachment(plugin, "spawnbuildrights.admin", true);
        assertThat(admin.hasPermission("spawnbuildrights.info")).isTrue();
        assertThat(plugin.getCommand("spawnbuildrights").testPermissionSilent(admin)).isTrue();
    }

    private PlayerMock playerWithPlaytime(String name, long ticks) {
        PlayerMock player = server.addPlayer(name);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) ticks);
        return player;
    }

    private BlockBreakEvent breakAtRegionCentre(PlayerMock player) {
        Location spawn = server.getWorld("world").getSpawnLocation();
        Block block =
                server.getWorld("world")
                        .getBlockAt(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static String plain(Component component) {
        assertThat(component).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

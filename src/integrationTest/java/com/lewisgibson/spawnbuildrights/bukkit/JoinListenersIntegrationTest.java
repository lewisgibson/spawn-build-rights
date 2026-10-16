package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * What happens when a player joins: an earned event is recorded the first time a player is seen
 * past the threshold, and operators and admins are warned privately about vanilla spawn-protection.
 */
@DisplayName("SpawnBuildRights join listeners (integration)")
class JoinListenersIntegrationTest {

    private static final long THIRTY_SIX_HOURS = 2_592_000L;

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
    @DisplayName("a join past the threshold records one earned event, and a join below none")
    void earnedOnJoin() {
        PlayerMock veteran = player("Veteran", THIRTY_SIX_HOURS);
        PlayerMock newcomer = player("Newcomer", THIRTY_SIX_HOURS - 1);
        join(veteran);
        join(veteran);
        join(newcomer);
        assertThat(plugin.awaitStoreWrites(Duration.ofSeconds(5))).isTrue();

        assertThat(plugin.runtime().grants().history(veteran.getUniqueId(), 10))
                .singleElement()
                .extracting(GrantEvent::kind)
                .isEqualTo(GrantEvent.Kind.EARNED);
        assertThat(plugin.runtime().grants().history(newcomer.getUniqueId(), 10)).isEmpty();
    }

    @Test
    @DisplayName("operators and admins are warned on join while vanilla spawn-protection is on")
    void operatorsAreWarned() {
        server.setSpawnRadius(10);
        PlayerMock operator = player("Operator", 0);
        operator.setOp(true);
        PlayerMock admin = player("Admin", 0);
        admin.addAttachment(plugin, SpawnBuildRightsPlugin.ADMIN_PERMISSION, true);
        PlayerMock ordinary = player("Ordinary", 0);

        join(operator);
        join(admin);
        join(ordinary);

        assertThat(plain(operator.nextComponentMessage())).contains("radius of 10");
        assertThat(plain(admin.nextComponentMessage())).contains("radius of 10");
        assertThat(ordinary.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName(
            "nobody is warned with vanilla spawn-protection off, the alert off or a blank line")
    void noWarningWhenNothingToSay() {
        server.setSpawnRadius(0);
        PlayerMock operator = player("Operator", 0);
        operator.setOp(true);
        join(operator);
        assertThat(operator.nextComponentMessage()).isNull();

        server.setSpawnRadius(10);
        plugin.getConfig().set("alert-operators-on-join", false);
        reload();
        join(operator);
        assertThat(operator.nextComponentMessage()).isNull();

        plugin.getConfig().set("alert-operators-on-join", true);
        plugin.getConfig().set("messages.operator-conflict-warning", " ");
        reload();
        join(operator);
        assertThat(operator.nextComponentMessage()).isNull();
    }

    private PlayerMock player(String name, long playtime) {
        PlayerMock player = server.addPlayer(name);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) playtime);
        while (player.nextComponentMessage() != null) {
            // drain anything the join inside addPlayer sent
        }
        return player;
    }

    private void join(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
    }

    private void reload() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private static String plain(Component component) {
        assertThat(component).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

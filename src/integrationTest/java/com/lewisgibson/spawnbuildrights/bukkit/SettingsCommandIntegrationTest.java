package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.spawnbuildrights.core.region.RegionShape;
import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /sbr set} subcommands, what an unknown subcommand tells each kind of sender, and tab
 * completion, end to end on a mocked server: each edit is checked both live and in the saved file.
 */
@DisplayName("SpawnBuildRights settings commands (integration)")
class SettingsCommandIntegrationTest {

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private ConsoleCommandSenderMock console;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        console = server.getConsoleSender();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("/sbr set threshold reads a duration typed as several words")
    void thresholdAcceptsSpacedDuration() {
        server.dispatchCommand(console, "sbr set threshold 1d 12h");

        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo("Required playtime set to 36h.");
        assertThat(plugin.runtime().settings().requiredPlaytimeTicks()).isEqualTo(2_592_000L);
        assertThat(saved().getString("required-playtime")).isEqualTo("36h");
    }

    @Test
    @DisplayName("/sbr set threshold refuses a bad duration and changes nothing")
    void thresholdRefusesBadDuration() {
        server.dispatchCommand(console, "sbr set threshold 36x");

        assertThat(plain(console.nextComponentMessage())).contains("invalid duration");
        assertThat(plugin.runtime().settings().requiredPlaytimeTicks()).isEqualTo(2_592_000L);
    }

    @Test
    @DisplayName("/sbr set radius and shape apply live and are saved")
    void radiusAndShape() {
        server.dispatchCommand(console, "sbr set radius 40");
        assertThat(plain(console.nextComponentMessage())).isEqualTo("Radius set to 40.");
        server.dispatchCommand(console, "sbr set shape circle");
        assertThat(plain(console.nextComponentMessage())).isEqualTo("Shape set to circle.");

        assertThat(plugin.runtime().settings().region().radius()).isEqualTo(40);
        assertThat(plugin.runtime().settings().region().shape()).isEqualTo(RegionShape.CIRCLE);
        assertThat(saved().getInt("region.radius")).isEqualTo(40);

        server.dispatchCommand(console, "sbr set radius -1");
        assertThat(plain(console.nextComponentMessage())).isEqualTo("Radius must be >= 0.");
        server.dispatchCommand(console, "sbr set radius ten");
        assertThat(plain(console.nextComponentMessage()))
                .isEqualTo("Radius must be a whole number.");
        assertThat(plugin.runtime().settings().region().radius()).isEqualTo(40);
    }

    @Test
    @DisplayName("/sbr set center here moves the centre to a player standing in the region's world")
    void centerHereInTheRegionWorld() {
        PlayerMock player = admin();
        player.teleport(new Location(world, 120.5, 70, -45.5));

        player.performCommand("sbr set center here");

        assertThat(plain(player.nextComponentMessage()))
                .isEqualTo("Region centre set to your location.");
        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(120);
        assertThat(plugin.runtime().settings().region().centerZ()).isEqualTo(-46);
    }

    @Test
    @DisplayName(
            "/sbr set center here refuses a player in another world, whose numbers mean nothing")
    void centerHereRefusedFromAnotherWorld() {
        World nether = server.addSimpleWorld("world_nether");
        PlayerMock player = admin();
        player.teleport(new Location(nether, 300, 70, 300));
        int before = plugin.runtime().settings().region().centerX();

        player.performCommand("sbr set center here");

        assertThat(plain(player.nextComponentMessage())).contains("the region's world");
        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(before);
        assertThat(saved().isInt("region.center-x")).isFalse();
    }

    @Test
    @DisplayName("an unknown subcommand names the admin subcommands only to an admin")
    void unknownSubcommandDoesNotAdvertiseAdminCommands() {
        PlayerMock player = server.addPlayer("Steve");
        player.addAttachment(plugin, "spawnbuildrights.info", true);
        player.performCommand("sbr bogus");
        assertThat(plain(player.nextComponentMessage()))
                .isEqualTo("Unknown subcommand. Use /sbr info.");

        server.dispatchCommand(console, "sbr bogus");
        assertThat(plain(console.nextComponentMessage())).contains("reload").contains("grant");
    }

    @Test
    @DisplayName("tab completion offers info alone to a player and the full set to an admin")
    void tabCompletion() {
        Command command = plugin.getCommand("spawnbuildrights");
        PlayerMock player = server.addPlayer("Steve");
        player.addAttachment(plugin, "spawnbuildrights.info", true);
        assertThat(command.tabComplete(player, "sbr", new String[] {""})).containsExactly("info");
        assertThat(command.tabComplete(player, "sbr", new String[] {"grant", ""})).isEmpty();

        PlayerMock admin = admin();
        assertThat(command.tabComplete(admin, "sbr", new String[] {"re"}))
                .containsExactly("reload", "revoke", "reset");
        assertThat(command.tabComplete(admin, "sbr", new String[] {"grant", "st"}))
                .containsExactly("Steve");
        assertThat(command.tabComplete(admin, "sbr", new String[] {"set", ""}))
                .containsExactly("threshold", "radius", "shape", "center");
        assertThat(command.tabComplete(admin, "sbr", new String[] {"set", "shape", "c"}))
                .containsExactly("circle");
        assertThat(command.tabComplete(admin, "sbr", new String[] {"set", "radius", ""})).isEmpty();
    }

    private PlayerMock admin() {
        PlayerMock admin = server.addPlayer("Alex");
        admin.addAttachment(plugin, SpawnBuildRightsPlugin.ADMIN_PERMISSION, true);
        return admin;
    }

    private YamlConfiguration saved() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
    }

    private static String plain(Component component) {
        assertThat(component).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

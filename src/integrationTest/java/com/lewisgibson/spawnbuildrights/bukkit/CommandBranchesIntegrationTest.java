package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Every reply the command can give: usage lines for missing arguments, unknown players and
 * settings, the ways a target can be named, the history lines, and what a reload or a save reports
 * when the store or the configuration will not load.
 */
@DisplayName("SpawnBuildRights command replies (integration)")
class CommandBranchesIntegrationTest {

    private static final long THIRTY_SIX_HOURS = 2_592_000L;

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private ConsoleCommandSenderMock console;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        console = server.getConsoleSender();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("every subcommand that needs more says how to use it")
    void usageLines() {
        assertThat(reply("sbr grant")).isEqualTo("Usage: /sbr grant <player> [reason]");
        assertThat(reply("sbr revoke")).isEqualTo("Usage: /sbr revoke <player> [reason]");
        assertThat(reply("sbr reset")).isEqualTo("Usage: /sbr reset <player>");
        assertThat(reply("sbr history")).isEqualTo("Usage: /sbr history <player>");
        assertThat(reply("sbr set")).startsWith("Usage: /sbr set <threshold|radius|shape|center>");
        assertThat(reply("sbr set threshold")).startsWith("Usage: /sbr set threshold <duration>");
        assertThat(reply("sbr set radius")).isEqualTo("Usage: /sbr set radius <blocks>");
        assertThat(reply("sbr set shape")).isEqualTo("Usage: /sbr set shape <square|circle>");
        assertThat(reply("sbr set shape hexagon")).contains("unknown region shape 'hexagon'");
        assertThat(reply("sbr set colour red")).startsWith("Unknown setting.");
        assertThat(reply("sbr set center here")).contains("Only a player");
        PlayerMock admin = admin("Alex");
        admin.performCommand("sbr set center");
        assertThat(plain(admin.nextComponentMessage())).isEqualTo("Usage: /sbr set center here");
        admin.performCommand("sbr set center there");
        assertThat(plain(admin.nextComponentMessage())).isEqualTo("Usage: /sbr set center here");
    }

    @Test
    @DisplayName("a player nobody has seen cannot be reset or looked up")
    void unknownPlayers() {
        assertThat(reply("sbr reset Nobody")).contains("No player called 'Nobody'");
        assertThat(reply("sbr history Nobody")).contains("No player called 'Nobody'");
    }

    @Test
    @DisplayName("a UUID is accepted, whether or not the player is online")
    void uuidTargets() {
        PlayerMock steve = server.addPlayer("Steve");
        assertThat(reply("sbr grant " + steve.getUniqueId())).startsWith("Steve can build");
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
        // MockBukkit invents a name for any id, so only the server's answer is checked here.
        assertThat(reply("sbr revoke " + stranger)).contains(" cannot build at spawn");
        assertThat(plugin.runtime().grants().registry().override(stranger)).isPresent();
    }

    @Test
    @DisplayName("a player who has left is found in the server's name cache")
    void cachedPlayer() {
        PlayerMock gone = server.addPlayer("Gone");
        gone.disconnect();
        assertThat(reply("sbr grant Gone")).startsWith("Gone can build");
    }

    @Test
    @DisplayName("a player's own grant is recorded under their name")
    void playerActor() {
        PlayerMock admin = admin("Alex");
        server.addPlayer("Steve");
        admin.performCommand("sbr grant Steve");
        admin.nextComponentMessage();
        admin.performCommand("sbr history Steve");
        assertThat(plain(admin.nextComponentMessage())).contains("granted by Alex at");
    }

    @Test
    @DisplayName("history lists earned and reasonless events, and says when there is none")
    void historyLines() {
        PlayerMock steve = server.addPlayer("Steve");
        assertThat(lines("sbr history Steve"))
                .containsExactly("Steve override: none", "History: nothing recorded.");

        steve.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) THIRTY_SIX_HOURS);
        plugin.runtime()
                .grants()
                .recordEarned(steve.getUniqueId(), "Steve", THIRTY_SIX_HOURS + 20L * 60);
        reply("sbr grant Steve");
        List<String> lines = lines("sbr history Steve");
        assertThat(lines.get(0)).endsWith("UTC");
        assertThat(lines).anyMatch(line -> line.endsWith(": granted by console"));
        assertThat(lines).anyMatch(line -> line.endsWith(": earned by playtime at 36h 1m"));
    }

    @Test
    @DisplayName("history says so when queued writes did not finish in time, and still answers")
    void historyWithWritesStillQueued() {
        server.addPlayer("Steve");
        plugin.queueStoreWrite(
                () -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
        List<String> lines = lines("sbr history Steve");
        assertThat(lines).contains("Store writes are still pending; the log may be incomplete.");
    }

    @Test
    @DisplayName("history reports a store that cannot be read instead of failing")
    void historyWithAnUnreadableStore() throws IOException {
        server.addPlayer("Steve");
        Path file = plugin.getDataFolder().toPath().resolve("data.db");
        Files.delete(file);
        Files.createDirectories(file);
        assertThat(lines("sbr history Steve"))
                .anyMatch(line -> line.startsWith("Could not read the store: "));
    }

    @Test
    @DisplayName("reload reports success, and failure with the reason")
    void reload() throws IOException {
        assertThat(reply("sbr reload")).isEqualTo("Configuration reloaded.");
        write("region:\n    radius: lots\n    min-y: lots\n");
        assertThat(reply("sbr reload")).startsWith("Reload failed: region.radius");
        assertThat(reply("sbr set radius 5")).startsWith("Saved, but reload failed: ");
    }

    @Test
    @DisplayName("info reads a one-block radius, a granted node and a reasonless decision")
    void infoDetails() {
        plugin.editConfig(config -> config.set("region.radius", 1));
        PlayerMock steve = server.addPlayer("Steve");
        steve.addAttachment(plugin, "spawnbuildrights.admin", true);
        steve.addAttachment(plugin, "spawnbuildrights.build", true);
        reply("sbr grant Steve");
        steve.performCommand("sbr info");
        List<String> lines = new ArrayList<>();
        Component line;
        while ((line = steve.nextComponentMessage()) != null) {
            lines.add(plain(line));
        }
        assertThat(lines).anyMatch(text -> text.startsWith("Spawn region: square, 1 block around"));
        assertThat(lines).contains("Permission spawnbuildrights.build: true");
        assertThat(lines)
                .anyMatch(text -> text.matches("Manual decision: granted by console at .* UTC"));
        assertThat(lines.getLast()).isEqualTo("You can build at spawn: yes");
    }

    @Test
    @DisplayName("tab completion offers nothing past the arguments it knows")
    void tabCompletionEnds() {
        Command command = plugin.getCommand("spawnbuildrights");
        CommandSender admin = admin("Alex");
        assertThat(command.tabComplete(admin, "sbr", new String[] {"set", "center", ""}))
                .containsExactly("here");
        assertThat(command.tabComplete(admin, "sbr", new String[] {"info", ""})).isEmpty();
        assertThat(command.tabComplete(admin, "sbr", new String[] {"grant", "x", ""})).isEmpty();
        assertThat(command.tabComplete(admin, "sbr", new String[] {"set", "shape", "c", ""}))
                .isEmpty();
    }

    private PlayerMock admin(String name) {
        PlayerMock admin = server.addPlayer(name);
        admin.addAttachment(plugin, SpawnBuildRightsPlugin.ADMIN_PERMISSION, true);
        return admin;
    }

    private String reply(String command) {
        server.dispatchCommand(console, command);
        String first = plain(console.nextComponentMessage());
        while (console.nextComponentMessage() != null) {
            // only the first line is compared
        }
        return first;
    }

    private List<String> lines(String command) {
        server.dispatchCommand(console, command);
        List<String> lines = new ArrayList<>();
        Component line;
        while ((line = console.nextComponentMessage()) != null) {
            lines.add(plain(line));
        }
        return lines;
    }

    private void write(String yaml) throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), "config.yml").toPath(), yaml);
    }

    private static String plain(Component component) {
        assertThat(component).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantService;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import com.lewisgibson.spawnbuildrights.storage.JdbcGrantStore;
import com.lewisgibson.spawnbuildrights.storage.SqliteConnectionSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Starting, stopping and switching stores: a configuration that cannot load at startup disables the
 * plugin, a store that cannot be read is never swapped in, and a stop or a switch that finds writes
 * still queued waits only so long.
 */
@DisplayName("SpawnBuildRights lifecycle (integration)")
class PluginLifecycleIntegrationTest {

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an invalid configuration at startup disables the plugin")
    void invalidConfigurationDisables() throws IOException {
        server.getPluginManager().disablePlugin(plugin);
        write("region:\n    shape: hexagon\n");

        server.getPluginManager().enablePlugin(plugin);

        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("starts with vanilla spawn-protection on, whether or not it is told to warn")
    void startsAlongsideVanillaSpawnProtection() throws IOException {
        server.setSpawnRadius(10);
        for (String warn : new String[] {"true", "false"}) {
            server.getPluginManager().disablePlugin(plugin);
            write("warn-on-vanilla-spawn-protection: " + warn + "\n");
            server.getPluginManager().enablePlugin(plugin);
            assertThat(plugin.isEnabled()).isTrue();
        }
    }

    @Test
    @DisplayName("starts quietly with vanilla spawn-protection off")
    void startsWithVanillaSpawnProtectionOff() {
        server.setSpawnRadius(0);
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        assertThat(plugin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("a store write that fails is reported and the storage thread carries on")
    void aFailedWriteIsReported() {
        plugin.queueStoreWrite(
                () -> {
                    throw new IllegalStateException("disk full");
                });
        assertThat(plugin.awaitStoreWrites(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    @DisplayName("region.world naming a loaded world anchors the region there")
    void regionWorldNamesALoadedWorld() throws IOException {
        World nether = server.addSimpleWorld("world_nether");
        write("region:\n    world: world_nether\n");
        plugin.reloadSettings();

        assertThat(plugin.runtime().settings().region().world()).isEqualTo(nether.getName());
    }

    @Test
    @DisplayName("a store whose rows cannot be read is closed and the running one kept")
    void unreadableStoreIsNotSwappedIn() throws Exception {
        Path broken = plugin.getDataFolder().toPath().resolve("broken.db");
        JdbcGrantStore store = new JdbcGrantStore(new SqliteConnectionSource(broken));
        store.open();
        store.close();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + broken);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO sbr_overrides VALUES ('not-a-uuid', 'x', 'GRANTED', 'c', '', 0)");
        }
        write("storage:\n    sqlite:\n        file: broken.db\n");

        assertThatThrownBy(plugin::reloadSettings).isInstanceOf(IllegalArgumentException.class);
        assertThat(plugin.runtime().settings().storage().sqliteFile()).isEqualTo("data.db");
    }

    @Test
    @DisplayName("a reload whose re-read of the same store fails keeps that store open and running")
    void failedReReadOfTheSameStoreKeepsIt() throws Exception {
        Path data = plugin.getDataFolder().toPath().resolve("data.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + data);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO sbr_overrides VALUES ('not-a-uuid', 'x', 'GRANTED', 'c', '', 0)");
        }

        assertThatThrownBy(plugin::reloadSettings).isInstanceOf(IllegalArgumentException.class);
        assertThat(plugin.runtime().settings().storage().sqliteFile()).isEqualTo("data.db");

        // The store was not closed: the running service can still write and read through it.
        UUID player = UUID.randomUUID();
        plugin.runtime().grants().grant(player, "Steve", "console", "");
        assertThat(plugin.awaitStoreWrites(Duration.ofSeconds(5))).isTrue();
        assertThat(plugin.runtime().grants().history(player, 10)).hasSize(1);
    }

    @Test
    @DisplayName("a store that cannot be opened fails the reload and keeps the running one")
    void unopenableStoreFailsTheReload() throws IOException {
        Files.createDirectories(plugin.getDataFolder().toPath().resolve("adir.db"));
        write("storage:\n    sqlite:\n        file: adir.db\n");

        assertThatThrownBy(plugin::reloadSettings).isInstanceOf(StorageException.class);
        assertThat(plugin.runtime().settings().storage().sqliteFile()).isEqualTo("data.db");
    }

    @Test
    @DisplayName("switching stores waits only so long for writes queued on the old one")
    void switchingStoresWaitsOnlySoLong() throws IOException {
        plugin.storageTimeout(Duration.ofMillis(50));
        blockTheStorageThread();
        write("storage:\n    sqlite:\n        file: other.db\n");

        plugin.reloadSettings();

        assertThat(plugin.runtime().settings().storage().sqliteFile()).isEqualTo("other.db");
    }

    @Test
    @DisplayName("stopping waits only so long for queued writes")
    void stoppingWaitsOnlySoLong() {
        plugin.storageTimeout(Duration.ofMillis(50));
        blockTheStorageThread();

        server.getPluginManager().disablePlugin(plugin);

        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("a grant made through a service read just before a store switch is not lost")
    void aGrantRacingAStoreSwitchIsKept() throws IOException {
        // A region thread reads the runtime, then the console switches stores, then that thread
        // grants: on Folia nothing orders the two, so the grant goes to the old service.
        GrantService before = plugin.runtime().grants();
        write("storage:\n    sqlite:\n        file: other.db\n");
        plugin.reloadSettings();
        UUID steve = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

        before.grant(steve, "Steve", "console", "raced the reload");

        assertThat(plugin.runtime().grants().registry().state(steve))
                .isEqualTo(OverrideState.GRANTED);
        assertThat(plugin.awaitStoreWrites(Duration.ofSeconds(5))).isTrue();
        JdbcGrantStore other =
                new JdbcGrantStore(
                        new SqliteConnectionSource(
                                plugin.getDataFolder().toPath().resolve("other.db")));
        other.open();
        assertThat(other.loadOverrides()).containsKey(steve);
        assertThat(other.history(steve, 10)).hasSize(1);
        assertThat(before.history(steve, 10)).hasSize(1);
        other.close();
    }

    private void blockTheStorageThread() {
        plugin.queueStoreWrite(
                () -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
    }

    private void write(String yaml) throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), "config.yml").toPath(), yaml);
    }
}

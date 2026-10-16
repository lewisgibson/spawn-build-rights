package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * How config.yml is read: a value of the wrong kind fails the load with the key's name instead of
 * quietly falling back to a default, and a key the plugin does not know is reported.
 */
@DisplayName("SpawnBuildRights config codec (integration)")
class ConfigCodecIntegrationTest {

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        world.setSpawnLocation(new Location(world, 40, 70, -8));
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "region.radius | 16a | must be a whole number",
                "region.min-y | 12.5 | must be a whole number",
                "storage.postgres.port | five | must be a whole number",
                "protection.explosions | flase | must be true or false",
                "protection.portal-arrivals.fireworks | 1 | must be true or false",
                "warn-on-vanilla-spawn-protection | maybe | must be true or false",
                "protection.portal-arrivals | sometimes | must be true, false or a section",
            })
    void aValueOfTheWrongKindFailsNamingItsKey(String key, String value, String message)
            throws IOException {
        write(key + ": " + value);

        assertThatThrownBy(plugin::reloadSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key)
                .hasMessageContaining(message);
    }

    @Test
    @DisplayName("a failed reload keeps the settings that were running")
    void aFailedReloadKeepsTheRunningSettings() throws IOException {
        write("region:\n    radius: 20");
        plugin.reloadSettings();
        write("region:\n    radius: twenty");

        assertThatThrownBy(plugin::reloadSettings).isInstanceOf(IllegalArgumentException.class);
        assertThat(plugin.runtime().settings().region().radius()).isEqualTo(20);
    }

    @Test
    @DisplayName("whole numbers written as quoted text still read as numbers")
    void quotedNumbersStillRead() throws IOException {
        write("region:\n    radius: '24'\n    center-x: '5'\n    center-z: '-5'");
        plugin.reloadSettings();

        assertThat(plugin.runtime().settings().region().radius()).isEqualTo(24);
        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(5);
        assertThat(plugin.runtime().settings().region().centerZ()).isEqualTo(-5);
    }

    @Test
    @DisplayName("a blank centre follows the world spawn, and so does a missing one")
    void blankCentreFollowsSpawn() throws IOException {
        write("region:\n    center-x: ''\n    center-z: ''");
        plugin.reloadSettings();
        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(40);
        assertThat(plugin.runtime().settings().region().centerZ()).isEqualTo(-8);

        write("required-playtime: 1h");
        plugin.reloadSettings();
        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(40);
    }

    @Test
    @DisplayName("half a centre is refused rather than mixed with the world spawn")
    void halfACentreIsRefused() throws IOException {
        write("region:\n    center-x: 100\n    center-z: ''");

        assertThatThrownBy(plugin::reloadSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }

    @Test
    @DisplayName("a non-numeric centre is refused rather than silently following the spawn")
    void aNonNumericCentreIsRefused() throws IOException {
        write("region:\n    center-x: 1o0\n    center-z: 0");

        assertThatThrownBy(plugin::reloadSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region.center-x");
    }

    @Test
    @DisplayName("misspelt keys are listed once each, a misspelt section once for all its keys")
    void unknownKeysAreReported() throws IOException {
        write(
                "requried-playtime: 1h\n"
                        + "region:\n    radius: 10\n    radious: 12\n"
                        + "protection:\n    explosion: false\n    portal-arrivals: false\n"
                        + "storag:\n    type: sqlite\n    sqlite:\n        file: x.db\n");
        plugin.reloadSettings();

        assertThat(plugin.codec().unknownKeys(plugin.getConfig()))
                .containsExactly(
                        "requried-playtime", "region.radious", "protection.explosion", "storag");
    }

    @Test
    @DisplayName("the shipped config.yml has no unknown keys")
    void shippedConfigIsAllKnown() {
        assertThat(plugin.codec().unknownKeys(plugin.getConfig())).isEmpty();
    }

    private void write(String yaml) throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), "config.yml").toPath(), yaml + "\n");
    }

    @Test
    @DisplayName("with no shipped defaults to compare against, nothing is unknown and blank lines")
    void withoutDefaults() {
        YamlConfiguration bare = new YamlConfiguration();
        bare.set("anything", 1);
        assertThat(plugin.codec().unknownKeys(bare)).isEmpty();
        assertThat(plugin.codec().load(bare, "world", 0, 0).messages().buildDenied()).isEmpty();
    }
}

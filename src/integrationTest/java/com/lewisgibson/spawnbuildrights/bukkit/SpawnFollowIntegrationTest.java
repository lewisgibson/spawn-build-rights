package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.world.SpawnChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A region whose centre is left blank follows the world spawn when it moves, with no reload; one
 * with a configured centre, or in another world, stays where it is.
 */
@DisplayName("SpawnBuildRights region follows the world spawn (integration)")
class SpawnFollowIntegrationTest {

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        world.setSpawnLocation(new Location(world, 0, 70, 0));
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a blank centre moves to the new spawn at once, keeping the store and its grants")
    void blankCentreFollows() {
        PlayerMock steve = server.addPlayer("Steve");
        server.dispatchCommand(server.getConsoleSender(), "sbr grant Steve");

        moveSpawn(world, 1000, -500);

        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(1000);
        assertThat(plugin.runtime().settings().region().centerZ()).isEqualTo(-500);
        assertThat(plugin.runtime().settings().region().contains("world", 1000, 70, -500)).isTrue();
        assertThat(plugin.runtime().settings().region().contains("world", 0, 70, 0)).isFalse();
        assertThat(plugin.runtime().grants().registry().state(steve.getUniqueId()))
                .isEqualTo(OverrideState.GRANTED);
    }

    @Test
    @DisplayName("a spawn change that does not move the centre block changes nothing")
    void sameBlockChangesNothing() {
        PluginRuntime before = plugin.runtime();
        world.setSpawnLocation(new Location(world, 0.7, 75, 0.2));
        server.getPluginManager().callEvent(new SpawnChangeEvent(world, world.getSpawnLocation()));
        assertThat(plugin.runtime()).isSameAs(before);
    }

    @Test
    @DisplayName("a spawn moved along one axis only still moves the region")
    void oneAxisMove() {
        moveSpawn(world, 0, 300);
        assertThat(plugin.runtime().settings().region().centerZ()).isEqualTo(300);
    }

    @Test
    @DisplayName("a configured centre stays where it was configured")
    void configuredCentreStays() {
        plugin.editConfig(
                config -> {
                    config.set("region.center-x", 40);
                    config.set("region.center-z", 40);
                });

        moveSpawn(world, 1000, -500);

        assertThat(plugin.runtime().settings().region().centerX()).isEqualTo(40);
    }

    @Test
    @DisplayName("a spawn moving in another world leaves the region alone")
    void otherWorldIgnored() {
        World nether = server.addSimpleWorld("world_nether");

        moveSpawn(nether, 1000, -500);

        assertThat(plugin.runtime().settings().region().centerX()).isZero();
    }

    private void moveSpawn(World target, int x, int z) {
        Location previous = target.getSpawnLocation();
        target.setSpawnLocation(new Location(target, x, 70, z));
        server.getPluginManager().callEvent(new SpawnChangeEvent(target, previous));
    }
}

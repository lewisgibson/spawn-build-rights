package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;

/**
 * The per-tick watch on a projectile a dispenser outside the region fires, driven tick by tick on
 * the mocked server's scheduler: it removes the projectile on the tick it would enter, and stops
 * watching once the projectile no longer needs it.
 */
@DisplayName("SpawnBuildRights dispensed projectile watch (integration)")
class ProjectileWatchIntegrationTest {

    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        plugin.getConfig().set("region.center-x", 0);
        plugin.getConfig().set("region.center-z", 0);
        plugin.getConfig().set("region.radius", 16);
        reload();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a projectile is left alone while it stays outside and removed as it enters")
    void removedOnTheTickItWouldEnter() {
        TestArrow arrow = launched(arrow(24.5), dispenserAt(26));

        server.getScheduler().performTicks(2);
        assertThat(arrow.isValid()).isTrue();

        arrow.setLocation(at(17.3));
        server.getScheduler().performTicks(1);
        assertThat(arrow.isValid()).isFalse();
    }

    @Test
    @DisplayName("the watch ends once dispensers is switched off, and the projectile flies on")
    void endsWhenTheProtectionIsOff() {
        Snowball snowball = launched(world.spawn(at(24.5), Snowball.class), dispenserAt(26));
        plugin.getConfig().set("protection.dispensers", false);
        reload();
        server.getScheduler().performTicks(1);

        plugin.getConfig().set("protection.dispensers", true);
        reload();
        snowball.teleport(at(17.3));
        server.getScheduler().performTicks(1);
        assertThat(snowball.isValid()).isTrue();
    }

    @Test
    @DisplayName("an arrow stuck in a block outside is no longer watched")
    void endsWhenTheArrowIsStuck() {
        TestArrow arrow = launched(arrow(24.5), dispenserAt(26));
        arrow.stuck = true;
        server.getScheduler().performTicks(1);

        arrow.stuck = false;
        arrow.setLocation(at(17.3));
        server.getScheduler().performTicks(1);
        assertThat(arrow.isValid()).isTrue();
    }

    @Test
    @DisplayName("a projectile with no shooter yet is checked once and then let go")
    void aShooterlessProjectileIsLetGo() {
        TestArrow arrow = arrow(24.5);
        arrow.setVelocity(new Vector(-0.5, 0, 0));
        assertThat(call(arrow).isCancelled()).isFalse();
        server.getScheduler().performTicks(1);

        arrow.setShooter(dispenserAt(26));
        arrow.setLocation(at(17.3));
        server.getScheduler().performTicks(1);
        assertThat(arrow.isValid()).isTrue();
    }

    @Test
    @DisplayName("nothing is watched while dispensers is off")
    void nothingWatchedWhileOff() {
        plugin.getConfig().set("protection.dispensers", false);
        reload();
        TestArrow arrow = arrow(17.2);
        arrow.setVelocity(new Vector(-1.1, 0, 0));
        arrow.setShooter(dispenserAt(18));
        assertThat(call(arrow).isCancelled()).isFalse();
    }

    /**
     * An arrow the test can mark as stuck in a block; MockBukkit's own arrow does not implement
     * isInBlock.
     */
    private static final class TestArrow extends ArrowMock {
        private boolean stuck;

        TestArrow(ServerMock server) {
            super(server, UUID.randomUUID());
        }

        @Override
        public boolean isInBlock() {
            return stuck;
        }
    }

    private <T extends Projectile> T launched(T projectile, BlockProjectileSource dispenser) {
        projectile.setVelocity(new Vector(-0.5, 0, 0));
        projectile.setShooter(dispenser);
        assertThat(call(projectile).isCancelled()).isFalse();
        return projectile;
    }

    private ProjectileLaunchEvent call(Projectile projectile) {
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(projectile);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private TestArrow arrow(double x) {
        TestArrow arrow = new TestArrow(server);
        arrow.setLocation(at(x));
        return arrow;
    }

    private Location at(double x) {
        return new Location(world, x, 70.5, 0.5);
    }

    private BlockProjectileSource dispenserAt(int x) {
        Block block = world.getBlockAt(x, 70, 0);
        block.setType(Material.DISPENSER);
        return new BlockProjectileSource() {
            @Override
            public Block getBlock() {
                return block;
            }

            @Override
            public <T extends Projectile> T launchProjectile(
                    Class<? extends T> projectile,
                    Vector velocity,
                    java.util.function.Consumer<? super T> function) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void reload() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }
}

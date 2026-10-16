package com.lewisgibson.spawnbuildrights.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The plugin on a real Paper 26.3 server, once per storage backend: it enables, opens its store,
 * and the gate does what the unit tests say it does - observed through the server's own event
 * pipeline and commands over RCON, with nothing of the plugin's internals reached into.
 */
@DisplayName("SpawnBuildRights on a real Paper server")
class PaperServerTest {

    private static final Path PLUGIN_JAR = Path.of(System.getProperty("sbr.pluginJar"));
    private static final Path FIXTURE_JAR = Path.of(System.getProperty("sbr.fixtureJar"));
    private static final Path WORK = Path.of(System.getProperty("sbr.workDir"));
    private static final String VERSION = System.getProperty("sbr.version");
    private static final long ONE_HOUR = 72_000L;

    /** The offline-mode id of the fixture's TestPlayer, the same derivation the fixture uses. */
    private static final UUID TEST_PLAYER =
            UUID.nameUUIDFromBytes("OfflinePlayer:TestPlayer".getBytes(StandardCharsets.UTF_8));

    @BeforeAll
    static void requireDocker() {
        Docker.requireDocker();
        assertThat(PLUGIN_JAR).exists();
        assertThat(FIXTURE_JAR).exists();
    }

    @org.junit.jupiter.api.Test
    void protectsBuildsAgainstActualExplosionsAndWitherAttacks() throws Exception {
        Path data = Files.createDirectories(WORK.resolve("environment"));
        try (PaperServer paper = new PaperServer(data)) {
            paper.install(
                    PLUGIN_JAR,
                    FIXTURE_JAR,
                    "region:\n    world: world\n    center-x: 0\n    center-z: 0\n    radius: 16\n"
                            + "    min-y: 60\n    max-y: 90\nrequired-playtime: 1h\n");
            assertThat(paper.start()).contains("Enabling SpawnBuildRights v" + VERSION);
            assertThat(paper.rcon("sbrtest environment explosion"))
                    .isEqualTo(
                            "inside=WHITE_WOOL outside=AIR chest=true stand=true helmet=true frame=true painting=true outsideStand=false cowHurt=true");
            assertThat(paper.rcon("sbrtest environment skull-inside")).isEqualTo("pending");
            assertThat(awaitEnvironment(paper))
                    .isEqualTo("impact=true affected=true block=SLIME_BLOCK");
            assertThat(paper.rcon("sbrtest environment skull-outside")).isEqualTo("pending");
            assertThat(awaitEnvironment(paper)).isEqualTo("impact=true affected=true block=AIR");
            assertThat(paper.rcon("sbrtest environment wither")).isEqualTo("pending");
            assertThat(awaitEnvironment(paper))
                    .isEqualTo("changed=true inside=WHITE_WOOL outside=AIR");
            assertThat(paper.rcon("sbrtest environment blaze-inside")).isEqualTo("pending");
            assertThat(awaitEnvironment(paper)).isEqualTo("ignited=true cancelled=true block=AIR");
            assertThat(paper.rcon("sbrtest environment blaze-outside")).isEqualTo("pending");
            assertThat(awaitEnvironment(paper))
                    .isEqualTo("ignited=true cancelled=false block=FIRE");
            for (String action : java.util.List.of("pickup", "place")) {
                assertThat(paper.rcon("sbrtest environment enderman-" + action + "-inside"))
                        .isEqualTo(
                                "attempted=true cancelled=true block="
                                        + (action.equals("pickup")
                                                ? "DIRT carrying=false"
                                                : "AIR carrying=true"));
                assertThat(paper.rcon("sbrtest environment enderman-" + action + "-outside"))
                        .isEqualTo(
                                "attempted=true cancelled=false block="
                                        + (action.equals("pickup")
                                                ? "AIR carrying=true"
                                                : "DIRT carrying=false"));
            }
            for (String origin : java.util.List.of("inside", "outside")) {
                assertThat(paper.rcon("sbrtest environment tnt-" + origin)).isEqualTo("pending");
                assertThat(awaitEnvironment(paper))
                        .isEqualTo("exploded=true inside=WHITE_WOOL outside=AIR");
            }
            assertThat(paper.rcon("sbrtest environment tnt-projectile-inside"))
                    .isEqualTo("pending");
            assertThat(awaitEnvironment(paper))
                    .isEqualTo("attempted=true cancelled=true block=TNT primed=false");
            assertThat(paper.rcon("sbrtest environment tnt-projectile-outside"))
                    .isEqualTo("pending");
            assertThat(awaitEnvironment(paper))
                    .isEqualTo("attempted=true cancelled=false block=AIR primed=true");
            assertThat(paper.rcon("sbrtest environment fire-boundary"))
                    .isEqualTo(
                            "insideBurn=true outsideBurn=true insideSpread=true outsideSpread=true protectedWood=true protectedAir=true outsideChanged=true outsideFire=true");
        }
    }

    @org.junit.jupiter.api.Test
    void stopsFallingBlocksFluidsPistonsDispensersAndTreesReachingInFromOutside() throws Exception {
        Path data = Files.createDirectories(WORK.resolve("outside-changes"));
        try (PaperServer paper = new PaperServer(data)) {
            String region =
                    "region:\n    world: world\n    center-x: 0\n    center-z: 0\n    radius: 16\n"
                            + "    min-y: 60\n    max-y: 90\nrequired-playtime: 1h\n";
            paper.install(PLUGIN_JAR, FIXTURE_JAR, region);
            assertThat(paper.start()).contains("Enabling SpawnBuildRights v" + VERSION);

            // Sand carried in from outside drops as an item where it would have landed.
            assertThat(outside(paper, "falling"))
                    .isEqualTo("carried=AIR inside=SAND outside=SAND drops=SAND@inside");
            assertThat(outside(paper, "fluid"))
                    .isEqualTo("lavaOutside=LAVA lavaInside=AIR waterInside=WATER waterOut=WATER");
            // A piston outside that would push in does not move at all. A sticky one that would
            // pull a block out retracts its own head and leaves the block, as it does from
            // obsidian. One inside pushes as normal.
            assertThat(outside(paper, "pistons"))
                    .isEqualTo(
                            "pushIn=AIR/STONE pushInPiston=retracted headIn=AIR"
                                    + " pushOutside=STONE pullOut=STONE pullPiston=retracted/AIR"
                                    + " pullOutside=STONE insidePush=STONE/PISTON_HEAD");
            assertThat(outside(paper, "dispensers"))
                    .isEqualTo("inside=AIR kept=true outside=LAVA used=true");
            // Arrows from dispensers outside are gone before they enter; a dispenser inside and a
            // skeleton outside shoot as normal.
            assertThat(outside(paper, "projectiles"))
                    .isEqualTo(
                            "fired=true outsideDispensersInside=0 outsideDispensersLeft=0"
                                    + " ownInside=1 skeletonInside=1");
            // A tree and a huge mushroom beside the region grow, without their blocks inside it.
            assertThat(paper.rcon("sbrtest environment outside-tree"))
                    .isEqualTo("grown=true inside=0 outside=true");
            assertThat(paper.rcon("sbrtest environment outside-mushroom"))
                    .isEqualTo("grown=true inside=0 outside=true");

            // The same scenarios with the five switches off reach in: they are real attempts.
            Files.writeString(
                    data.resolve("plugins/SpawnBuildRights/config.yml"),
                    region
                            + "protection:\n    falling-blocks: false\n    fluid-flow: false\n"
                            + "    pistons: false\n    dispensers: false\n    growth: false\n");
            assertThat(paper.rcon("sbr reload")).contains("reloaded");
            assertThat(outside(paper, "falling"))
                    .startsWith("carried=SAND ")
                    .endsWith(" drops=none");
            assertThat(outside(paper, "fluid")).contains("lavaInside=LAVA ");
            assertThat(outside(paper, "pistons"))
                    .startsWith("pushIn=STONE/")
                    .contains("headIn=PISTON_HEAD ")
                    .contains("pullOut=AIR ");
            assertThat(outside(paper, "dispensers")).startsWith("inside=LAVA kept=false ");
            assertThat(outside(paper, "projectiles"))
                    .isEqualTo(
                            "fired=true outsideDispensersInside=2 outsideDispensersLeft=2"
                                    + " ownInside=1 skeletonInside=1");
            assertThat(paper.rcon("sbrtest environment outside-tree"))
                    .startsWith("grown=true ")
                    .doesNotContain("inside=0 ");
            assertThat(paper.rcon("sbrtest environment outside-mushroom"))
                    .startsWith("grown=true ")
                    .doesNotContain("inside=0 ");
        }
    }

    private static String outside(PaperServer paper, String scenario) throws Exception {
        assertThat(paper.rcon("sbrtest environment outside-" + scenario))
                .as(scenario)
                .isEqualTo("pending");
        return awaitEnvironment(paper);
    }

    /** What a defused entity reports: it entered, never arrived, and nothing at spawn changed. */
    private static final String DEFUSED =
            "entered=true arrived=false removed=PLUGIN exitCancelled=true overworldExplosions=0"
                    + " overworldChanged=0 otherExplosions=0 sentinelHurt=false overworldItems=none";

    /** A defused falling block: the same, except that its item lies at spawn. */
    private static final String DEFUSED_TO_ITEM =
            DEFUSED.replace("overworldItems=none", "overworldItems=SAND@spawn");

    /**
     * What an entity carried as normal reports: it entered and arrived, it was not removed by the
     * plugin, and its exit went ahead. The explosion counts depend on the entity.
     */
    private static final String CARRIED =
            "entered=true arrived=true removed=(?!PLUGIN)\\S+ exitCancelled=false .*";

    /** Each switch and a scenario of its kind, to show the switches are independent. */
    private static final java.util.Map<String, String> KINDS =
            new java.util.LinkedHashMap<>(
                    java.util.Map.of(
                            "explosives", "portal-tnt",
                            "wither-skulls", "portal-skull-steep",
                            "fireballs", "portal-fireball",
                            "projectiles", "portal-nether-arrow",
                            "fireworks", "portal-star-rocket",
                            "falling-blocks", "portal-sand"));

    @org.junit.jupiter.api.Test
    void defusesExplosivesAndProjectilesThatAPortalWouldDeliverIntoTheRegion() throws Exception {
        Path data = Files.createDirectories(WORK.resolve("portal-arrivals"));
        try (PaperServer paper = new PaperServer(data)) {
            String region =
                    "region:\n    world: world\n    center-x: 0\n    center-z: 0\n    radius: 16\n"
                            + "required-playtime: 1h\n";
            paper.install(PLUGIN_JAR, FIXTURE_JAR, region);
            assertThat(paper.start()).contains("Enabling SpawnBuildRights v" + VERSION);

            // Wither skulls into the End exit portal, steep to flat, black and blue, and the
            // other explosives, a star rocket and an arrow through a nether portal at spawn.
            for (String angle : java.util.List.of("steep", "45", "shallow", "horizontal")) {
                for (String colour : java.util.List.of("", "-blue")) {
                    String scenario = "portal-skull-" + angle + colour;
                    assertThat(portal(paper, scenario)).as(scenario).isEqualTo(DEFUSED);
                }
            }
            for (String scenario :
                    java.util.List.of(
                            "portal-tnt",
                            "portal-tnt-minecart",
                            "portal-fireball",
                            "portal-star-rocket",
                            "portal-splash-potion",
                            "portal-poison-lingering",
                            "portal-tipped-arrow",
                            "portal-nether-arrow")) {
                assertThat(portal(paper, scenario)).as(scenario).isEqualTo(DEFUSED);
            }
            // Sand dropped into the End's exit portal reaches spawn as an item, not a pile.
            assertThat(portal(paper, "portal-sand")).isEqualTo(DEFUSED_TO_ITEM);

            // Items, mobs, a plain rocket, a snowball, a healing potion and a wind charge arrive,
            // and
            // so does a skull
            // whose landing is outside the region.
            for (String scenario :
                    java.util.List.of(
                            "portal-item",
                            "portal-cow",
                            "portal-plain-rocket",
                            "portal-snowball",
                            "portal-healing-potion",
                            "portal-wind-charge",
                            "portal-skull-steep-outside")) {
                assertThat(portal(paper, scenario)).as(scenario).matches(CARRIED);
            }
            // A star rocket boosting a glider is never touched, and the glider arrives.
            assertThat(portal(paper, "portal-boost"))
                    .doesNotContain("rocket.removed=PLUGIN")
                    .contains("rocket.exitCancelled=false")
                    .contains("rider.arrived=true");

            // Each switch on its own: its kind arrives while the next kind is still defused.
            java.util.List<String> keys = java.util.List.copyOf(KINDS.keySet());
            for (int i = 0; i < keys.size(); i++) {
                String off = keys.get(i);
                String still = keys.get((i + 1) % keys.size());
                Files.writeString(
                        data.resolve("plugins/SpawnBuildRights/config.yml"),
                        region + "protection:\n    portal-arrivals:\n        " + off + ": false\n");
                assertThat(paper.rcon("sbr reload")).contains("reloaded");
                String carried = portal(paper, KINDS.get(off));
                assertThat(carried).as(off + " off").matches(CARRIED);
                if (off.equals("falling-blocks")) {
                    // Through the portal, but its landing is still turned into an item by
                    // falling-blocks.
                    assertThat(carried)
                            .contains("overworldChanged=0 ")
                            .endsWith(" overworldItems=SAND@spawn");
                }
                assertThat(portal(paper, KINDS.get(still)))
                        .as(still + " with " + off + " off")
                        .isEqualTo(still.equals("falling-blocks") ? DEFUSED_TO_ITEM : DEFUSED);
            }

            // Everything off, explosion protection too: vanilla, and the skull blows up spawn.
            Files.writeString(
                    data.resolve("plugins/SpawnBuildRights/config.yml"),
                    region + "protection:\n    explosions: false\n    portal-arrivals: false\n");
            assertThat(paper.rcon("sbr reload")).contains("reloaded");
            assertThat(portal(paper, "portal-skull-steep"))
                    .matches(CARRIED)
                    .contains("overworldExplosions=1 ")
                    .doesNotContain("overworldChanged=0 ")
                    .contains("otherExplosions=0");
        }
    }

    private static String portal(PaperServer paper, String scenario) throws Exception {
        assertThat(paper.rcon("sbrtest environment " + scenario)).as(scenario).isEqualTo("pending");
        return awaitEnvironment(paper);
    }

    private static String awaitEnvironment(PaperServer paper) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
        String result;
        do {
            result = paper.rcon("sbrtest environment status");
            if (!result.equals("pending")) {
                return result;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        return result;
    }

    @ParameterizedTest(name = "storage: {0}")
    @ValueSource(strings = {"sqlite", "postgres", "postgres-tls", "mariadb", "mysql"})
    void gateOnRealServer(String backend) throws Exception {
        Path data = Files.createDirectories(WORK.resolve(backend));
        MySqlServer.Flavour flavour =
                backend.equals("mysql") ? MySqlServer.Flavour.MYSQL : MySqlServer.Flavour.MARIADB;
        try (PostgresServer postgres = new PostgresServer();
                MySqlServer mysql = new MySqlServer(flavour);
                PaperServer paper = new PaperServer(data)) {
            String storage;
            if (backend.equals("mariadb") || backend.equals("mysql")) {
                mysql.start();
                storage =
                        """
                        storage:
                            type: %s
                            mysql:
                                host: %s
                                port: 3306
                                database: %s
                                user: %s
                                password: %s
                        """
                                .formatted(
                                        backend,
                                        mysql.bridgeAddress(),
                                        MySqlServer.DATABASE,
                                        MySqlServer.USER,
                                        MySqlServer.PASSWORD);
            } else if (backend.startsWith("postgres")) {
                postgres.start();
                boolean tls = backend.equals("postgres-tls");
                if (tls) {
                    postgres.requireTls(WORK.resolve(backend + "-certificates"));
                }
                // With TLS, pg_hba refuses every connection that is not TLS, and verify-full
                // checks the certificate against the test CA and the host against its address.
                storage =
                        """
                        storage:
                            type: postgres
                            postgres:
                                host: %s
                                port: 5432
                                database: %s
                                user: %s
                                password: %s
                                ssl-mode: %s
                                ssl-root-cert: '%s'
                        """
                                .formatted(
                                        postgres.bridgeAddress(),
                                        PostgresServer.DATABASE,
                                        PostgresServer.USER,
                                        PostgresServer.PASSWORD,
                                        tls ? "verify-full" : "disable",
                                        tls ? "/data/plugins/SpawnBuildRights/ca.crt" : "");
            } else {
                storage = "storage:\n    type: sqlite\n";
            }
            paper.install(
                    PLUGIN_JAR,
                    FIXTURE_JAR,
                    "region:\n    world: world\nrequired-playtime: 1h\n" + storage);
            if (backend.equals("postgres-tls")) {
                Files.copy(
                        postgres.caCertificate(), data.resolve("plugins/SpawnBuildRights/ca.crt"));
            }

            String boot = paper.start();
            assertThat(boot).contains("Enabling SpawnBuildRights v");
            assertThat(boot)
                    .contains(
                            "[SpawnBuildRights] Store: "
                                    + switch (backend) {
                                        case "postgres", "postgres-tls" -> "postgres";
                                        case "mariadb", "mysql" -> "mysql";
                                        default -> backend;
                                    });
            if (backend.equals("postgres-tls")) {
                assertThat(boot).contains("sslmode verify-full");
            }
            assertThat(boot).doesNotContain("Error occurred while enabling SpawnBuildRights");
            assertThat(boot).contains("Enabling SpawnBuildRightsTestFixture");

            // Below the threshold: stopped, and told on the action bar how long is left.
            String denied = paper.rcon("sbrtest break " + (ONE_HOUR - 20L * 60 * 15));
            assertThat(denied)
                    .contains("cancelled=true")
                    .contains("after another 15 minutes of play");

            // At the threshold: allowed, nothing shown.
            assertThat(paper.rcon("sbrtest break " + ONE_HOUR))
                    .isEqualTo("cancelled=false actionbar=-");

            // A block changed without placing or breaking one: trampling farmland, a flower pot.
            assertThat(paper.rcon("sbrtest change 0")).isEqualTo("trample=true pot=true");
            assertThat(paper.rcon("sbrtest change " + ONE_HOUR))
                    .isEqualTo("trample=false pot=false");

            // The permission node, both ways, through the server's own Permissible calls.
            assertThat(paper.rcon("sbrtest break 0 granted")).startsWith("cancelled=false");
            assertThat(paper.rcon("sbrtest break " + ONE_HOUR + " revoked"))
                    .contains("cancelled=true")
                    .contains("removed");

            // A by-hand revocation through the real command and the real store beats playtime.
            String revoke = paper.rcon("sbr revoke " + TEST_PLAYER + " griefing at spawn");
            assertThat(revoke).contains("cannot build at spawn");
            assertThat(paper.rcon("sbrtest break " + ONE_HOUR * 100))
                    .contains("cancelled=true")
                    .contains("removed");

            String history = paper.rcon("sbr history " + TEST_PLAYER);
            assertThat(history).contains("revoked by console").contains("griefing at spawn");

            assertThat(paper.rcon("sbr reset " + TEST_PLAYER)).contains("back to playtime");
            assertThat(paper.rcon("sbrtest break " + ONE_HOUR)).startsWith("cancelled=false");

            // A reload on the live server keeps the store and applies a changed threshold.
            Files.writeString(
                    data.resolve("plugins/SpawnBuildRights/config.yml"),
                    "region:\n    world: world\nrequired-playtime: 2h\n" + storage);
            assertThat(paper.rcon("sbr reload")).contains("reloaded");
            assertThat(paper.rcon("sbrtest break " + ONE_HOUR)).contains("cancelled=true");

            // The centre is blank, so /setworldspawn moves the region with no reload: a break at
            // the new spawn is refused and one at the old spawn is not.
            String[] old = paper.rcon("sbrtest spawn").split(" ");
            assertThat(paper.rcon("minecraft:setworldspawn 1000 70 1000")).contains("1000");
            assertThat(paper.rcon("sbrtest breakat 1000 70 1000 0")).isEqualTo("cancelled=true");
            assertThat(paper.rcon("sbrtest breakat " + String.join(" ", old) + " 0"))
                    .isEqualTo("cancelled=false");
            assertThat(paper.logs()).contains("the region is now centred on 1000, 1000 in world");

            assertThat(paper.logs()).doesNotContain("A store write failed");
            if (backend.equals("sqlite")) {
                assertThat(data.resolve("plugins/SpawnBuildRights/data.db")).exists();
            }
        }
    }
}

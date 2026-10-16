package com.lewisgibson.spawnbuildrights.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.FireworkMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Explosives, projectiles and falling blocks that a portal would deliver into the region are
 * removed on the way; everything else, and everything landing outside it, still arrives.
 */
class PortalArrivalIntegrationTest {
    private ServerMock server;
    private SpawnBuildRightsPlugin plugin;
    private World world;
    private World end;
    private Location inside;
    private Location outside;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        end = server.addSimpleWorld("world_the_end");
        plugin = MockBukkit.load(SpawnBuildRightsPlugin.class);
        plugin.getConfig().set("region.center-x", 0);
        plugin.getConfig().set("region.center-z", 0);
        plugin.getConfig().set("region.radius", 16);
        plugin.getConfig().set("region.min-y", 60);
        plugin.getConfig().set("region.max-y", 90);
        reload();
        inside = new Location(world, 16.5, 64, -15.5);
        outside = new Location(world, 17.5, 64, 0.5);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    /** One of every kind, keyed by its configuration switch. */
    private Map<String, List<Function<Location, Entity>>> dangerous() {
        Map<String, List<Function<Location, Entity>>> kinds = new LinkedHashMap<>();
        kinds.put(
                "explosives",
                List.of(
                        at -> at.getWorld().spawn(at, TNTPrimed.class),
                        at -> at.getWorld().spawn(at, ExplosiveMinecart.class)));
        kinds.put(
                "wither-skulls",
                List.of(
                        at -> at.getWorld().spawn(at, WitherSkull.class),
                        at ->
                                at.getWorld()
                                        .spawn(
                                                at,
                                                WitherSkull.class,
                                                skull -> skull.setCharged(true))));
        kinds.put(
                "fireballs",
                List.of(
                        at -> at.getWorld().spawn(at, LargeFireball.class),
                        at -> at.getWorld().spawn(at, SmallFireball.class),
                        at -> at.getWorld().spawn(at, DragonFireball.class)));
        kinds.put(
                "projectiles",
                List.of(
                        at -> at.getWorld().spawn(at, Arrow.class),
                        at ->
                                at.getWorld()
                                        .spawn(
                                                at,
                                                Arrow.class,
                                                arrow ->
                                                        arrow.setBasePotionType(
                                                                PotionType.HARMING)),
                        at -> at.getWorld().spawn(at, SpectralArrow.class),
                        at -> at.getWorld().spawn(at, Trident.class),
                        at -> potion(at, SplashPotion.class, PotionType.HARMING, null),
                        at -> potion(at, LingeringPotion.class, PotionType.POISON, null),
                        at ->
                                potion(
                                        at,
                                        SplashPotion.class,
                                        PotionType.WATER,
                                        new PotionEffect(PotionEffectType.WITHER, 100, 0)),
                        // No base type at all, only a harmful custom effect.
                        at ->
                                potion(
                                        at,
                                        SplashPotion.class,
                                        null,
                                        new PotionEffect(PotionEffectType.POISON, 100, 0)),
                        at -> at.getWorld().spawn(at, LlamaSpit.class),
                        at -> at.getWorld().spawn(at, ShulkerBullet.class)));
        kinds.put("fireworks", List.of(at -> rocket(at, true)));
        kinds.put(
                "falling-blocks",
                List.of(
                        at -> at.getWorld().spawnFallingBlock(at, Material.SAND.createBlockData()),
                        at ->
                                at.getWorld()
                                        .spawnFallingBlock(at, Material.ANVIL.createBlockData())));
        return kinds;
    }

    @Test
    void defusesEveryKindLandingInTheRegion() {
        dangerous()
                .forEach(
                        (key, spawners) -> {
                            for (Function<Location, Entity> spawner : spawners) {
                                Entity entity = spawner.apply(endPortal());
                                EntityPortalExitEvent exit = exit(entity, inside);
                                assertThat(exit.isCancelled()).as(key).isTrue();
                                assertThat(entity.isValid()).as(key).isFalse();
                            }
                        });
    }

    @Test
    void aFallingBlockArrivesAsItsItemInsteadOfLanding() {
        Entity sand = end.spawnFallingBlock(endPortal(), Material.SAND.createBlockData());
        assertThat(exit(sand, inside).isCancelled()).isTrue();
        assertThat(sand.isValid()).isFalse();
        List<org.bukkit.entity.Item> items =
                world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().toList();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getItemStack().getType()).isEqualTo(Material.SAND);
        assertThat(items.getFirst().getLocation().toVector()).isEqualTo(inside.toVector());
        assertThat(end.getEntitiesByClass(Item.class)).isEmpty();
    }

    @Test
    void leavesEverythingLandingOutsideTheRegionAlone() {
        Location[] landings = {
            outside,
            new Location(world, 0.5, 59, 0.5),
            new Location(world, 0.5, 91, 0.5),
            new Location(end, 0.5, 64, 0.5)
        };
        dangerous()
                .forEach(
                        (key, spawners) -> {
                            for (Function<Location, Entity> spawner : spawners) {
                                for (Location landing : landings) {
                                    Entity entity = spawner.apply(endPortal());
                                    assertThat(exit(entity, landing).isCancelled())
                                            .as(key + " to " + landing)
                                            .isFalse();
                                    assertThat(entity.isValid()).as(key).isTrue();
                                }
                            }
                        });
    }

    @Test
    void playersMobsItemsPearlsAndHarmlessRocketsAlwaysArrive() {
        PlayerMock glider = server.addPlayer();
        glider.teleport(endPortal());
        glider.setGliding(true);
        Firework boost = rocket(endPortal(), true);
        boost.setAttachedTo(glider);
        PlayerMock walker = server.addPlayer();
        walker.teleport(endPortal());
        List<Entity> harmless =
                List.of(
                        glider,
                        boost,
                        walker,
                        rocket(endPortal(), false),
                        end.spawn(endPortal(), EnderPearl.class),
                        end.spawn(endPortal(), Snowball.class),
                        potion(endPortal(), SplashPotion.class, PotionType.HEALING, null),
                        potion(endPortal(), LingeringPotion.class, PotionType.REGENERATION, null),
                        potion(endPortal(), SplashPotion.class, PotionType.WATER, null),
                        potion(
                                endPortal(),
                                SplashPotion.class,
                                PotionType.WATER,
                                new PotionEffect(PotionEffectType.SPEED, 100, 0)),
                        end.spawn(endPortal(), Egg.class),
                        end.spawn(endPortal(), ThrownExpBottle.class),
                        end.spawn(endPortal(), WindCharge.class),
                        end.dropItem(endPortal(), new ItemStack(Material.TNT)),
                        end.spawn(endPortal(), Cow.class),
                        end.spawn(endPortal(), Creeper.class));
        for (Entity entity : harmless) {
            EntityPortalExitEvent exit = exit(entity, inside);
            assertThat(exit.isCancelled()).as(entity.getType().name()).isFalse();
            assertThat(exit.getTo()).isEqualTo(inside);
            assertThat(entity.isValid()).as(entity.getType().name()).isTrue();
        }
    }

    @Test
    void eachKindIsSwitchedOffOnItsOwn() {
        for (String off : dangerous().keySet()) {
            for (String key : dangerous().keySet()) {
                plugin.getConfig().set("protection.portal-arrivals." + key, !key.equals(off));
            }
            reload();
            dangerous()
                    .forEach(
                            (key, spawners) -> {
                                for (Function<Location, Entity> spawner : spawners) {
                                    Entity entity = spawner.apply(endPortal());
                                    boolean defused = exit(entity, inside).isCancelled();
                                    assertThat(defused)
                                            .as(key + " with " + off + " off")
                                            .isEqualTo(!key.equals(off));
                                    assertThat(entity.isValid()).isEqualTo(!defused);
                                }
                            });
        }
    }

    @Test
    void oneFalseSwitchesEveryKindOffAndOneTrueBackOn() {
        plugin.getConfig().set("protection.portal-arrivals", false);
        reload();
        Entity skull = end.spawn(endPortal(), WitherSkull.class);
        assertThat(exit(skull, inside).isCancelled()).isFalse();
        assertThat(exit(end.spawn(endPortal(), TNTPrimed.class), inside).isCancelled()).isFalse();
        assertThat(skull.isValid()).isTrue();

        plugin.getConfig().set("protection.portal-arrivals", true);
        reload();
        assertThat(exit(end.spawn(endPortal(), Arrow.class), inside).isCancelled()).isTrue();
    }

    /**
     * Another plugin cancelling the exit does not stop the teleport: the server still moves the
     * entity, to its old coordinates in the destination world. That is where it is judged.
     */
    @Test
    void judgesACancelledExitWhereTheServerWouldReallyPutIt() {
        Entity overSpawn = end.spawn(new Location(end, 0.5, 70, 0.5), WitherSkull.class);
        EntityPortalExitEvent cancelled = exit(overSpawn, outside, true);
        assertThat(cancelled.isCancelled()).isTrue();
        assertThat(overSpawn.isValid()).isFalse();

        Entity farAway = end.spawn(new Location(end, 100.5, 70, 0.5), WitherSkull.class);
        exit(farAway, inside, true);
        assertThat(farAway.isValid()).isTrue();

        Entity nowhere = end.spawn(endPortal(), WitherSkull.class);
        EntityPortalExitEvent cleared =
                new EntityPortalExitEvent(
                        nowhere, nowhere.getLocation(), null, new Vector(), new Vector());
        server.getPluginManager().callEvent(cleared);
        assertThat(nowhere.isValid()).isTrue();
    }

    private Location endPortal() {
        return new Location(end, 0.5, 62.5, 0.5);
    }

    private <T extends ThrownPotion> Entity potion(
            Location at, Class<T> type, PotionType base, PotionEffect custom) {
        Material material =
                LingeringPotion.class.isAssignableFrom(type)
                        ? Material.LINGERING_POTION
                        : Material.SPLASH_POTION;
        ItemStack item = new ItemStack(material);
        item.editMeta(
                PotionMeta.class,
                meta -> {
                    meta.setBasePotionType(base);
                    if (custom != null) {
                        meta.addCustomEffect(custom, true);
                    }
                });
        return at.getWorld().spawn(at, type, potion -> potion.setItem(item));
    }

    /**
     * A firework rocket at the given place. MockBukkit does not implement attaching a rocket to a
     * glider, so the mock keeps the rider the way the server does.
     */
    private Firework rocket(Location at, boolean stars) {
        FireworkMock firework =
                new FireworkMock(server, UUID.randomUUID()) {
                    private LivingEntity rider;

                    @Override
                    public boolean setAttachedTo(LivingEntity entity) {
                        rider = entity;
                        return true;
                    }

                    @Override
                    public LivingEntity getAttachedTo() {
                        return rider;
                    }
                };
        firework.setLocation(at.clone());
        FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(1);
        if (stars) {
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED).build());
        }
        firework.setFireworkMeta(meta);
        return firework;
    }

    private EntityPortalExitEvent exit(Entity entity, Location to) {
        return exit(entity, to, false);
    }

    private EntityPortalExitEvent exit(Entity entity, Location to, boolean cancelledAlready) {
        Vector velocity = new Vector(-0.5, -0.5, 0);
        EntityPortalExitEvent event =
                new EntityPortalExitEvent(
                        entity, entity.getLocation(), to.clone(), velocity, velocity);
        event.setCancelled(cancelledAlready);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void reload() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }
}

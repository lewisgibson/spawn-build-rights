package com.lewisgibson.spawnbuildrights.fixture;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Axis;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

/**
 * Real entities sent through real portals: an End exit portal built in the End, which sends
 * anything that enters it to the overworld spawn, and a nether portal pair whose overworld end is
 * inside the region. Each scenario reports whether the entity entered the portal, whether it ever
 * reached the overworld, why it left the world it was fired in, how many explosions happened in
 * each dimension, how many overworld blocks around spawn changed, and whether a cow standing at
 * spawn was hurt.
 */
final class PortalScenarios implements Listener {
    /** The End exit portal: a 5x5 sheet of portal blocks at y 80, with nothing under it. */
    private static final int PORTAL_X = 39;

    private static final int PORTAL_Y = 80;
    private static final int PORTAL_Z = 39;
    private static final Vector PORTAL_CENTRE = new Vector(41.5, 80.5, 41.5);

    /** The overworld spawn, inside the region, and a spawn far outside it for the control. */
    private static final Location SPAWN_INSIDE = new Location(null, 0, -60, 0);

    private static final Location SPAWN_OUTSIDE = new Location(null, 60, -60, 60);
    private static final int SURFACE = -61;

    private final JavaPlugin plugin;
    private final Consumer<String> result;
    private final Map<UUID, String> tracked = new LinkedHashMap<>();
    private final Map<UUID, Boolean> entered = new HashMap<>();
    private final Map<UUID, Boolean> arrived = new HashMap<>();
    private final Map<UUID, String> removed = new HashMap<>();
    private final Map<UUID, Boolean> exitCancelled = new HashMap<>();
    private int overworldExplosions;
    private int otherExplosions;
    private Material[] before;
    private Cow sentinel;

    PortalScenarios(JavaPlugin plugin, Consumer<String> result) {
        this.plugin = plugin;
        this.result = result;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void enter(EntityPortalEnterEvent event) {
        if (tracked.containsKey(event.getEntity().getUniqueId())) {
            entered.put(event.getEntity().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void exit(EntityPortalExitEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (tracked.containsKey(id)) {
            exitCancelled.put(id, event.isCancelled());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void add(EntityAddToWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (tracked.containsKey(id) && event.getWorld().equals(overworld())) {
            arrived.put(id, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void remove(EntityRemoveEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (tracked.containsKey(id) && !event.getEntity().getWorld().equals(overworld())) {
            removed.putIfAbsent(id, event.getCause().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void explode(EntityExplodeEvent event) {
        count(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void firework(FireworkExplodeEvent event) {
        count(event.getEntity());
    }

    private void count(Entity entity) {
        if (entity.getWorld().equals(overworld())) {
            overworldExplosions++;
        } else {
            otherExplosions++;
        }
    }

    String run(String scenario) {
        boolean spawnOutside = scenario.endsWith("-outside");
        String name = spawnOutside ? scenario.substring(0, scenario.length() - 8) : scenario;
        reset(spawnOutside);
        World end = world(World.Environment.THE_END);
        boolean blue = name.endsWith("-blue");
        String shot = blue ? name.substring(0, name.length() - 5) : name;
        Entity entity =
                switch (shot) {
                    // Down onto the portal from the east: steep (about 80 degrees), 45 degrees,
                    // shallow (about 19 degrees), and flat along the portal sheet itself.
                    case "portal-skull-steep" -> skull(end, new Vector(43.5, 94, 41.5), blue);
                    case "portal-skull-45" -> skull(end, new Vector(54.5, 93.5, 41.5), blue);
                    case "portal-skull-shallow" -> skull(end, new Vector(63.5, 88, 41.5), blue);
                    case "portal-skull-horizontal" ->
                            fireball(
                                    end,
                                    WitherSkull.class,
                                    new Vector(37.5, 80.5, 40.5),
                                    new Vector(1, 0, 0),
                                    blue);
                    case "portal-fireball" ->
                            fireball(
                                    end,
                                    LargeFireball.class,
                                    new Vector(54.5, 93.5, 41.5),
                                    PORTAL_CENTRE.clone().subtract(new Vector(54.5, 93.5, 41.5)),
                                    false);
                    case "portal-tnt" ->
                            end.spawn(
                                    above(end),
                                    TNTPrimed.class,
                                    tnt -> {
                                        tnt.setFuseTicks(80);
                                        tnt.setVelocity(new Vector(0, -0.5, 0));
                                    });
                    case "portal-tnt-minecart" ->
                            end.spawn(
                                    above(end),
                                    ExplosiveMinecart.class,
                                    cart -> cart.setVelocity(new Vector(0, -0.5, 0)));
                    case "portal-star-rocket" -> rocket(end, true);
                    case "portal-plain-rocket" -> rocket(end, false);
                    // Harmless projectiles pass; damaging ones are defused. Each drops straight in.
                    case "portal-snowball" -> dropped(end, Snowball.class, snowball -> {});
                    case "portal-wind-charge" ->
                            fireball(
                                    end,
                                    WindCharge.class,
                                    new Vector(43.5, 94, 41.5),
                                    PORTAL_CENTRE.clone().subtract(new Vector(43.5, 94, 41.5)),
                                    false);
                    case "portal-splash-potion" ->
                            potion(
                                    end,
                                    SplashPotion.class,
                                    Material.SPLASH_POTION,
                                    PotionType.HARMING);
                    case "portal-healing-potion" ->
                            potion(
                                    end,
                                    SplashPotion.class,
                                    Material.SPLASH_POTION,
                                    PotionType.HEALING);
                    case "portal-poison-lingering" ->
                            potion(
                                    end,
                                    LingeringPotion.class,
                                    Material.LINGERING_POTION,
                                    PotionType.POISON);
                    case "portal-tipped-arrow" ->
                            dropped(
                                    end,
                                    Arrow.class,
                                    arrow -> arrow.setBasePotionType(PotionType.HARMING));
                    case "portal-sand" ->
                            end.spawnFallingBlock(above(end), Material.SAND.createBlockData());
                    case "portal-item" -> end.dropItem(above(end), new ItemStack(Material.TNT));
                    case "portal-cow" -> end.spawn(above(end), Cow.class);
                    case "portal-boost" -> boost(end);
                    case "portal-nether-arrow" -> netherArrow();
                    default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
                };
        track(entity, "subject");
        // A blue skull is slow, and TNT keeps its fuse through the portal before it explodes.
        int wait = blue || shot.startsWith("portal-tnt") ? 140 : 60;
        result.accept("pending");
        plugin.getServer().getScheduler().runTaskLater(plugin, this::report, wait);
        return "pending";
    }

    private void report() {
        StringBuilder outcome = new StringBuilder();
        for (Map.Entry<UUID, String> entry : tracked.entrySet()) {
            UUID id = entry.getKey();
            String prefix = entry.getValue().equals("subject") ? "" : entry.getValue() + ".";
            outcome.append(prefix)
                    .append("entered=")
                    .append(entered.getOrDefault(id, false))
                    .append(' ')
                    .append(prefix)
                    .append("arrived=")
                    .append(arrived.getOrDefault(id, false))
                    .append(' ')
                    .append(prefix)
                    .append("removed=")
                    .append(removed.getOrDefault(id, "none"))
                    .append(' ')
                    .append(prefix)
                    .append("exitCancelled=")
                    .append(exitCancelled.getOrDefault(id, false))
                    .append(' ');
        }
        outcome.append("overworldExplosions=")
                .append(overworldExplosions)
                .append(" overworldChanged=")
                .append(changedBlocks())
                .append(" otherExplosions=")
                .append(otherExplosions)
                .append(" sentinelHurt=")
                .append(sentinel.isDead() || sentinel.getHealth() < sentinel.getMaxHealth())
                .append(" overworldItems=")
                .append(overworldItems());
        plugin.getLogger().info("Portal scenario: " + outcome);
        result.accept(outcome.toString());
    }

    private Entity skull(World end, Vector from, boolean blue) {
        return fireball(end, WitherSkull.class, from, PORTAL_CENTRE.clone().subtract(from), blue);
    }

    private <T extends Fireball> Entity fireball(
            World end, Class<T> type, Vector from, Vector direction, boolean blue) {
        Vector heading = direction.clone().normalize();
        return end.spawn(
                from.toLocation(end),
                type,
                fireball -> {
                    if (fireball instanceof WitherSkull skull) {
                        skull.setCharged(blue);
                    }
                    fireball.setDirection(heading);
                    fireball.setVelocity(heading.clone().multiply(0.5));
                });
    }

    private Entity rocket(World end, boolean stars) {
        Vector from = new Vector(43.5, 94, 41.5);
        Vector heading = PORTAL_CENTRE.clone().subtract(from).normalize();
        return end.spawn(
                from.toLocation(end),
                Firework.class,
                firework -> {
                    FireworkMeta meta = firework.getFireworkMeta();
                    if (stars) {
                        meta.addEffect(
                                FireworkEffect.builder()
                                        .with(FireworkEffect.Type.BALL_LARGE)
                                        .withColor(Color.RED)
                                        .build());
                    }
                    firework.setFireworkMeta(meta);
                    firework.setShotAtAngle(true);
                    firework.setVelocity(heading.multiply(0.8));
                    firework.setTicksToDetonate(40);
                });
    }

    /**
     * A gliding armour stand wearing an elytra, looking straight down and boosted by an attached
     * rocket carrying stars, dives into the portal. It stands in for a player boosting through it,
     * since the harness has no real client; an armour stand keeps the pitch it is given, where a
     * mob's own look control would level it out and steer it away.
     */
    private Entity boost(World end) {
        ArmorStand rider =
                end.spawn(
                        new Location(end, 41.5, 88, 41.5, 0, 90),
                        ArmorStand.class,
                        stand -> {
                            stand.getEquipment().setChestplate(new ItemStack(Material.ELYTRA));
                            stand.setVelocity(new Vector(0, -0.5, 0));
                        });
        rider.setGliding(true);
        Firework rocket =
                end.spawn(
                        rider.getLocation(),
                        Firework.class,
                        firework -> {
                            FireworkMeta meta = firework.getFireworkMeta();
                            meta.addEffect(FireworkEffect.builder().withColor(Color.BLUE).build());
                            firework.setFireworkMeta(meta);
                            firework.setAttachedTo(rider);
                            firework.setTicksToDetonate(30);
                        });
        track(rocket, "rocket");
        track(rider, "rider");
        return rocket;
    }

    /** An arrow flown through a nether portal in the nether, whose exit portal is at spawn. */
    private Entity netherArrow() {
        World nether = world(World.Environment.NETHER);
        return nether.spawn(
                new Location(nether, 1.0, 71, 6.5),
                Arrow.class,
                arrow -> {
                    arrow.setGravity(false);
                    arrow.setVelocity(new Vector(0, 0, -1));
                });
    }

    private <T extends ThrownPotion> Entity potion(
            World end, Class<T> type, Material material, PotionType base) {
        ItemStack item = new ItemStack(material);
        item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(base));
        return dropped(end, type, potion -> potion.setItem(item));
    }

    private <T extends Entity> Entity dropped(World end, Class<T> type, Consumer<T> setup) {
        return end.spawn(
                above(end),
                type,
                entity -> {
                    setup.accept(entity);
                    entity.setVelocity(new Vector(0, -0.6, 0));
                });
    }

    private Location above(World end) {
        return new Location(end, 41.5, 84, 41.5);
    }

    private void track(Entity entity, String label) {
        if (!tracked.containsKey(entity.getUniqueId())) {
            tracked.put(entity.getUniqueId(), label);
        }
    }

    private void reset(boolean spawnOutside) {
        tracked.clear();
        entered.clear();
        arrived.clear();
        removed.clear();
        exitCancelled.clear();
        overworldExplosions = 0;
        otherExplosions = 0;

        World overworld = overworld();
        World end = world(World.Environment.THE_END);
        World nether = world(World.Environment.NETHER);
        for (World world : new World[] {overworld, end, nether}) {
            world.setGameRule(GameRule.MOB_GRIEFING, true);
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }
        for (int chunkX = -1; chunkX <= 4; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 4; chunkZ++) {
                overworld.addPluginChunkTicket(chunkX, chunkZ, plugin);
                end.addPluginChunkTicket(chunkX, chunkZ, plugin);
            }
        }
        nether.addPluginChunkTicket(0, 0, plugin);
        nether.addPluginChunkTicket(-1, 0, plugin);

        // The End: open air around a floating 5x5 sheet of exit-portal blocks.
        fill(end, 28, 72, 30, 70, 100, 52, Material.AIR);
        fill(
                end,
                PORTAL_X,
                PORTAL_Y,
                PORTAL_Z,
                PORTAL_X + 4,
                PORTAL_Y,
                PORTAL_Z + 4,
                Material.END_PORTAL);

        // The overworld: a flat floor around spawn, with a nether portal inside the region.
        fill(overworld, -12, SURFACE + 1, -12, 12, SURFACE + 12, 12, Material.AIR);
        fill(overworld, -12, SURFACE, -12, 12, SURFACE, 12, Material.GRASS_BLOCK);
        fill(overworld, -12, SURFACE - 3, -12, 12, SURFACE - 1, 12, Material.DIRT);
        netherPortal(overworld, 4, SURFACE, 8);
        if (spawnOutside) {
            fill(overworld, 50, SURFACE + 1, 50, 70, SURFACE + 12, 70, Material.AIR);
            fill(overworld, 50, SURFACE, 50, 70, SURFACE, 70, Material.GRASS_BLOCK);
        }
        Location spawn = spawnOutside ? SPAWN_OUTSIDE.clone() : SPAWN_INSIDE.clone();
        spawn.setWorld(overworld);
        overworld.setSpawnLocation(spawn);

        // The nether: a portal at its own origin, which the overworld portal answers.
        fill(nether, -3, 66, -2, 4, 76, 10, Material.AIR);
        fill(nether, -3, 66, -2, 4, 68, 10, Material.NETHERRACK);
        netherPortal(nether, 0, 69, 1);

        sentinel =
                overworld.spawn(
                        new Location(overworld, 3.5, SURFACE + 1, -2.5),
                        Cow.class,
                        cow -> cow.setAI(false));
        before = snapshot();
    }

    /** An obsidian frame two wide and three tall along X, lit, with its lower corner at x,y,z. */
    private static void netherPortal(World world, int x, int y, int z) {
        fill(world, x - 1, y, z, x + 2, y + 4, z, Material.OBSIDIAN);
        for (int portalX = x; portalX <= x + 1; portalX++) {
            for (int portalY = y + 1; portalY <= y + 3; portalY++) {
                Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
                portal.setAxis(Axis.X);
                world.getBlockAt(portalX, portalY, z).setBlockData(portal, false);
            }
        }
    }

    private static void fill(
            World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    /** Every overworld block in a 25x25 box around the spawn inside the region, 12 high. */
    private Material[] snapshot() {
        World overworld = overworld();
        Material[] blocks = new Material[25 * 25 * 13];
        int index = 0;
        for (int x = -12; x <= 12; x++) {
            for (int y = SURFACE - 1; y <= SURFACE + 11; y++) {
                for (int z = -12; z <= 12; z++) {
                    blocks[index++] = overworld.getBlockAt(x, y, z).getType();
                }
            }
        }
        return blocks;
    }

    private int changedBlocks() {
        Material[] after = snapshot();
        int changed = 0;
        for (int i = 0; i < after.length; i++) {
            if (after[i] != before[i]) {
                changed++;
            }
        }
        return changed;
    }

    /** Every item lying in the overworld, as its type and whether it is inside the region. */
    private String overworldItems() {
        String items =
                overworld().getEntitiesByClass(Item.class).stream()
                        .map(
                                item ->
                                        item.getItemStack().getType()
                                                + (Math.abs(item.getLocation().getBlockX()) <= 16
                                                                && Math.abs(
                                                                                item.getLocation()
                                                                                        .getBlockZ())
                                                                        <= 16
                                                        ? "@spawn"
                                                        : "@elsewhere"))
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
        return items.isEmpty() ? "none" : items;
    }

    private World overworld() {
        return world(World.Environment.NORMAL);
    }

    private World world(World.Environment environment) {
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == environment)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + environment + " world"));
    }
}

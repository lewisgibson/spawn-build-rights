package com.lewisgibson.spawnbuildrights.fixture;

import net.kyori.adventure.text.Component;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real explosions, projectiles and wither attacks in the disposable test world. */
final class EnvironmentalScenarios implements Listener {
    private final JavaPlugin plugin;
    private final FireScenarios fire;
    private final MobBlockScenarios mobBlocks;
    private final PortalScenarios portals;
    private final OutsideScenarios outside;
    private String result = "idle";
    private int witherChanges;
    private int skullHits;
    private int skullBlocks;
    private String skullRemoval = "none";

    EnvironmentalScenarios(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fire = new FireScenarios(plugin, outcome -> result = outcome);
        this.mobBlocks = new MobBlockScenarios(plugin, outcome -> result = outcome);
        this.portals = new PortalScenarios(plugin, outcome -> result = outcome);
        this.outside = new OutsideScenarios(plugin, outcome -> result = outcome);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    String run(String scenario) {
        if (scenario.equals("status")) {
            return result;
        }
        if (scenario.startsWith("portal-")) {
            return portals.run(scenario);
        }
        World world = plugin.getServer().getWorlds().getFirst();
        reset(world);
        return switch (scenario) {
            case "explosion" -> explosion(world);
            case "skull-inside" -> skull(world, 16);
            case "skull-outside" -> skull(world, 32);
            case "wither" -> wither(world);
            case "blaze-inside" -> fire.blaze(world, 16);
            case "blaze-outside" -> fire.blaze(world, 32);
            case "fire-boundary" -> fire.boundary(world);
            case "enderman-pickup-inside" -> mobBlocks.enderman(world, 16, false);
            case "enderman-pickup-outside" -> mobBlocks.enderman(world, 17, false);
            case "enderman-place-inside" -> mobBlocks.enderman(world, 16, true);
            case "enderman-place-outside" -> mobBlocks.enderman(world, 17, true);
            case "tnt-inside" -> mobBlocks.tnt(world, 16);
            case "tnt-outside" -> mobBlocks.tnt(world, 18);
            case "tnt-projectile-inside" -> mobBlocks.projectileTnt(world, 16);
            case "tnt-projectile-outside" -> mobBlocks.projectileTnt(world, 17);
            case "outside-falling" -> outside.falling(world);
            case "outside-fluid" -> outside.fluid(world);
            case "outside-pistons" -> outside.pistons(world);
            case "outside-dispensers" -> outside.dispensers(world);
            case "outside-tree" -> outside.tree(world);
            case "outside-mushroom" -> outside.mushroom(world);
            case "outside-projectiles" -> outside.projectiles(world);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void countWitherChanges(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Wither) {
            witherChanges++;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void countSkullBlocks(EntityExplodeEvent event) {
        if (event.getEntity() instanceof WitherSkull) {
            skullBlocks += event.blockList().size();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void countSkullHits(ProjectileHitEvent event) {
        if (event.getEntity() instanceof WitherSkull) {
            skullHits++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void skullRemoved(EntityRemoveEvent event) {
        if (event.getEntity() instanceof WitherSkull) {
            skullRemoval = event.getCause().name();
        }
    }

    private String explosion(World world) {
        world.getBlockAt(16, 80, 0).setType(Material.WHITE_WOOL);
        world.getBlockAt(18, 80, 0).setType(Material.WHITE_WOOL);
        world.getBlockAt(15, 80, 2).setType(Material.CHEST);
        Chest chest = (Chest) world.getBlockAt(15, 80, 2).getState();
        ItemStack treasure = named(Material.DIAMOND, 23, "Museum collection");
        chest.getBlockInventory().setItem(7, treasure);
        ArmorStand stand =
                world.spawn(
                        new Location(world, 15.5, 80, -2.5),
                        ArmorStand.class,
                        entity -> {
                            entity.setGravity(false);
                            entity.getEquipment()
                                    .setHelmet(named(Material.DIAMOND_HELMET, 1, "Museum helmet"));
                        });
        ArmorStand outside =
                world.spawn(
                        new Location(world, 19.5, 80, -2.5),
                        ArmorStand.class,
                        entity -> entity.setGravity(false));
        world.getBlockAt(15, 80, 4).setType(Material.STONE);
        ItemFrame frame =
                world.spawn(
                        new Location(world, 15.5, 80.5, 3.5),
                        ItemFrame.class,
                        entity -> {
                            entity.setFacingDirection(BlockFace.NORTH, true);
                            entity.setItem(named(Material.DIAMOND, 1, "Exhibit"));
                        });
        world.getBlockAt(14, 80, 4).setType(Material.STONE);
        Painting painting =
                world.spawn(
                        new Location(world, 14.5, 80.5, 3.5),
                        Painting.class,
                        entity -> entity.setFacingDirection(BlockFace.NORTH, true));
        Cow cow = world.spawn(new Location(world, 15.5, 80, -4.5), Cow.class);
        Creeper source = world.spawn(new Location(world, 17.5, 80.5, 0.5), Creeper.class);
        world.createExplosion(source.getLocation(), 6, false, true, source);
        boolean contents =
                world.getBlockAt(15, 80, 2).getState() instanceof Chest remaining
                        && treasure.equals(remaining.getBlockInventory().getItem(7));
        return "inside="
                + world.getBlockAt(16, 80, 0).getType()
                + " outside="
                + world.getBlockAt(18, 80, 0).getType()
                + " chest="
                + contents
                + " stand="
                + !stand.isDead()
                + " helmet="
                + named(Material.DIAMOND_HELMET, 1, "Museum helmet")
                        .equals(stand.getEquipment().getHelmet())
                + " frame="
                + (!frame.isDead() && named(Material.DIAMOND, 1, "Exhibit").equals(frame.getItem()))
                + " painting="
                + !painting.isDead()
                + " outsideStand="
                + !outside.isDead()
                + " cowHurt="
                + (cow.isDead() || cow.getHealth() < cow.getMaxHealth());
    }

    private String skull(World world, int targetX) {
        world.getBlockAt(targetX, 80, 0).setType(Material.SLIME_BLOCK);
        skullHits = 0;
        skullBlocks = 0;
        skullRemoval = "none";
        WitherSkull skull =
                world.spawn(
                        new Location(world, targetX + 3.5, 80.5, 0.5),
                        WitherSkull.class,
                        entity -> {
                            entity.setCharged(true);
                            entity.setIsIncendiary(false);
                            entity.setDirection(new Vector(-1, 0, 0));
                            entity.setVelocity(new Vector(-0.5, 0, 0));
                        });
        result = "pending";
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            result =
                                    "impact="
                                            + (skullHits > 0)
                                            + " affected="
                                            + (skullBlocks > 0)
                                            + " block="
                                            + world.getBlockAt(targetX, 80, 0).getType();
                            plugin.getLogger()
                                    .info(
                                            "Skull at "
                                                    + skull.getLocation()
                                                    + "; removed="
                                                    + skullRemoval
                                                    + "; "
                                                    + result);
                            skull.remove();
                        },
                        40);
        return result;
    }

    private String wither(World world) {
        world.getBlockAt(16, 80, 0).setType(Material.WHITE_WOOL);
        world.getBlockAt(18, 80, 0).setType(Material.WHITE_WOOL);
        Wither wither =
                world.spawn(
                        new Location(world, 17.5, 80, 0.5),
                        Wither.class,
                        entity -> {
                            entity.setInvulnerableTicks(0);
                            entity.setGravity(false);
                        });
        witherChanges = 0;
        wither.damage(1);
        result = "pending";
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            result =
                                    "changed="
                                            + (witherChanges > 0)
                                            + " inside="
                                            + world.getBlockAt(16, 80, 0).getType()
                                            + " outside="
                                            + world.getBlockAt(18, 80, 0).getType();
                            wither.remove();
                        },
                        40);
        return result;
    }

    private static ItemStack named(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        item.editMeta(meta -> meta.displayName(Component.text(name)));
        return item;
    }

    private void reset(World world) {
        for (int chunkX = 0; chunkX <= 3; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            }
        }
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
        for (int x = 10; x <= 38; x++) {
            for (int y = 77; y <= 86; y++) {
                for (int z = -7; z <= 7; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }
}

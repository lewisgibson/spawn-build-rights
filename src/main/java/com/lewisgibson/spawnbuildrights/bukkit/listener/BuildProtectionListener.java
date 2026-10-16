package com.lewisgibson.spawnbuildrights.bukkit.listener;

import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Guards player block edits, signs, buckets, entity placement, ignition, bone meal, flower pots,
 * pumpkin carving and the other block changes a player makes without placing or breaking anything,
 * using build rights. Doors, buttons and ordinary container access remain usable without build
 * rights.
 */
public final class BuildProtectionListener implements Listener {

    private final BuildGuard guard;

    /**
     * @param plugin the plugin whose live runtime snapshot each check reads
     */
    public BuildProtectionListener(SpawnBuildRightsPlugin plugin) {
        this.guard = new BuildGuard(plugin);
    }

    /** Breaking a block in the region needs build access. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        guard.check(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    /** Placing a block in the region needs build access. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        guard.check(event.getPlayer(), event.getBlockPlaced().getLocation(), event);
    }

    /** Editing a sign in the region needs build access. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        guard.check(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    /** Liquid lands on the block beside the one clicked, so that is the one checked. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location target = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        guard.check(event.getPlayer(), target, event);
    }

    /** Scooping up a liquid removes a block, so it needs build access. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        guard.check(event.getPlayer(), event.getBlockClicked().getLocation(), event);
    }

    /** Frames and paintings placed in the region need build access; dispensers have no player. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            guard.check(player, event.getEntity().getLocation(), event);
        }
    }

    /** Boats, minecarts and similar placed in the region need build access. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            guard.check(player, event.getEntity().getLocation(), event);
        }
    }

    /**
     * Only a player-lit fire is checked here; other ignition belongs to the environmental listener.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            guard.check(player, event.getBlock().getLocation(), event);
        }
    }

    /**
     * A block a player changes without placing or breaking it: trampling farmland, eating a cake or
     * putting a candle on one, an eye of ender in a portal frame, brushing suspicious sand, and the
     * blocks a player's own projectile breaks or changes (a decorated pot, a chorus flower, pointed
     * dripstone, a fire put out by a splash potion). The server reports all of these as an entity
     * changing a block, with the player or their projectile as the entity.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChangeBlock(EntityChangeBlockEvent event) {
        Player player = actingPlayer(event.getEntity());
        if (player != null && !momentary(event.getBlock().getType())) {
            guard.check(player, event.getBlock().getLocation(), event);
        }
    }

    private static Player actingPlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * Changes that use a block rather than change the build, which anyone may do as they may press
     * a button: redstone ore glowing when touched, a big dripleaf tilting under someone, a
     * composter being filled or emptied, and glow berries picked from cave vines (sweet berries,
     * picked the same way, fire no change at all).
     */
    private static boolean momentary(Material material) {
        return switch (material) {
            case REDSTONE_ORE,
                    DEEPSLATE_REDSTONE_ORE,
                    BIG_DRIPLEAF,
                    COMPOSTER,
                    CAVE_VINES,
                    CAVE_VINES_PLANT ->
                    true;
            default -> false;
        };
    }

    /**
     * Bone meal used on a block in the region needs build access: on moss it turns the stone and
     * dirt around it to moss, and on grass it covers it in flowers. Bone meal used outside the
     * region that would grow into it is the growth protection's to stop, in the environmental
     * listener.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            guard.check(player, event.getBlock().getLocation(), event);
        }
    }

    /** Putting a plant in a flower pot, or taking one out, changes the pot. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFlowerPot(PlayerFlowerPotManipulateEvent event) {
        guard.check(event.getPlayer(), event.getFlowerpot().getLocation(), event);
    }

    /**
     * Carving a pumpkin with shears changes the block; taking honeycomb from a beehive or bee nest
     * with them is using it, like a container.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShearBlock(PlayerShearBlockEvent event) {
        if (!Tag.BEEHIVES.isTagged(event.getBlock().getType())) {
            guard.check(event.getPlayer(), event.getBlock().getLocation(), event);
        }
    }

    /**
     * Dyeing a sign, making its text glow or dim, or waxing it changes the sign as much as editing
     * its text does, and waxing locks it against every later edit. The server fires no event of its
     * own for these, so the click is checked: a right click on a sign with a dye, an ink sac, a
     * glow ink sac or honeycomb. The cheap checks come first, because every click in the world
     * arrives here.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignApply(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        // A right click on a block always has the block; only clicks on air have none.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && item != null
                && Tag.ALL_SIGNS.isTagged(block.getType())
                && signApplicator(item.getType())) {
            guard.check(event.getPlayer(), block.getLocation(), event);
        }
    }

    private static boolean signApplicator(Material material) {
        return material == Material.INK_SAC
                || material == Material.GLOW_INK_SAC
                || material == Material.HONEYCOMB
                || Tag.ITEMS_DYES.isTagged(material);
    }
}

package com.lewisgibson.spawnbuildrights.bukkit.listener;

import com.lewisgibson.spawnbuildrights.bukkit.PluginRuntime;
import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import com.lewisgibson.spawnbuildrights.core.settings.PortalArrivals;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import java.util.List;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SizedFireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.Vector;

/**
 * Protects the affected build or display without changing combat or ordinary container access.
 * Reads the current settings for each event so configuration reloads take effect immediately.
 */
public final class EnvironmentalProtectionListener implements Listener {
    private final SpawnBuildRightsPlugin plugin;
    private final BuildGuard guard;

    /**
     * @param plugin the plugin whose live runtime snapshot each event reads
     */
    public EnvironmentalProtectionListener(SpawnBuildRightsPlugin plugin) {
        this.plugin = plugin;
        this.guard = new BuildGuard(plugin);
    }

    /** The affected blocks decide protection, including explosions outside the region. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectFromExplosion(event.blockList());
    }

    /** Beds and respawn anchors can explode without an entity source. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectFromExplosion(event.blockList());
    }

    private void protectFromExplosion(List<Block> blocks) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (settings.protection().explosions()) {
            blocks.removeIf(block -> contains(settings.region(), block));
        }
    }

    /**
     * Wither contact damage and hostile mob grief do not use the explosion event. A hostile mob's
     * projectile counts as the mob: a skeleton's arrow breaking a decorated pot or a chorus flower.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobChange(EntityChangeBlockEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        Entity entity = event.getEntity();
        if (settings.protection().hostileMobChanges()
                && (hostile(entity)
                        || (entity instanceof Projectile projectile
                                && projectile.getShooter() instanceof Entity shooter
                                && hostile(shooter)))
                && contains(settings.region(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Keep hostile mobs from trampling crops or eggs while allowing pressure plates. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobInteract(EntityInteractEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        Material material = event.getBlock().getType();
        if (settings.protection().hostileMobChanges()
                && hostile(event.getEntity())
                && (material == Material.FARMLAND || material == Material.TURTLE_EGG)
                && contains(settings.region(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Ignition is separate from explosions, and Paper attributes owned projectiles to the shooter,
     * so a player's flaming arrow arrives here as the player with cause {@code ARROW} and is
     * stopped like any other projectile. A fire charge used by hand also arrives as the player with
     * cause {@code FIREBALL}, but it is lighting a fire directly, like flint and steel, so it
     * follows build rights alone. Lava, lightning and explosions light fires with nobody to ask:
     * inside the region nothing they light could burn anything, but the fire itself would sit on a
     * flammable block for good, so it is not lit.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobIgnite(BlockIgniteEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (!contains(settings.region(), event.getBlock())) {
            return;
        }
        Entity source = event.getIgnitingEntity();
        boolean mobIgnition =
                settings.protection().hostileMobChanges()
                        && (hostile(source) || source instanceof Projectile);
        boolean protectedIgnition =
                switch (event.getCause()) {
                    case LAVA, LIGHTNING -> settings.protection().fireDamage() || mobIgnition;
                    case EXPLOSION -> settings.protection().explosions() || mobIgnition;
                    case FIREBALL ->
                            settings.protection().hostileMobChanges()
                                    && !(source instanceof Player player
                                            && holdsFireCharge(player));
                    case ARROW -> settings.protection().hostileMobChanges();
                    default -> mobIgnition;
                };
        if (protectedIgnition) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether a player-attributed fireball ignition is the player's own fire charge used on a
     * block, rather than a blaze fireball they deflected, which the server attributes to them too.
     */
    private static boolean holdsFireCharge(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.FIRE_CHARGE
                || player.getInventory().getItemInOffHand().getType() == Material.FIRE_CHARGE;
    }

    /** Priming consumes the TNT block before an explosion and does not fire BlockIgniteEvent. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        PluginRuntime runtime = plugin.runtime();
        SpawnBuildRightsSettings settings = runtime.settings();
        if (!contains(settings.region(), event.getBlock())) {
            return;
        }
        boolean protectedCause =
                switch (event.getCause()) {
                    case PROJECTILE -> settings.protection().hostileMobChanges();
                    case FIRE -> settings.protection().fireDamage();
                    case EXPLOSION -> settings.protection().explosions();
                    default -> false;
                };
        if (protectedCause) {
            event.setCancelled(true);
        } else if (event.getCause() == TNTPrimeEvent.PrimeCause.PLAYER
                && event.getPrimingEntity() instanceof Player player) {
            guard.check(runtime, player, event.getBlock().getLocation(), event);
        }
    }

    /** Later fire ticks no longer identify who started the fire, including outside the region. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (settings.protection().fireDamage() && contains(settings.region(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Only fire spread is guarded; mushroom, grass and other natural spread remain available. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        Material material = event.getNewState().getType();
        if (settings.protection().fireDamage()
                && (material == Material.FIRE || material == Material.SOUL_FIRE)
                && contains(settings.region(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * A falling block is an entity until it lands, so none of the block protections see it: sand,
     * gravel or concrete powder launched from outside, or dropped into the End's exit portal, would
     * otherwise pile up inside the region. Only a block that started falling inside the region may
     * land in it, so a builder's own sand still settles. One from outside breaks into its item
     * where it would have landed, the way it does when it lands on a torch, so nothing builds up in
     * the region and nothing is lost. A cancelled landing makes the server discard the falling
     * block without a drop, which is why the item is dropped here. The same event also fires when a
     * block starts to fall, before the falling block has an origin, and that is never stopped:
     * cancelling it would leave the block hanging in the air.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (settings.protection().fallingBlocks()
                && entersFromOutside(settings.region(), falling.getOrigin(), event.getBlock())) {
            event.setCancelled(true);
            dropAsItem(falling, falling.getLocation());
        }
    }

    /**
     * Drops a falling block's item the way the game does when the block cannot land: only if the
     * falling block would drop one (a destroyed anvil, or one spawned not to drop, does not) and
     * entity drops are on, and only for a block that has an item. Every block that falls (sand,
     * gravel, concrete powder, anvils, a dragon egg, pointed dripstone, scaffolding) is its own
     * item, so the block's material is the item; anything else set falling by a command and with no
     * item of its own, such as fire, drops nothing.
     */
    private static void dropAsItem(FallingBlock falling, Location at) {
        World world = at.getWorld();
        Material material = falling.getBlockData().getMaterial();
        if (falling.getDropItem()
                && !falling.getCancelDrop()
                && material.isItem()
                && !material.isAir()
                && Boolean.TRUE.equals(world.getGameRuleValue(GameRules.ENTITY_DROPS))) {
            // Still, so the item stays in the block it would have landed in rather than being
            // tossed a random way, which could carry it back out of the region.
            world.dropItem(at, new ItemStack(material), item -> item.setVelocity(new Vector()));
        }
    }

    /**
     * Water and lava flowing into the region from outside it. A dragon egg teleporting uses the
     * same event, so an egg cannot be knocked into the region from outside either. Fluid placed
     * inside the region, which only someone with build rights can do, flows as normal.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (settings.protection().fluidFlow()
                && entersFromOutside(settings.region(), event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * A piston outside the region may not reach into it: no pushing blocks in, no moving or
     * breaking blocks that are inside, and no extending its head into it. The event's block list
     * already holds the blocks a push would break as well as the ones it moves. A piston inside the
     * region was placed by someone with build rights and works as normal.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonReachesIn(event.getBlock(), event.getBlocks(), true)) {
            event.setCancelled(true);
        }
    }

    /** A sticky piston outside the region may not pull blocks out of it, or pull blocks into it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonReachesIn(event.getBlock(), event.getBlocks(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * The piston's own facing decides where the blocks go: forward on extending, back towards the
     * piston on retracting. It is read from the block itself, which is still the piston, or the
     * moving piston that replaces it while it retracts, and both carry the facing.
     */
    private boolean pistonReachesIn(Block piston, List<Block> blocks, boolean extending) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        SpawnRegion region = settings.region();
        if (!settings.protection().pistons()
                || contains(region, piston)
                || !(piston.getBlockData() instanceof Directional directional)) {
            return false;
        }
        BlockFace facing = directional.getFacing();
        BlockFace motion = extending ? facing : facing.getOppositeFace();
        if (extending && contains(region, piston.getRelative(facing))) {
            return true;
        }
        for (Block block : blocks) {
            if (contains(region, block) || contains(region, block.getRelative(motion))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A dispenser outside the region facing into it dispenses nothing: a bucket of water or lava,
     * fire, a shulker box, a carved pumpkin, a wither skull or anything else it can put into the
     * world. Droppers only drop items, which change no block, so they are left alone.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (dispenser.getType() != Material.DISPENSER) {
            return;
        }
        // A dispenser's block data always carries its facing.
        Directional directional = (Directional) dispenser.getBlockData();
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        if (settings.protection().dispensers()
                && entersFromOutside(
                        settings.region(),
                        dispenser,
                        dispenser.getRelative(directional.getFacing()))) {
            event.setCancelled(true);
        }
    }

    /**
     * A projectile a dispenser outside the region fires (an arrow, a fire charge, a snowball, a
     * potion, a firework) is watched while it flies and removed on the tick it would enter the
     * region, so it never touches anything inside. Only dispensers: players' and mobs' projectiles
     * are ordinary play and fly as normal, and what they could do to blocks inside is covered by
     * the other protections. A dispenser inside the region is a builder's own and is not watched.
     *
     * <p>A projectile is added to the world during the dispenser's tick and moves in that same
     * tick, so its first step is checked as it is launched and a launch straight into the region is
     * cancelled. After that the watch runs on the projectile's own scheduler, which is safe on
     * Folia, and ends when the projectile is gone or stuck. Nothing is scheduled for a projectile a
     * living thing shot, which is almost every one, so the common path is two checks. The shooter
     * of a dispensed projectile may not be set yet when it is launched, so the watch decides again
     * on its first tick.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof LivingEntity
                || !plugin.runtime().settings().protection().dispensers()) {
            return;
        }
        SpawnRegion region = plugin.runtime().settings().region();
        if (dispensedFromOutside(region, projectile) && reachesThisTick(region, projectile)) {
            event.setCancelled(true);
            return;
        }
        watchNextTick(projectile);
    }

    /**
     * Each step of the watch schedules the next, rather than one repeating task being cancelled, so
     * the watch simply stops when a step does not reschedule. The entity's own scheduler is
     * Folia-safe, and drops the step if the projectile is gone before it runs.
     */
    private void watchNextTick(Projectile projectile) {
        projectile.getScheduler().runDelayed(plugin, task -> watch(projectile), null, 1L);
    }

    private void watch(Projectile projectile) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        SpawnRegion region = settings.region();
        if (!settings.protection().dispensers()
                || !dispensedFromOutside(region, projectile)
                || (projectile instanceof AbstractArrow arrow && arrow.isInBlock())) {
            return;
        }
        if (reachesThisTick(region, projectile)) {
            projectile.remove();
        } else {
            watchNextTick(projectile);
        }
    }

    /** Whether the projectile's next step, at its current speed, touches the region. */
    private static boolean reachesThisTick(SpawnRegion region, Projectile projectile) {
        Location at = projectile.getLocation();
        Vector step = projectile.getVelocity();
        return region.reachedAlong(
                at.getWorld().getName(),
                at.getX(),
                at.getY(),
                at.getZ(),
                step.getX(),
                step.getY(),
                step.getZ());
    }

    /**
     * The backstop for the watch: a dispensed projectile from outside that hits a block or entity
     * inside the region anyway, because something knocked it off its course mid-tick, is removed
     * instead of hitting.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        SpawnRegion region = settings.region();
        Block block = event.getHitBlock();
        Entity target = event.getHitEntity();
        if (settings.protection().dispensers()
                && ((block != null && contains(region, block))
                        || (target != null && contains(region, target.getLocation())))
                && dispensedFromOutside(region, event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    private static boolean dispensedFromOutside(SpawnRegion region, Projectile projectile) {
        return projectile.getShooter() instanceof BlockProjectileSource source
                && !contains(region, source.getBlock());
    }

    /**
     * A tree or huge mushroom grown outside the region keeps every block it would place outside and
     * loses the ones inside, so a sapling planted beside spawn cannot grow into it. Growth that
     * starts inside the region is the region's own and grows whole.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        protectFromGrowth(event.getLocation(), event.getBlocks());
    }

    /** Bone meal used outside the region spreads grass and flowers only outside it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        protectFromGrowth(event.getBlock().getLocation(), event.getBlocks());
    }

    private void protectFromGrowth(Location origin, List<BlockState> blocks) {
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        SpawnRegion region = settings.region();
        if (settings.protection().growth() && !contains(region, origin)) {
            blocks.removeIf(state -> contains(region, state));
        }
    }

    /** Frame contents and rotation use a different event from breaking the frame. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        guard.check(event.getPlayer(), event.getItemFrame().getLocation(), event);
    }

    /** Changing displayed equipment requires the same access as changing its stand. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStandChange(PlayerArmorStandManipulateEvent event) {
        guard.check(event.getPlayer(), event.getRightClicked().getLocation(), event);
    }

    /** Entity damage happens separately from explosion block destruction. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDisplayDamage(EntityDamageEvent event) {
        Entity display = event.getEntity();
        if (!(display instanceof Hanging) && !(display instanceof ArmorStand)) {
            return;
        }
        PluginRuntime runtime = plugin.runtime();
        SpawnBuildRightsSettings settings = runtime.settings();
        if (!contains(settings.region(), display.getLocation())) {
            return;
        }
        Entity source = event.getDamageSource().getCausingEntity();
        boolean explosion =
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                        || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
        if (!explosion && source instanceof Player player) {
            guard.check(runtime, player, display.getLocation(), event);
        } else if (settings.protection().displayDamage() && environmental(event.getCause())) {
            event.setCancelled(true);
        }
    }

    /** Protect the entire hanging entity as well as an occupied frame's item. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        PluginRuntime runtime = plugin.runtime();
        SpawnBuildRightsSettings settings = runtime.settings();
        if (!contains(settings.region(), event.getEntity().getLocation())) {
            return;
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            if (settings.protection().displayDamage()) {
                event.setCancelled(true);
            }
        } else if (event instanceof HangingBreakByEntityEvent byEntity) {
            // The damage source names who is behind a projectile; with no one behind it, the
            // remover itself is what broke the display.
            Entity source = byEntity.getDamageSource().getCausingEntity();
            if (source == null) {
                source = byEntity.getRemover();
            }
            if (source instanceof Player player) {
                guard.check(runtime, player, event.getEntity().getLocation(), event);
            } else if (settings.protection().displayDamage()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Defuses an explosive or projectile that a portal would deliver into the region. A falling
     * block is turned into its item where it would have arrived, so sand dropped into the End's
     * exit portal reaches spawn as an item instead of a pile and nothing is lost. A portal keeps an
     * entity's speed and the End's exit portal always lands at the world spawn, so a wither skull
     * or TNT sent through one strikes spawn with nobody there to aim it.
     *
     * <p>This event is the one place that knows where the entity will actually land, for every
     * portal: it fires on the real teleport, after the End exit has been resolved to the surface at
     * the world spawn and a nether portal to the exit portal it found, and after any other plugin
     * has changed the destination. Entering a portal does not know the destination yet, and the
     * earlier portal event carries only the scaled-down nether coordinates before the exit portal
     * is searched for. Removing the entity here also ends the teleport, which the server abandons
     * once its entity is gone, so the entity cannot sit in the portal and try again next tick, and
     * removal itself neither explodes nor drops anything.
     *
     * <p>Cancelling this event alone would not stop the teleport: the server then still moves the
     * entity, to its old coordinates in the destination world. That is why a cancelled event is
     * still examined, at the place the entity would really land.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalArrival(EntityPortalExitEvent event) {
        Entity entity = event.getEntity();
        PortalArrivals.Kind kind = arrivalKind(entity);
        if (kind == null) {
            return;
        }
        SpawnBuildRightsSettings settings = plugin.runtime().settings();
        Location landing = landing(event);
        if (settings.protection().portalArrivals().defuses(kind)
                && landing != null
                && contains(settings.region(), landing)) {
            event.setCancelled(true);
            if (entity instanceof FallingBlock falling) {
                dropAsItem(falling, landing);
            }
            entity.remove();
        }
    }

    /**
     * The kind of thing that can damage a player or the world, or null for everything a portal
     * should carry as normal. Every kind is named here rather than taken as "every projectile but
     * these", so a harmless projectile, or one a later game version adds, passes until it is added
     * on purpose. Passing: players, mobs, items, ender pearls, snowballs, eggs, experience bottles,
     * fishing hooks, wind charges, a plain firework rocket, and a rocket boosting someone's elytra,
     * which goes wherever its rider goes.
     */
    private static PortalArrivals.Kind arrivalKind(Entity entity) {
        if (entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart) {
            return PortalArrivals.Kind.EXPLOSIVE;
        }
        if (entity instanceof WitherSkull) {
            return PortalArrivals.Kind.WITHER_SKULL;
        }
        if (entity instanceof SizedFireball || entity instanceof DragonFireball) {
            return PortalArrivals.Kind.FIREBALL;
        }
        if (entity instanceof FallingBlock) {
            return PortalArrivals.Kind.FALLING_BLOCK;
        }
        if (entity instanceof Firework firework) {
            return firework.getAttachedTo() == null && firework.getFireworkMeta().hasEffects()
                    ? PortalArrivals.Kind.FIREWORK
                    : null;
        }
        // Arrows of every kind and tridents are AbstractArrow, and do damage tipped or not.
        if (entity instanceof AbstractArrow
                || entity instanceof LlamaSpit
                || entity instanceof ShulkerBullet) {
            return PortalArrivals.Kind.PROJECTILE;
        }
        // Splash and lingering potions are ThrownPotion: only a harmful one is defused.
        if (entity instanceof ThrownPotion potion && harmful(potion)) {
            return PortalArrivals.Kind.PROJECTILE;
        }
        return null;
    }

    /**
     * Whether the potion's base type or any custom effect is one the game itself classes as
     * harmful, so an effect a later version adds classifies itself. Healing, regeneration, water
     * and every other potion with no harmful effect pass.
     */
    private static boolean harmful(ThrownPotion potion) {
        PotionMeta meta = potion.getPotionMeta();
        PotionType base = meta.getBasePotionType();
        return (base != null
                        && base.getPotionEffects().stream()
                                .anyMatch(EnvironmentalProtectionListener::harmful))
                || meta.getCustomEffects().stream()
                        .anyMatch(EnvironmentalProtectionListener::harmful);
    }

    private static boolean harmful(PotionEffect effect) {
        return effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL;
    }

    private static Location landing(EntityPortalExitEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return null;
        }
        if (!event.isCancelled()) {
            return to;
        }
        Location from = event.getFrom().clone();
        from.setWorld(to.getWorld());
        return from;
    }

    /**
     * Every mob the game counts as an enemy: monsters, the ender dragon, and the hostile mobs that
     * are not monsters to Bukkit, such as ghasts, slimes, phantoms, shulkers and hoglins.
     */
    private static boolean hostile(Entity entity) {
        return entity instanceof Enemy;
    }

    private static boolean environmental(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case ENTITY_ATTACK,
                    ENTITY_SWEEP_ATTACK,
                    PROJECTILE,
                    BLOCK_EXPLOSION,
                    ENTITY_EXPLOSION,
                    FIRE,
                    FIRE_TICK,
                    LAVA,
                    LIGHTNING,
                    MAGIC,
                    POISON,
                    WITHER,
                    DRAGON_BREATH,
                    FALLING_BLOCK ->
                    true;
            default -> false;
        };
    }

    private static boolean contains(SpawnRegion region, Block block) {
        return region.contains(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private static boolean contains(SpawnRegion region, BlockState state) {
        return region.contains(
                state.getWorld().getName(), state.getX(), state.getY(), state.getZ());
    }

    private static boolean entersFromOutside(SpawnRegion region, Block from, Block to) {
        return region.entersFromOutside(
                from.getWorld().getName(),
                from.getX(),
                from.getY(),
                from.getZ(),
                to.getWorld().getName(),
                to.getX(),
                to.getY(),
                to.getZ());
    }

    /**
     * A falling block has no origin yet when the server asks whether a block may start to fall: the
     * event fires before the entity is added to the world, and the origin is only set then. That is
     * a block starting to fall where it already is, never a landing, so it is not stopped.
     */
    private static boolean entersFromOutside(SpawnRegion region, Location from, Block to) {
        if (from == null) {
            return false;
        }
        return region.entersFromOutside(
                from.getWorld().getName(),
                from.getBlockX(),
                from.getBlockY(),
                from.getBlockZ(),
                to.getWorld().getName(),
                to.getX(),
                to.getY(),
                to.getZ());
    }

    /** Every location passed here is an entity's or a landing's, which always has a world. */
    private static boolean contains(SpawnRegion region, Location location) {
        return region.contains(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}

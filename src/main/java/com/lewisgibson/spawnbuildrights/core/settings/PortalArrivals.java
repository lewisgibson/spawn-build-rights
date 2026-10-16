package com.lewisgibson.spawnbuildrights.core.settings;

import java.util.Objects;

/**
 * Which kinds of thing are defused when a portal would deliver them into the region. A portal keeps
 * an entity's speed, and the End's exit portal always lands at the world spawn, so a wither skull,
 * TNT or a fireball sent through one can strike spawn without anyone being there to aim it. Each
 * kind is switched on its own so a server can allow, say, arrows while still stopping TNT.
 *
 * @param explosives primed TNT and TNT minecarts
 * @param witherSkulls wither skulls, black and blue
 * @param fireballs ghast, blaze and dragon fireballs
 * @param projectiles projectiles that damage players or the world: arrows of every kind, tridents,
 *     splash and lingering potions with a harmful effect, llama spit and shulker bullets
 * @param fireworks firework rockets carrying an explosion; a plain rocket is never defused
 * @param fallingBlocks falling blocks: sand, gravel, concrete powder, anvils and the rest, which
 *     the End's exit portal would otherwise rain onto spawn
 */
public record PortalArrivals(
        boolean explosives,
        boolean witherSkulls,
        boolean fireballs,
        boolean projectiles,
        boolean fireworks,
        boolean fallingBlocks) {

    /** The kinds of arriving entity, as the adapter classifies them. */
    public enum Kind {
        /** Primed TNT and TNT minecarts. */
        EXPLOSIVE,
        /** A wither skull, black or blue. */
        WITHER_SKULL,
        /** A ghast, blaze or dragon fireball. */
        FIREBALL,
        /**
         * A damaging projectile: an arrow, trident, thrown potion, llama spit or shulker bullet.
         */
        PROJECTILE,
        /** A firework rocket that carries an explosion. */
        FIREWORK,
        /** A falling block: sand, gravel, concrete powder, an anvil and the rest. */
        FALLING_BLOCK
    }

    /**
     * @return every kind defused, so an existing configuration gains the protection without its
     *     other settings being overwritten
     */
    public static PortalArrivals defaults() {
        return all(true);
    }

    /**
     * @param enabled whether every kind is defused
     * @return every kind set to {@code enabled}, for a configuration that switches them as one
     */
    public static PortalArrivals all(boolean enabled) {
        return new PortalArrivals(enabled, enabled, enabled, enabled, enabled, enabled);
    }

    /**
     * @param kind the kind of the arriving entity
     * @return whether an entity of that kind is defused before it lands in the region
     */
    public boolean defuses(Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case EXPLOSIVE -> explosives;
            case WITHER_SKULL -> witherSkulls;
            case FIREBALL -> fireballs;
            case PROJECTILE -> projectiles;
            case FIREWORK -> fireworks;
            case FALLING_BLOCK -> fallingBlocks;
        };
    }
}

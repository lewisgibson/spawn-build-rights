package com.lewisgibson.spawnbuildrights.core.settings;

import java.util.Objects;

/**
 * Protection against damage that does not depend on a player's build permission.
 *
 * @param explosions preserve blocks affected by explosions inside the region
 * @param hostileMobChanges prevent hostile mob block changes and mob/projectile ignition
 * @param displayDamage protect item frames, paintings and armour stands from environmental damage
 * @param fireDamage prevent blocks burning and fire spreading into the region
 * @param fallingBlocks stop a falling block that started outside the region (sand, gravel, concrete
 *     powder, an anvil) landing inside it; it drops as its item instead
 * @param fluidFlow stop water and lava flowing in from outside the region, and a dragon egg
 *     teleporting in from outside it
 * @param pistons stop a piston outside the region moving or breaking blocks inside it, or pushing
 *     blocks into it
 * @param dispensers stop a dispenser outside the region dispensing into a block inside it, and
 *     remove a projectile such a dispenser fires as it enters the region
 * @param growth stop a tree, huge mushroom or bone meal outside the region placing blocks inside it
 * @param portalArrivals which explosives and projectiles are defused when a portal would deliver
 *     them into the region
 */
public record EnvironmentalProtection(
        boolean explosions,
        boolean hostileMobChanges,
        boolean displayDamage,
        boolean fireDamage,
        boolean fallingBlocks,
        boolean fluidFlow,
        boolean pistons,
        boolean dispensers,
        boolean growth,
        PortalArrivals portalArrivals) {

    public EnvironmentalProtection {
        Objects.requireNonNull(portalArrivals, "portalArrivals");
    }

    /**
     * @return every protection enabled, so an existing configuration gains them without its other
     *     settings being overwritten
     */
    public static EnvironmentalProtection defaults() {
        return new EnvironmentalProtection(
                true, true, true, true, true, true, true, true, true, PortalArrivals.defaults());
    }
}

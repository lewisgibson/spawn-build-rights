package com.lewisgibson.spawnbuildrights.core.region;

import java.util.Objects;

/**
 * An immutable, world-anchored volume describing the protected spawn area.
 *
 * <p>The footprint is centred on {@code (centerX, centerZ)} and extends {@code radius} blocks
 * according to {@link #shape()}. Vertically it spans {@code [minY, maxY]} inclusive. A block is
 * inside the region only when it is in the matching world and satisfies both the horizontal and
 * vertical bounds.
 *
 * <p>This type is deliberately free of any server-platform classes: it operates purely on primitive
 * block coordinates, so it can be unit-tested in isolation and reused unchanged by a future
 * non-Bukkit platform adapter.
 *
 * @param world the name of the world this region protects
 * @param centerX the region centre X block coordinate
 * @param centerZ the region centre Z block coordinate
 * @param radius the horizontal radius in blocks ({@code >= 0}); {@code 0} protects a single column
 * @param shape the footprint {@link RegionShape}
 * @param minY the inclusive lower Y bound
 * @param maxY the inclusive upper Y bound ({@code >= minY})
 */
public record SpawnRegion(
        String world, int centerX, int centerZ, int radius, RegionShape shape, int minY, int maxY) {

    /** The largest gap, in blocks, between two points of a step tested by reachedAlong. */
    private static final double PATH_STEP = 0.25;

    /** A cap on the samples of one step: 64 quarter blocks is 16 blocks a tick. */
    private static final int MAX_PATH_SAMPLES = 64;

    /** Validates invariants at construction so an invalid region can never exist. */
    public SpawnRegion {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(shape, "shape");
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0, was " + radius);
        }
        if (minY > maxY) {
            throw new IllegalArgumentException(
                    "minY (" + minY + ") must be <= maxY (" + maxY + ")");
        }
    }

    /**
     * The same region moved to a new centre, for a region that follows the world spawn when the
     * spawn moves.
     *
     * @param newCenterX the new centre X block coordinate
     * @param newCenterZ the new centre Z block coordinate
     * @return a region with every other bound unchanged
     */
    public SpawnRegion centredAt(int newCenterX, int newCenterZ) {
        return new SpawnRegion(world, newCenterX, newCenterZ, radius, shape, minY, maxY);
    }

    /**
     * Tests whether the given block lies inside this region.
     *
     * @param world the world name the block is in
     * @param x the block X coordinate
     * @param y the block Y coordinate
     * @param z the block Z coordinate
     * @return {@code true} if the block is protected by this region
     */
    public boolean contains(String world, int x, int y, int z) {
        if (!this.world.equals(world)) {
            return false;
        }
        if (y < minY || y > maxY) {
            return false;
        }
        return withinFootprint(x, z);
    }

    /**
     * Tests whether a change that starts at one block and ends at another reaches into this region
     * from outside it: fluid flowing in, a block pushed or falling in, a dispenser or a growing
     * tree acting on a block inside. A change that starts inside the region belongs to the region
     * (a builder's water feature, sand placed inside it), and one that ends outside it touches
     * nothing here, so neither is protected against. The destination is tested first because it is
     * outside the region for almost every event on the server.
     *
     * @param fromWorld the world name the change starts in
     * @param fromX the starting block X coordinate
     * @param fromY the starting block Y coordinate
     * @param fromZ the starting block Z coordinate
     * @param toWorld the world name the change ends in
     * @param toX the destination block X coordinate
     * @param toY the destination block Y coordinate
     * @param toZ the destination block Z coordinate
     * @return {@code true} if the destination is inside this region and the start is not
     */
    public boolean entersFromOutside(
            String fromWorld,
            int fromX,
            int fromY,
            int fromZ,
            String toWorld,
            int toX,
            int toY,
            int toZ) {
        return contains(toWorld, toX, toY, toZ) && !contains(fromWorld, fromX, fromY, fromZ);
    }

    /**
     * Tests whether something moving from a point by the given displacement in one step would be
     * inside this region at any point of that step, start and end included: a projectile from a
     * dispenser outside the region about to fly into it this tick. The step is sampled every
     * quarter of a block, so a fast projectile cannot pass through a corner of the region between
     * samples, and at most {@value #MAX_PATH_SAMPLES} times, which covers any speed the server lets
     * a projectile reach. A displacement that is not finite is treated as staying put.
     *
     * @param world the world name the movement happens in
     * @param x the starting X coordinate
     * @param y the starting Y coordinate
     * @param z the starting Z coordinate
     * @param dx the X displacement over the step
     * @param dy the Y displacement over the step
     * @param dz the Z displacement over the step
     * @return {@code true} if any point of the step lies in a block inside this region
     */
    public boolean reachedAlong(
            String world, double x, double y, double z, double dx, double dy, double dz) {
        if (!this.world.equals(world)) {
            return false;
        }
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length)) {
            return contains(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
        int samples = (int) Math.min(MAX_PATH_SAMPLES, Math.ceil(length / PATH_STEP));
        for (int sample = 0; sample <= samples; sample++) {
            double fraction = samples == 0 ? 0 : (double) sample / samples;
            if (contains(
                    world,
                    (int) Math.floor(x + dx * fraction),
                    (int) Math.floor(y + dy * fraction),
                    (int) Math.floor(z + dz * fraction))) {
                return true;
            }
        }
        return false;
    }

    private boolean withinFootprint(int x, int z) {
        // Widen to long before squaring/subtracting so extreme coordinates cannot overflow.
        long dx = Math.abs((long) x - centerX);
        long dz = Math.abs((long) z - centerZ);
        return switch (shape) {
            case SQUARE -> Math.max(dx, dz) <= radius;
            // Reject any offset larger than the radius before squaring: this is both the
            // correct circle test and what stops dx*dx / dz*dz overflowing a long at extreme
            // (beyond-world-border) coordinates, where dx can approach 2^32.
            case CIRCLE ->
                    dx <= radius && dz <= radius && (dx * dx + dz * dz) <= (long) radius * radius;
        };
    }
}

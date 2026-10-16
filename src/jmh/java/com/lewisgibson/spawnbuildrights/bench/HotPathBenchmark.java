package com.lewisgibson.spawnbuildrights.bench;

import com.lewisgibson.spawnbuildrights.core.access.AccessResult;
import com.lewisgibson.spawnbuildrights.core.access.BuildAccessPolicy;
import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.access.PermissionState;
import com.lewisgibson.spawnbuildrights.core.region.RegionShape;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import com.lewisgibson.spawnbuildrights.core.time.PlaytimeDuration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Microbenchmarks for the per-event hot path. Every block break/place server-wide runs the
 * containment check, and in-region actions also run the access decision, so these are the only
 * methods whose cost is paid at gameplay frequency. Results are in nanoseconds per operation.
 *
 * <p>Run with {@code ./gradlew jmh}. The parse, format and display benchmarks cover the config
 * load, the config write-back and the denial message, which are not hot but are measured.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class HotPathBenchmark {

    private final SpawnRegion square =
            new SpawnRegion("world", 0, 0, 128, RegionShape.SQUARE, -64, 320);
    private final SpawnRegion circle =
            new SpawnRegion("world", 0, 0, 128, RegionShape.CIRCLE, -64, 320);
    private final BuildAccessPolicy policy = new BuildAccessPolicy(2_592_000L);

    // The inputs live in non-final state fields rather than as literals in each call, so the JIT
    // cannot fold a call on constants into a constant result and report the cost of nothing.
    private String world = "world";
    private String otherWorld = "world_nether";
    private int x = 40;
    private int y = 70;
    private int z = 40;
    private int farX = 400;
    private int edgeX = 128;
    private long playtime = 1_000_000L;
    private String duration = "1d12h";
    private long ticks = 2_592_000L;
    private long remaining = 2_425_260L;
    private double fromX = 140.5;
    private double fromY = 70.5;
    private double fromZ = 0.5;
    private double stepX = -1.5;
    private double stepY = -0.05;
    private double stepZ = 0.0;

    /**
     * The by-far most common case server-wide: an event in another world short-circuits on name.
     */
    @Benchmark
    public boolean containsOtherWorld() {
        return square.contains(otherWorld, x, y, z);
    }

    /** A containment check for a square region, the common hot-path shape. */
    @Benchmark
    public boolean containsInsideSquare() {
        return square.contains(world, x, y, z);
    }

    /** A circular region adds a squared-distance comparison to the same check. */
    @Benchmark
    public boolean containsInsideCircle() {
        return circle.contains(world, x, y, z);
    }

    /**
     * Fluid flow is the most frequent event the plugin now listens to: every water and lava spread
     * anywhere in the world. Almost all of it is far from spawn, where the destination test fails
     * on the footprint and the origin is never looked at.
     */
    @Benchmark
    public boolean entersFromOutsideFar() {
        return square.entersFromOutside(world, farX, y, farX + 1, world, farX, y, farX);
    }

    /** A flow at the boundary itself, where both ends are tested. */
    @Benchmark
    public boolean entersFromOutsideBoundary() {
        return square.entersFromOutside(world, edgeX + 1, y, 0, world, edgeX, y, 0);
    }

    /** The access decision on its own, for a player below the threshold. */
    @Benchmark
    public AccessResult evaluateDenied() {
        return policy.evaluate(playtime, PermissionState.UNSET, OverrideState.NONE);
    }

    /** The full per-event decision for an action inside the region. */
    @Benchmark
    public boolean guardDecision() {
        if (!square.contains(world, x, y, z)) {
            return true;
        }
        return policy.evaluate(playtime, PermissionState.UNSET, OverrideState.NONE).allowed();
    }

    /**
     * The one per-tick job: each projectile a dispenser outside the region fires is tested every
     * tick until it lands. A fast arrow just outside the region is the worst common case, sampled
     * along its whole step and found outside.
     */
    @Benchmark
    public boolean projectileStepOutside() {
        return square.reachedAlong(world, fromX, fromY, fromZ, stepX, stepY, stepZ);
    }

    /** Config-load cost of reading a duration. */
    @Benchmark
    public long parseThreshold() {
        return PlaytimeDuration.parseTicks(duration);
    }

    /** Config write-back cost of writing a duration, as /sbr set threshold saves it. */
    @Benchmark
    public String formatThreshold() {
        return PlaytimeDuration.format(ticks);
    }

    /**
     * Denial-message cost of showing a player the time left, paid on every refused action: 33h 41m
     * of a 36h requirement.
     */
    @Benchmark
    public String displayRemaining() {
        return PlaytimeDuration.display(remaining);
    }
}

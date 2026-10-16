# Performance

SpawnBuildRights responds to events as they happen, without scanning your world or polling players' playtime.

Block breaking and placement reach its listeners throughout the server, so their cost must stay small compared with a 50 ms (50,000,000 ns) server tick. The measurements below cover the core geometry and access policy.

## Designed for it

The player build guard tests region containment before reading player state, starting with the world name and then the height and footprint. For example, breaking a block in another world returns without reading playtime or permissions.

An action inside the region reads the permission node, stored override and playtime from memory, without a disk query, scoreboard or polling. Reading the permission takes up to two calls to the server's permission cache, and reading the override takes one `ConcurrentHashMap` lookup in the registry loaded at startup.

Database writes run on a single plugin-owned thread. During normal play, `/sbr history` is the admin command that reads the database synchronously, while startup and reload also read stored records.

Most work happens in response to player actions or environmental events, including mob block changes, explosions and display damage. Falling-block landings, fluid flow, pistons, dispensers and growth also use events. Projectiles fired by dispensers outside the region have a per-tick watch while they fly.

These protections need no world scan. Explosion protection filters the event's existing affected-block list once, and display protection examines the entity being damaged. Bukkit supplies location objects through the adapter, while denial placeholder maps and action-bar components are created only when access is refused.

## Measured

These JMH 1.37 microbenchmarks measure the core methods. Run `./gradlew jmh` to measure them on your hardware. The figures below are from a run on 2026-10-16 (3 warm-up and 5 measured iterations, one fork), as average nanoseconds per operation with JMH's 99.9% error margin.

| Benchmark                   | ns/op | Error (+-) | What it measures                                                              |
| --------------------------- | ----: | ---------: | ----------------------------------------------------------------------------- |
| `containsOtherWorld`        |  0.78 |       0.01 | Containment returning early because the world name differs                    |
| `containsInsideSquare`      |  1.40 |       0.03 | Containment within a square footprint                                         |
| `containsInsideCircle`      |  1.57 |       0.15 | Containment within a circular footprint                                       |
| `evaluateDenied`            |  1.56 |       0.39 | Access policy returning a denial                                              |
| `guardDecision`             |  1.57 |       0.03 | Containment and access policy together for an action inside the region        |
| `entersFromOutsideFar`      |  1.85 |       0.45 | A destination outside the footprint, so origin containment is not tested      |
| `entersFromOutsideBoundary` |  2.53 |       0.18 | A change at the boundary requiring containment tests for both ends            |
| `projectileStepOutside`     |  27.3 |        2.5 | One tick of a dispensed arrow's watch, seven containment tests along its step |
| `displayRemaining`          |  10.7 |        1.3 | The time left shown to a player whose action is refused                       |
| `formatThreshold`           |  11.1 |        1.1 | Compact duration formatting for configuration write-back and command output   |
| `parseThreshold`            |   168 |        4.5 | Duration parsing during configuration load and `/sbr set`                     |

Fluid flow has been handled by `onFluidFlow` since 0.7.0 and can generate many events as water and lava spread throughout the protected world. The core `entersFromOutside` method tests the destination first, so a flow away from spawn needs only one containment test.

In the 2026-10-16 run, that case took 1.85 ns with an error of +-0.45 ns, and the containment benchmarks in the same run measured 0.78 to 1.57 ns. Pistons, dispensers, falling-block landings and growth use related containment decisions, and a piston outside the region also requires a short loop over the affected blocks.

Since 0.7.1, projectile launches reach `onProjectileLaunch`. A projectile fired by a player or mob returns after the living-shooter type test, while a projectile without a living shooter can start a watch on its own scheduler when dispenser protection is enabled.

The watch ends on its first tick unless the projectile came from a dispenser outside the region. While active, it calls `reachedAlong` once per tick and continues until the projectile lands or is removed. That method performs at most 65 containment tests, and seven at a dispensed arrow's speed, which `projectileStepOutside` measures at 27 ns a tick.

## What the numbers mean

A tick contains 50,000,000 ns, compared with 1.6 ns for the core in-region decision benchmark and 0.8 ns for returning early in a different world.

These figures exclude Bukkit event dispatch, location allocation, permission lookups and explosion filtering. They measure core methods rather than whole listeners, and absolute timings vary by hardware. Use them to compare the methods rather than predict the total cost of an event on your server. The adapter's location objects and access-result records also mean the complete path should not be described as allocation-free.

`parseThreshold` is the slowest listed method at 168 ns. It uses a regular expression when configuration loads, including after `/sbr set threshold` and other `/sbr set` edits, so it is outside ordinary block-event handling. Keeping this parser readable matters more than reducing a small cost paid only when settings load.

## Re-running

```bash
./gradlew jmh   # results printed to the console and written under build/results/jmh/
```

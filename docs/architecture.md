# Architecture

The plugin separates its domain model from database access and server events. Start with [CONTRIBUTING.md](../CONTRIBUTING.md) for contribution guidance and [.claude/rules/](../.claude/rules/) for the coding rules.

## Three layers

| Layer     | Package                                    | Contents                                                                                                                            | Allowed imports                            |
| --------- | ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `core`    | `com.lewisgibson.spawnbuildrights.core`    | Region geometry, access policy, durations, vanilla-conflict detection, settings, messages, store contract and in-memory registry    | The JDK only                               |
| `storage` | `com.lewisgibson.spawnbuildrights.storage` | One JDBC store with the SQL that differs in a `Dialect`, numbered migrations, a connection source per backend and the writer thread | The JDK, `java.sql`, HikariCP, the drivers |
| `bukkit`  | `com.lewisgibson.spawnbuildrights.bukkit`  | Plugin entry point, listeners, command, config codec, messenger and playtime reader                                                 | All plugin layers and Paper                |

Region geometry and build-access decisions live in `core`. They operate on primitives and records, so they can be tested without a server.

The `bukkit` adapter identifies the relevant action and extracts its world name, coordinates, playtime and permission state. It then calls the domain model and applies the result. For example, a block-place event supplies the affected block's coordinates to `SpawnRegion.contains`, then uses the access policy to decide whether to cancel it.

Neither `core` nor `storage` may import `org.bukkit.*`.

## The hot path

Block events happen throughout the server, so most should return before reading player state. The player build guard follows this order:

1. `SpawnRegion.contains` tests the world name, height and footprint in that order, and an event outside the region returns here. The pure containment methods take under two nanoseconds in the published benchmarks.
2. For an action inside, the guard reads the build permission, stored override and playtime. The permission uses up to two calls to the server's permission cache, and the override uses one `ConcurrentHashMap` lookup in `GrantRegistry`.
3. `BuildAccessPolicy.evaluate(playtime, permission, override)` denies access if either source revokes it, then allows a grant. With neither, playtime decides.
4. If access is denied, the guard cancels the event and `BukkitMessenger` sends the action-bar message. Denial placeholder maps and message components are created only on this path.

For example, a stored grant cannot override a permission set to `false`, so the policy returns a revocation result and the player receives the revoked message.

The guard does not query the disk or database. It can queue one earned-access record per player when playtime first becomes the reason access is allowed, and that write runs on the storage thread.

See [performance.md](performance.md) for benchmark scope and allocation details.

## Settings and reload

`PluginRuntime` groups the parsed settings, access policy and grant service. The build guard uses one runtime snapshot for its decision, avoiding a mixture of old settings and a new policy.

`/sbr reload` parses the file, reloads the grant registry and builds a replacement runtime. Each `/sbr set` saves its edit and reloads the configuration. The operations share one lock, so concurrent Folia region threads cannot edit the configuration at the same time.

If parsing fails, the existing runtime stays active and the command reports the error. For example, a radius of `16a` cannot replace the running region.

## Storage

The store keeps manual overrides in `sbr_overrides` and audit history in `sbr_events`, while `sbr_schema` records the database schema version. The game owns the running playtime total, although an earned event includes the observed playtime.

An older store is upgraded in place when it opens. Event IDs keep history entries ordered when their timestamps match.

Startup and every reload load overrides and earned-player records into `GrantRegistry`, including decisions made by another server sharing the same PostgreSQL or MySQL database. A decision made through `GrantService` updates that registry before queuing its database write, so the next block event can use a grant immediately.

`StorageWorker` runs queued writes in order on one plugin-owned thread, and shutdown waits for the queue to drain. A reload also waits for queued writes before reading the registry again or closing a replaced store. These waits have timeouts.

During normal play, `/sbr history` reads the database synchronously. For example, it waits briefly for a newly queued grant before reading history so that grant can appear in the response.

## Folia

Folia runs world regions on separate threads. Event handlers use the event's blocks, entities and players rather than scanning the world, and the runtime reference is swapped atomically. Shared grant state uses thread-safe collections and synchronised updates.

The only continuing per-tick task watches projectiles fired by dispensers outside the protected region. Each watch runs on that projectile's own scheduler until it lands or is removed. For example, a watched arrow is removed when its next movement would enter the region.

Portal protection depends on `EntityPortalExitEvent`. See [compatibility.md](compatibility.md) for Folia's behaviour.

## Why stable API only

The plugin uses Bukkit and Paper API, the game's statistics and Adventure. It does not use NMS, CraftBukkit classes or reflection, which reduces dependence on a particular server build and helps the same jar work on later builds.

The weekly Upgrade Watch workflow tests whether it still loads on the newest Paper build.

## Tests

| Suite                      | Where                                     | Coverage                                                                                      |
| -------------------------- | ----------------------------------------- | --------------------------------------------------------------------------------------------- |
| Unit                       | `src/test`                                | Decisions in `core` and queries in `storage`, using a real SQLite file                        |
| Integration                | `src/integrationTest`                     | Listeners, command and config codec through MockBukkit                                        |
| Coverage gate              | `build.gradle.kts`                        | Unit and integration suites together cover every line and branch, with nothing excluded       |
| Server                     | `src/serverTest`, `src/serverTestFixture` | The real jar on Paper 26.3 in Docker through RCON, with SQLite, PostgreSQL, MySQL and MariaDB |
| Load (CI)                  | `.github/workflows/build.yaml`            | Loading the jar on Paper, Purpur and Folia                                                    |
| Upgrade watch (CI, weekly) | `.github/workflows/upgrade-watch.yaml`    | Loading the jar on the newest Paper build                                                     |

The server suite covers behaviour that MockBukkit cannot reproduce. A fixture plugin fires real block events for a simulated player, and the harness triggers explosions, wither skulls and fire. It also sends sand, lava and water across the boundary and exercises pistons, dispensers, growing trees and End exit portals.

The suite examines the resulting world blocks. The protections against changes from outside and each kind of portal arrival are also run with their switch off, and the explosion, mob, display and fire scenarios compare the same event just inside and just outside the region. For example, lava must enter the region with fluid protection disabled before the protected result can demonstrate that the plugin stopped it.

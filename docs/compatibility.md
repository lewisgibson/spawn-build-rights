# Compatibility

Use Java 25, which Minecraft 26.x already requires.

| Server                   | Supported | Notes                                                                                                             |
| ------------------------ | :-------: | ----------------------------------------------------------------------------------------------------------------- |
| Paper 26.3               |    ✅     | Built and tested against build 194                                                                                |
| Purpur 26.3              |    ✅     | Uses Paper's API. The same jar provides the same behaviour                                                        |
| Folia 26.3               |    ✅     | All protections except `portal-arrivals`. Folia moves entities through portals without sending the required event |
| Spigot or CraftBukkit    |    No     | They do not provide the Paper Adventure API used for player messages                                              |
| Minecraft 26.2 and older |    No     | `api-version` is 26.3, so the plugin will not load                                                                |

## How it is tested

Unit tests cover the decision logic, including region geometry, access rules, durations, settings and storage. Integration tests run the listeners and commands against MockBukkit, and together these suites cover 100% of the plugin's lines and branches.

`./gradlew serverTest` starts real Paper 26.3 servers in Docker, and runs the build gate on SQLite, PostgreSQL, MySQL and MariaDB. Through RCON it exercises explosions, wither skulls, fire, sand, lava, pistons, dispensers, trees and End exit portals across the region boundary. The five protections against changes from outside and each kind of portal arrival are also run with their switch off, to confirm the change would otherwise happen. The explosion, mob, display and fire scenarios compare the same event just inside and just outside the region.

Every push to main and every pull request also loads the jar on Paper, Purpur and Folia. The protection scenarios run on Paper. A weekly run tests loading on the newest Paper build.

## Newer Minecraft versions

The plugin uses Paper's stable API without server internals, so a release can continue working on later Paper builds and Minecraft versions without a rebuild.

The `+mc26.3` part of the jar filename identifies the Minecraft version used to build and test that release. It does not promise compatibility with every future version.

## Other plugins

Permissions plugins such as LuckPerms work through `spawnbuildrights.build`, or you can use SpawnBuildRights without a permissions plugin. See [Commands and permissions](commands.md).

WorldGuard, GriefPrevention and Lands can run alongside SpawnBuildRights. Every protection plugin must allow a change before it can happen, so a player with enough playtime here can still be refused by a WorldGuard region covering spawn.

Vanilla `spawn-protection` in `server.properties` also takes precedence. Set it to `0` so it does not block earned builders, and SpawnBuildRights warns about the conflict unless you disable its warnings.

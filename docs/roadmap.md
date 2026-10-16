# Roadmap

Planned work that has not started. Nothing on this page is in a release yet.

## NeoForge

A server-side NeoForge mod that brings SpawnBuildRights to modded servers: the same build rule, the same protections and the same commands, from one repository that builds two jars, the Paper plugin and the NeoForge mod. Players would still install nothing.

### What can be reused

The plugin is already split so that most of the thinking is free of Paper (see [architecture.md](architecture.md)). Counting lines of code without comments or blank lines:

| Layer     | Lines | On NeoForge                                                                                      |
| --------- | ----: | ------------------------------------------------------------------------------------------------ |
| `core`    |   668 | Reused unchanged: the region, the access rule, durations, settings, messages, the store contract |
| `storage` |   507 | Reused unchanged: the JDBC store for SQLite, PostgreSQL and MySQL and its writer thread          |
| `bukkit`  | 1,769 | Rewritten as a `neoforge` adapter: listeners, the command, the config codec, the messenger       |

So about 40% of the code moves across as it is, and its tests with it. The part to write again is the thin layer that reads events and acts on them, which is also the part that needs the most testing.

### What each piece becomes

The NeoForge names below are the events and APIs to look for first. Each one has to be checked against the NeoForge jar for Minecraft 26.3 before it is relied on, because NeoForge renames and reshapes events between versions.

| Piece                                | Paper today                                         | NeoForge                                                                                                                        |
| ------------------------------------ | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Breaking and placing blocks          | `BlockBreakEvent`, `BlockPlaceEvent`                | `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`                                                                          |
| Buckets, signs, frames, stands, pots | Bucket, sign, hanging and armour stand events       | `FillBucketEvent` and `PlayerInteractEvent.RightClickBlock` / `EntityInteract`, with the item and block checked                 |
| Trampling, bone meal                 | `EntityChangeBlockEvent`, `BlockFertilizeEvent`     | `BlockEvent.FarmlandTrampleEvent`, `BonemealEvent`                                                                              |
| Explosions                           | `EntityExplodeEvent`, `BlockExplodeEvent`           | `ExplosionEvent.Detonate`, which carries the list of affected blocks to trim, as today                                          |
| Hostile mobs changing blocks         | `EntityChangeBlockEvent`                            | `EntityMobGriefingEvent` is only a yes or no per mob, not per block, so per-block trimming needs a mixin                        |
| Displays                             | `EntityDamageEvent`, `HangingBreakEvent`            | `LivingIncomingDamageEvent` for armour stands; frames and paintings need `AttackEntityEvent` plus a mixin for non-player damage |
| Fire spreading and burning           | `BlockSpreadEvent`, `BlockBurnEvent`                | No event: a mixin into the fire block's tick                                                                                    |
| Falling blocks landing               | `EntityChangeBlockEvent` for the falling block      | No event: a mixin where the falling block entity places itself                                                                  |
| Water and lava flowing in            | `BlockFromToEvent`                                  | No spread event (`BlockEvent.FluidPlaceBlockEvent` covers only fluids making stone): a mixin into fluid spreading               |
| Pistons                              | `BlockPistonExtendEvent`, `BlockPistonRetractEvent` | `PistonEvent.Pre`, which is cancellable before the piston moves                                                                 |
| Dispensers and what they fire        | `BlockDispenseEvent`, `ProjectileLaunchEvent`       | No dispense event: a mixin into the dispenser, and `ProjectileImpactEvent` plus a per-tick watch for projectiles                |
| Trees and huge mushrooms             | `StructureGrowEvent`                                | `BlockGrowFeatureEvent` lets the growth be cancelled but not trimmed block by block, so trimming needs a mixin                  |
| Portal arrivals                      | `EntityPortalExitEvent`                             | `EntityTravelToDimensionEvent` fires before the trip; the arrival point has to be worked out the way the game does it           |
| Playtime                             | `Player.getStatistic(PLAY_ONE_MINUTE)`              | `ServerPlayer.getStats()` read for `Stats.PLAY_TIME`: the same statistic, so existing playtime carries over                     |
| Permissions                          | Bukkit permissions, `plugin.yml` defaults           | NeoForge's permission API (`PermissionNode`), which LuckPerms for NeoForge provides; operators by permission level              |
| Commands                             | Bukkit command and tab completer                    | A Brigadier command registered in `RegisterCommandsEvent`, with the same subcommands and replies                                |
| Messages                             | Adventure and MiniMessage, built into Paper         | The Adventure platform for NeoForge and MiniMessage shaded into the jar, so `config.yml` messages keep working                  |
| Config                               | `config.yml` through Bukkit's YAML                  | The same `config.yml` read with SnakeYAML, so a server owner moving from Paper keeps their file                                 |
| Database drivers                     | Downloaded by Paper from `plugin.yml`               | Shipped inside the jar with NeoForge's jar-in-jar, which makes the mod about 13 MB                                              |
| Vanilla spawn protection warning     | `server.properties` via the Bukkit server           | The same setting read from the dedicated server's properties                                                                    |

### Build setup

One repository, two jars from one Gradle build:

- `core` and `storage` become a shared Gradle subproject with no Minecraft dependency, tested as today.
- `paper` is the current adapter and builds `SpawnBuildRights-<version>+mc26.3.jar`.
- `neoforge` is the new adapter, built with NeoForge's Gradle plugin (ModDevGradle), and builds `spawnbuildrights-neoforge-<version>+mc26.3.jar` with the shared code and the drivers inside it.
- The release workflow publishes both jars to the same GitHub release, and to Modrinth as two versions of one project, one for the Paper loaders and one for NeoForge.

### Testing

MockBukkit has no NeoForge equivalent, so the adapter would be tested two ways:

- NeoForge's GameTest framework, which runs scenarios inside a real server and can place blocks, spawn mobs and fire pistons. This replaces the MockBukkit integration tests and is what makes 100% coverage of the adapter reachable.
- The Docker server tests, booted with `TYPE=NEOFORGE` instead of `TYPE=PAPER`, driving the same scenarios over RCON. Most of the existing scenarios carry over if the fixture plugin is ported to a small test mod.

### Effort

About two to three weeks of focused work:

| Step                                                                                | Estimate    |
| ----------------------------------------------------------------------------------- | ----------- |
| Split the build into shared, Paper and NeoForge subprojects                         | 1 day       |
| Build rights: blocks, buckets, signs, displays, interactions                        | 2 days      |
| Explosions, pistons, playtime, permissions, commands, config, messages              | 3 days      |
| The mixins: fire, falling blocks, fluids, dispensers, growth trimming, mob griefing | 4 to 5 days |
| Portal arrivals                                                                     | 1 to 2 days |
| GameTests to 100% coverage, the NeoForge server tests, docs and the release         | 3 to 4 days |

### Risks

- **Mixins.** Six of the ten protections have no NeoForge event that can do the job, so they need mixins into Minecraft's own code. Mixins are tied to the exact game version and can break, or clash with other mods that change the same method, on any Minecraft update. Each would need the fail-soft approach Preheat uses: check the target exists before applying, and turn just that protection off, with a log line, if it does not.
- **Other mods.** Modded servers have blocks, machines and mobs from other mods that move, break and place blocks in ways the vanilla events never see, such as quarries, block breakers, item pipes and modded explosives. Some would be covered by the generic events and some would not. The docs would have to say plainly which protections cover vanilla only.
- **Permissions.** NeoForge's permission API is used by fewer mods than Bukkit's, and some modded servers run without any permissions mod. `/sbr grant` and `/sbr revoke` keep working without one, as they do on Paper.
- **Two adapters to keep in step.** Every new protection or command would have to be written twice, and tested twice, for as long as both jars are supported.
- **Version spread.** Many modded servers stay on older Minecraft versions for their modpack, so a NeoForge port for 26.3 only would reach fewer servers than its Paper sibling until packs catch up.

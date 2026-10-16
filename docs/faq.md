# FAQ

**Why use this instead of vanilla spawn protection?** Vanilla protection keeps building near spawn for server operators. SpawnBuildRights builds on it: new players can use spawn straight away and earn the right to build there by playing, so you do not have to grant each regular access by hand.

**Do players need to install anything?** No. SpawnBuildRights runs on your server and players join with their ordinary game.

**Does playtime from before installation count?** Yes. The plugin reads the game's existing playtime statistic, so regulars who already meet your requirement can build immediately unless their access has been revoked.

**Does AFK time count?** Yes. The game counts time spent logged in, including idle time. Another plugin can limit this by kicking idle players, as time stops counting while they are disconnected.

**What can a new player still do at spawn?** They can walk, fight, sleep and trade with villagers from their first minute. Doors and gates stay usable, along with buttons, levers and chests. They can also use furnaces and crafting tables. [How it works](how-it-works.md#what-counts-as-building) lists the actions that need build rights.

**Can I let a trusted player build early or stop someone who has earned access?** Yes. Use `/sbr grant <player>` or `/sbr revoke <player>`, with an optional reason you can read later in `/sbr history`. You can also set `spawnbuildrights.build` to true or false in your permissions plugin. If a grant and revocation conflict, the revocation wins.

**Can I protect more than spawn?** You can move the region wherever you want, but the plugin protects only one region. Use a claims plugin alongside it if you need several protected areas.

**Does it stop creepers and TNT wrecking spawn?** Yes. The default protections keep your build safe from explosions and fire, and stop endermen and withers damaging it. They also stop lava, pistons and dispensers reaching in from outside and defuse dangerous portal arrivals. [Protections](protections.md) explains each switch.

**Will it slow my server down?** The plugin responds to events without scanning the world, and most build decisions finish as soon as the action is found to be outside the region. The core decision takes a few nanoseconds in measurements. See [Performance](performance.md) for the benchmarks and how the plugin handles its work.

**Does it need a database?** There is no database server to set up, as the plugin creates its own SQLite file for grants, revocations and history. You can choose PostgreSQL, MySQL or MariaDB instead ([Storage](storage.md)).

**Can players see that I run it?** Players without `spawnbuildrights.info` cannot use `/sbr` or see it in tab completion, but they can still see denial messages when they try to change protected blocks. Give everyone the permission if you want them to be able to run `/sbr`, which tells them in one sentence whether they can build yet.

**How do I remove it?** Stop the server and delete the jar from `plugins`. You can also delete `plugins/SpawnBuildRights` if you do not want to keep its settings and records. There are no plugin blocks or world data to convert, and removing it does not undo changes players made while it was running.

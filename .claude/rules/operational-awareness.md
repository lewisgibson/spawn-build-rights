# Operational Awareness

What the plugin can count on from the server it runs in, and what it promises back.

- **A server-side jar.** It loads from a Paper, Purpur or Folia server's `plugins/` folder. Plain Spigot lacks the Adventure API it sends messages with. There is no client mod.
- **One region, event-driven.** It protects one region in one world and does almost nothing on the tick loop. It answers player actions, explosions, mob block changes, ignition, fire, display damage, portal arrivals, falling blocks landing, fluid flow, pistons, dispensers and growth. The one per-tick job is a watch on each projectile a dispenser outside the region fires, on that projectile's own scheduler, until it lands. Each decision must stay small against the 50 ms tick (`docs/performance.md`).
- **The store is small and off the hot path.** SQLite by default in the plugin's folder, or PostgreSQL, MySQL or MariaDB. It holds the grants and revocations made by hand and their log, never playtime, which the game keeps. It is read into memory at startup and on reload; writes go to one plugin-owned thread that is drained on shutdown. SQLite uses the driver Paper ships; HikariCP and the PostgreSQL and MariaDB drivers come through `plugin.yml` `libraries:`, so the first start needs Maven Central.
- **Config applies live.** `/sbr reload` and `/sbr set` rebuild the runtime and write back to `config.yml` without a restart.
- **It must not fight vanilla spawn protection.** Vanilla `spawn-protection` is applied before any plugin, so the plugin warns in the console at startup and tells operators privately when they join.
- **Built once, runs on later versions.** It is built against a pinned Paper API but uses only stable API. The weekly Upgrade Watch workflow boots it on the newest Paper and opens an issue if it ever fails.
- **Releases are automatic.** A push to `main` whose version has no GitHub release yet publishes it to GitHub Releases, and to Modrinth and Hangar when their tokens are set, with the version's `CHANGELOG.md` entry as the notes (`docs/releasing.md`). A push that keeps the version publishes nothing.

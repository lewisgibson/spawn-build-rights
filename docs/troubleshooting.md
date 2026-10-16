# Troubleshooting

**Players have earned build rights but still cannot build at spawn.** First check vanilla spawn protection, then set `spawn-protection=0` in `server.properties` and restart. The plugin warns about this in the startup console and when server operators join, unless those warnings are disabled. Also look at the player's build permission and stored decision. If SpawnBuildRights allows them to build, look for another plugin's region or claim around spawn.

**A player says they have played long enough.** Ask them to run `/sbr`, which tells them in one sentence whether they can build and, if not, how much longer they need. They need `spawnbuildrights.info`, which you can grant for ten minutes with LuckPerms using `/lp user <name> permission settemp spawnbuildrights.info true 10m`. Use `/sbr history <player>` to see their recorded decisions and any earned-access entry. The playtime comes from the same game statistic shown on their Statistics screen.

**Server operators can build but should have to earn access.** They hold `spawnbuildrights.build` by default, and setting it to `false` revokes access completely even after enough playtime. To make playtime decide, the node must resolve as unset rather than granted or revoked, and the player must have no stored override. Removing an explicit permission alone may leave the default operator grant in effect.

**The plugin does not enable on its first start.** Read the startup error. The first load downloads HikariCP and the PostgreSQL and MariaDB drivers from Maven Central, so if the server cannot reach it, copy the whole `libraries` folder from a server that has already loaded the plugin.

**A change to `config.yml` did nothing.** Run `/sbr reload`. If a setting is invalid, the plugin reports the problem and keeps its previous settings. For example:

```
Reload failed: region.radius must be a whole number, was '16a'
```

An unknown key is ignored and logged when the configuration loads. If you misspell a key and leave out the correct one, its default still applies:

```
[SpawnBuildRights] config.yml has a key this plugin does not know, 'protection.explosion'; it is ignored. Check its spelling.
```

**The plugin is disabled after a restart and spawn is unprotected.** A configuration or storage error can prevent startup, when there are no previous settings to fall back on. Read the error above `Disabling SpawnBuildRights; fix config.yml and restart.`, correct the reported problem and restart.

**Players see "unknown command" for `/sbr`.** The command is hidden from players without `spawnbuildrights.info`, so give them that permission if you want them to use `/sbr`.

**Something exploded at spawn on Folia.** See [Compatibility](compatibility.md) for Folia's portal-arrival limitation. [Protections](protections.md) explains the difference between protecting blocks and preventing damage to players.

If you still need help, open an issue with your `config.yml`, the server version from `/version` and the startup console lines. Remove any database password from the configuration you share.

# Deployment

Install one jar on your server. The [README](../README.md#install) has the short installation guide.

## What ships

`SpawnBuildRights-<version>.jar` is about 100 KB and contains no other libraries. The version names the Minecraft version the jar was built for, such as `1.0.0+mc26.3`.

HikariCP and the PostgreSQL and MariaDB drivers are listed under `libraries:` in `plugin.yml`. Paper downloads them from Maven Central into the server's `libraries` folder on first load, and later starts reuse them. SQLite uses the driver Paper ships.

## Installing

1. Stop the server.
2. Put the jar in `plugins/`.
3. Set `spawn-protection=0` in `server.properties`. See [Vanilla spawn protection](#vanilla-spawn-protection).
4. Start the server.

On a first start using SQLite, the console shows lines like these. The database path depends on where you installed your server:

```
[SpawnBuildRights] Loading server plugin SpawnBuildRights v1.0.0+mc26.3
[SpawnBuildRights] Enabling SpawnBuildRights v1.0.0+mc26.3
[SpawnBuildRights] Store: sqlite /srv/minecraft/plugins/SpawnBuildRights/data.db (0 overrides)
```

The default region is a square with a 16-block radius around world spawn, and players earn build rights once they have played for the time set in `required-playtime`.

To change these settings, edit `config.yml` and run `/sbr reload`, or use `/sbr set` in game. For example, `/sbr set radius 24` changes the radius and saves it.

## Files

| Path                                                            | Contents                                                                                 |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `plugins/SpawnBuildRights/config.yml`                           | Settings, created with comments on first start. See [configuration.md](configuration.md) |
| `plugins/SpawnBuildRights/data.db`                              | SQLite grants, revocations and audit history. See [storage.md](storage.md)               |
| `libraries/com/zaxxer/...`                                      | HikariCP, the connection pool for PostgreSQL and MySQL, downloaded by Paper              |
| `libraries/org/postgresql/...`                                  | PostgreSQL driver downloaded by Paper                                                    |
| `libraries/org/mariadb/...`                                     | MariaDB driver, used for MySQL and MariaDB, downloaded by Paper                          |
| `libraries/org/slf4j/...`, `libraries/org/checkerframework/...` | Two small libraries HikariCP and the PostgreSQL driver need                              |

The game keeps the running playtime statistic in `world/players/stats/<uuid>.json`, and SpawnBuildRights reads it through the server API. Its own audit log keeps only the playtime observed when earned access is recorded.

## Vanilla spawn protection

Set `spawn-protection=0` in `server.properties` and restart so Minecraft does not refuse players who have earned build rights.

Vanilla spawn protection blocks everyone except server operators within its radius, before the plugin receives the attempt. With the warning enabled, each startup logs:

```
[SpawnBuildRights] ================================================================
[SpawnBuildRights] Vanilla spawn-protection is ENABLED (radius 16).
[SpawnBuildRights] It overrides SpawnBuildRights and blocks players who have earned build access.
[SpawnBuildRights] Set 'spawn-protection=0' in server.properties and restart to fix.
[SpawnBuildRights] ================================================================
```

The plugin also sends the private `operator-conflict-warning` message when a server operator or admin joins.

If you intend to keep vanilla protection, disable the console warning with `warn-on-vanilla-spawn-protection: false` and the join message with `alert-operators-on-join: false`. Both settings are in `config.yml`.

## A server without internet access

The first load needs Maven Central to download both drivers. If your server cannot reach it, start the plugin once on a machine with internet access and copy that server's whole `libraries` folder to the offline server before starting it.

## Upgrading

Stop the server and replace the old jar with the new one, then start the server again.

Your `config.yml` and `data.db` are kept, and an older store is upgraded in place on first start. New protections default to on when their keys are missing from an older configuration. Read the [changelog](../CHANGELOG.md) for the changes in each release.

## Several servers

Each server has its own region and configuration. To share grants and revocations, point the servers at the same PostgreSQL database and tables as described in [storage.md](storage.md).

After making a decision on one server, run `/sbr reload` on the others to load it there. Restarting those servers also loads the shared decisions.

## Removing it

Stop the server and delete the jar. You can also delete `plugins/SpawnBuildRights` if you no longer want its settings and local records.

No world conversion or restoration is needed when you remove the plugin.

## Releasing

How versions, the changelog and releases work is in [releasing.md](releasing.md).

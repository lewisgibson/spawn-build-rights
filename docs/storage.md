# Storage

SpawnBuildRights keeps the decisions made with `/sbr grant`, `/sbr revoke` and `/sbr reset`, and a log of who made each one, when and why. The log also records the first time each player earned build rights through playtime, with their playtime at that moment. The playtime itself is kept by the game, not by the plugin.

`/sbr history <player>` shows a player's current decision and their log, newest first.

## SQLite (the default)

The default store is `plugins/SpawnBuildRights/data.db`. It needs no setup and suits almost every server.

Back up `plugins/SpawnBuildRights/` with the rest of your server. Stop the server before copying the folder, or the copy may miss recent writes.

To move or rename the file, change [`storage.sqlite.file`](configuration.md#storage). Relative paths start inside `plugins/SpawnBuildRights/`, and full paths work too.

## PostgreSQL

Create a database and a user for the plugin:

```sql
CREATE DATABASE spawnbuildrights;
CREATE USER spawnbuildrights WITH PASSWORD 'change-me';
ALTER DATABASE spawnbuildrights OWNER TO spawnbuildrights;
```

Then point the plugin at it:

```yaml
storage:
    type: postgres
    postgres:
        host: db.example.internal
        port: 5432
        database: spawnbuildrights
        user: spawnbuildrights
        password: 'change-me'
        schema: ''
```

The database, and the schema if you name one, must already exist. A blank `schema` uses the user's default schema, normally `public`. The plugin creates its tables on first start, so the user needs permission to create tables there. To give the plugin a schema of its own, create it first:

```sql
CREATE SCHEMA spawnbuildrights AUTHORIZATION spawnbuildrights;
```

PostgreSQL 9.5 or newer works. The tests use PostgreSQL 16.

## MySQL and MariaDB

Create a database and a user for the plugin:

```sql
CREATE DATABASE spawnbuildrights CHARACTER SET utf8mb4;
CREATE USER 'spawnbuildrights'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON spawnbuildrights.* TO 'spawnbuildrights'@'%';
```

Then point the plugin at it:

```yaml
storage:
    type: mysql
    mysql:
        host: db.example.internal
        port: 3306
        database: spawnbuildrights
        user: spawnbuildrights
        password: 'change-me'
```

`type: mariadb` means the same thing. Both servers are reached through the MariaDB driver, which supports MySQL as well. The tests use MySQL 8.4 and MariaDB 11.8.

## Sharing a database between servers

Several servers can share one PostgreSQL or MySQL database, so a player granted or revoked on one is granted or revoked on all of them. Each server reads the shared decisions at startup and on `/sbr reload`, so after a change on one server, run `/sbr reload` on the others to apply it there.

## TLS

Both server databases can connect over TLS. The startup line shows which mode is in use:

```
[SpawnBuildRights] Store: postgres spawnbuildrights@db.example.internal:5432/spawnbuildrights sslmode verify-full (3 overrides)
```

### PostgreSQL

`storage.postgres.ssl-mode` is the PostgreSQL driver's `sslmode`:

| Mode          | Meaning                                                                     |
| ------------- | --------------------------------------------------------------------------- |
| `disable`     | No TLS                                                                      |
| `allow`       | No TLS unless the server insists on it                                      |
| `prefer`      | TLS when the server offers it, plain otherwise. The default.                |
| `require`     | Always TLS, without checking the server's certificate                       |
| `verify-ca`   | TLS with a server certificate signed by the CA in `ssl-root-cert`           |
| `verify-full` | As `verify-ca`, and the certificate must also match `storage.postgres.host` |

For `verify-ca` and `verify-full`, set `ssl-root-cert` to the CA certificate's path. Left blank, the driver looks for `~/.postgresql/root.crt` in the home folder of the user running the server.

```yaml
storage:
    type: postgres
    postgres:
        host: db.example.internal
        port: 5432
        database: spawnbuildrights
        user: spawnbuildrights
        password: 'change-me'
        ssl-mode: verify-full
        ssl-root-cert: 'plugins/SpawnBuildRights/ca.crt'
```

### MySQL and MariaDB

`storage.mysql.ssl-mode` is the MariaDB driver's `sslMode`:

| Mode          | Meaning                                                                  |
| ------------- | ------------------------------------------------------------------------ |
| `disable`     | No TLS. The default.                                                     |
| `trust`       | TLS, without checking the server's certificate                           |
| `verify-ca`   | TLS with a server certificate signed by the CA in `ssl-ca-cert`          |
| `verify-full` | As `verify-ca`, and the certificate must also match `storage.mysql.host` |

For `verify-ca` and `verify-full`, set `ssl-ca-cert` to the CA certificate's path. Left blank, Java's own list of trusted CAs is used.

Without TLS, the plugin lets the driver fetch the server's public key, which MySQL 8's default login method needs to send a password over a plain connection. Use TLS when the database is on another network.

## The drivers

The jar contains no database drivers.

- SQLite uses the driver Paper ships with the server.
- For PostgreSQL, MySQL and MariaDB, Paper downloads the [PostgreSQL driver](https://github.com/pgjdbc/pgjdbc), the [MariaDB driver](https://github.com/mariadb-corporation/mariadb-connector-j) and the [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool from Maven Central the first time the plugin loads, about 2.5 MB in all, and keeps them in the server's `libraries` folder.

If the server cannot reach the internet on that first start, the plugin cannot load. Copying the `libraries` folder from a server that has already run it works too.

## Connections

The SQLite store opens its file for each read or write and closes it again, so nothing is left open between writes.

PostgreSQL and MySQL go through a pool of at most two connections. The pool checks a connection before using it, replaces one the database or a firewall has dropped, and keeps an idle one alive. Connecting and each query time out, so a database that stops answering makes a write fail with an error in the log rather than wait forever.

## The tables

Every database gets the same three tables, created on first start:

| Table           | One row per            | Columns                                                                                                                                                                               |
| --------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sbr_overrides` | Player with a decision | `player_uuid` (the key), `player_name`, `state` (`GRANTED` or `REVOKED`), `actor` (who made it), `reason`, `at_millis`                                                                |
| `sbr_events`    | Entry in the log       | `id` (numbered in the order entries were written), `player_uuid`, `player_name`, `kind` (`GRANTED`, `REVOKED`, `RESET` or `EARNED`), `actor`, `reason`, `playtime_ticks`, `at_millis` |
| `sbr_schema`    | The schema version     | `version`                                                                                                                                                                             |

Times are milliseconds since 1970 UTC, and playtime is in game ticks, 20 to the second.

The schema version is 2. When a later version of the plugin changes the tables, it upgrades them on startup, one numbered step at a time. Each step and its new version number are saved together, so a step that fails leaves the version where it was. A store written by an earlier release is upgraded in place the same way, with nothing lost. A plugin older than the database refuses to start rather than read tables it does not understand.

## When it writes

Decisions are read into memory at startup and on `/sbr reload`, and a command changes that copy at once. The write to the store goes into a queue and runs on the plugin's own background thread, so a block event never waits for the database. Shutdown waits up to 10 seconds for queued writes. A crash can lose changes still in the queue.

If the store cannot be opened or read at startup, the plugin says why and disables itself rather than start with no record of who was granted or revoked.

A reload that changes the storage settings opens and reads the new store, finishes the old store's queued writes, then switches to the new one. It does not copy data across. If the new store cannot be opened or read, the old one stays in use.

## Moving to another database

There is no built-in way to copy the data. Set `storage.type` and its connection settings, start the server once so the plugin creates empty tables, and stop it. Then copy the rows of `sbr_overrides` and `sbr_events` across, with a tool such as `pgloader` or a CSV export and import, and start the server again. The previous file or database is left as it was.

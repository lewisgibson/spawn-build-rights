# Architecture

The code is split three ways, and every change keeps the split.

## The seam

- **`core` is pure.** Everything under `com.lewisgibson.spawnbuildrights.core` (region geometry, the access policy, durations, conflict detection, the settings and message model, the store contract and the in-memory registry) holds the decision logic and imports no `org.bukkit.*` type. It works on primitives and its own value types, so it is unit-tested without a server.
- **`storage` is pure JDBC.** One `JdbcGrantStore` whose differing SQL sits in a `Dialect`, numbered migrations, a connection source per backend (SQLite, or PostgreSQL and MySQL through a HikariCP pool) and the `StorageWorker` thread. It imports `java.sql`, the pool and the drivers, never `org.bukkit.*`. The hot path never reaches it: each event reads the in-memory `GrantRegistry`, and writes are handed to the worker.
- **`bukkit` is a thin adapter.** The plugin, listeners, command, config codec, messenger and playtime reader take primitives from Bukkit types, ask `core` for a decision and act on it. No decision logic lives there.

New logic belongs in `core` wherever it can go. An `org.bukkit.*` import under `core` or `storage` is a bug, and so is a store call from a block event.

## Value types and invariants

- Domain types are immutable records that validate their invariants in the constructor, so an invalid one cannot exist.
- Geometry is overflow-safe: widen before squaring, and check offsets against the radius before multiplying.
- The runtime settings are swapped as one snapshot on reload, and each listener reads one snapshot per event.

## Platform safety

Only stable Paper API: block and entity events, `Statistic`, config, commands and Adventure text. No NMS, no CraftBukkit internals and no reflection into server classes. That keeps a jar built once working on later server versions, and every API the plugin touches is safe on Folia's region threads.

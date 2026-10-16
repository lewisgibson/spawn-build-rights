# Configuration

Choose your region and playtime requirement, then write the messages your players will see. All settings live in `plugins/SpawnBuildRights/config.yml`, which the plugin creates with comments on first start.

## Applying a change

Edit `config.yml` and run `/sbr reload`, or restart the server. The new settings apply to the next block event without players needing to relog.

You can also change settings in game with `/sbr set threshold`, `/sbr set radius`, `/sbr set shape` and `/sbr set center here`. Each applies the change immediately and saves it in `config.yml`, so `/sbr set threshold 2d` sets a two-day requirement that survives a restart. See [commands.md](commands.md#sbr-set) for every option.

If a value is invalid, `/sbr reload` reports the problem and keeps the previous settings. A value of `16a` for `region.radius` gives this response:

```
Reload failed: region.radius must be a whole number, was '16a'
```

At startup there are no previous settings to keep, so the plugin disables itself and logs:

```
[SpawnBuildRights] Invalid configuration or unusable store: region.radius must be a whole number, was '16a'
[SpawnBuildRights] Disabling SpawnBuildRights; fix config.yml and restart.
```

An unknown key is ignored and logged when the configuration loads, usually because of a spelling mistake:

```
[SpawnBuildRights] config.yml has a key this plugin does not know, 'protection.explosion'; it is ignored. Check its spelling.
```

A missing key uses its default. All protections default to on, so an older configuration gains new protections when you upgrade.

Reload also reads saved build-rights decisions from the store. If your servers share a database, `/sbr reload` picks up grants and revocations made on the others.

## The whole file

These are the defaults with the comments removed:

```yaml
region:
    world: ''
    center-x: ''
    center-z: ''
    shape: square
    radius: 16
    min-y: -64
    max-y: 320

required-playtime: 36h

build-permission: spawnbuildrights.build

storage:
    type: sqlite
    sqlite:
        file: data.db
    postgres:
        host: localhost
        port: 5432
        database: spawnbuildrights
        user: spawnbuildrights
        password: ''
        schema: ''
        ssl-mode: prefer
        ssl-root-cert: ''
    mysql:
        host: localhost
        port: 3306
        database: spawnbuildrights
        user: spawnbuildrights
        password: ''
        ssl-mode: disable
        ssl-ca-cert: ''

warn-on-vanilla-spawn-protection: true
alert-operators-on-join: true

messages:
    build-allowed: '<green>You can build at spawn</green>'
    build-denied: '<red>You can build at spawn after another <white>%remaining%</white> of play</red>'
    build-revoked: '<red>Your permission to build at spawn has been removed</red>'
    operator-conflict-warning: '<gold>SpawnBuildRights:</gold> <yellow>vanilla spawn protection is on with a radius of %radius% and stops players who have earned build rights. Set <white>spawn-protection=0</white> in server.properties</yellow>'

protection:
    explosions: true
    hostile-mob-changes: true
    display-damage: true
    fire-damage: true
    falling-blocks: true
    fluid-flow: true
    pistons: true
    dispensers: true
    growth: true
    portal-arrivals:
        explosives: true
        wither-skulls: true
        fireballs: true
        projectiles: true
        fireworks: true
        falling-blocks: true
```

## `region`

Build rights and the [protections](protections.md) apply within this area. Changes from outside are also stopped when they would affect blocks or displays inside.

### `region.world`

| Default | Accepts                              |
| ------- | ------------------------------------ |
| `''`    | The name of a loaded world, or blank |

Leave this blank to use the server's main world, named by `level-name` in `server.properties` and normally called `world`.

To protect a hub in another world, use its name:

```yaml
region:
    world: lobby
```

If that world is not loaded, the plugin uses the main world and logs `Configured region.world 'lobby' not found; using the default world.` If another plugin such as Multiverse loads the world later, run `/sbr reload` after it has loaded.

The region covers exactly one world, so matching coordinates in the Nether or the End are not protected.

### `region.center-x` and `region.center-z`

| Default | Accepts                                |
| ------- | -------------------------------------- |
| `''`    | Whole block coordinates, or both blank |

Leave both blank to follow the world spawn. Moving spawn with `/setworldspawn` moves the region immediately and logs the new centre in the console.

To keep the region at fixed coordinates, set both values:

```yaml
region:
    center-x: 120
    center-z: -340
```

You can also stand at the intended centre in the region's world and run `/sbr set center here` to save your block's X and Z coordinates.

Once both coordinates are set, moving the world spawn leaves the region where it is. To follow spawn again, blank both keys and run `/sbr reload`. Setting only one coordinate is refused.

### `region.shape` and `region.radius`

| Key      | Default  | Accepts                   |
| -------- | -------- | ------------------------- |
| `shape`  | `square` | `square` or `circle`      |
| `radius` | `16`     | A whole number, 0 or more |

The radius measures blocks out from the centre block.

- `square` includes blocks whose X and Z coordinates are each within the radius. A radius of 16 covers a 33 x 33 square, matching vanilla `spawn-protection=16`.
- `circle` includes blocks whose horizontal distance from the centre is at most the radius. A radius of 16 gives a disc 33 blocks across with rounded edges.

For a centre at 0, 0:

| Setting                | Block 16, 0 | Block 11, 11 | Block 16, 16 | Block 17, 0 |
| ---------------------- | :---------: | :----------: | :----------: | :---------: |
| `square`, `radius: 16` |   inside    |    inside    |    inside    |   outside   |
| `circle`, `radius: 16` |   inside    |    inside    |   outside    |   outside   |

Use `/sbr set shape circle` to switch to a circular region and `/sbr set radius 24` to extend its radius to 24 blocks.

`radius: 0` protects only the centre column, one block wide.

### `region.min-y` and `region.max-y`

| Key     | Default | Accepts                          |
| ------- | ------- | -------------------------------- |
| `min-y` | `-64`   | A whole number, at most `max-y`  |
| `max-y` | `320`   | A whole number, at least `min-y` |

These are the lowest and highest protected block heights, with both included. The defaults span the full overworld height from bedrock to the sky.

To protect a spawn town while leaving the caves below it open for mining:

```yaml
region:
    min-y: 60
    max-y: 320
```

### `required-playtime`

| Default | Accepts                                                                |
| ------- | ---------------------------------------------------------------------- |
| `36h`   | A duration in `d` (days), `h` (hours), `m` (minutes) and `s` (seconds) |

This is how long a player must have played before they can build in the region.

You can combine units and include spaces, so `36h`, `1d12h`, `1d 12h` and `2160m` all mean the same duration. Every number needs a unit, and a bare `36` is refused.

| Value | Means                                                                          |
| ----- | ------------------------------------------------------------------------------ |
| `2d`  | Two days of play                                                               |
| `90m` | An hour and a half                                                             |
| `0s`  | No playtime requirement. Revocations and environmental protections still apply |

The plugin reads the game's `minecraft:play_time` statistic shown on the player's Statistics screen, so time played before installation counts. See [how-it-works.md](how-it-works.md#where-the-playtime-comes-from).

Player messages write durations in words, with the largest unit and the next smaller one when it is not zero: `14 hours and 31 minutes`, or `45 minutes and 10 seconds` for a wait below an hour. Durations below a week use hours rather than days. Admin output uses the short form, such as `14h 31m`.

## `build-permission`

| Default                  | Accepts           |
| ------------------------ | ----------------- |
| `spawnbuildrights.build` | A permission node |

Use this node to grant or revoke access through your permissions plugin.

| The node is | Effect                                                                                                                   |
| ----------- | ------------------------------------------------------------------------------------------------------------------------ |
| unset       | The stored grant or revocation applies. With neither, playtime decides                                                   |
| `true`      | Grants access regardless of playtime, unless `/sbr revoke` applies. Server operators hold the default node automatically |
| `false`     | Blocks building in the region regardless of playtime or a stored grant                                                   |

If you already use a node for trusted builders, enter its name:

```yaml
build-permission: myserver.trusted
```

You can make decisions without a permissions plugin through `/sbr grant` and `/sbr revoke`. For example, `/sbr grant <player> spawn builder` grants early access and records the reason, while `/sbr revoke <player> damaged spawn` blocks access regardless of playtime.

A permission set to `false` or a stored revocation beats any grant. See [how-it-works.md](how-it-works.md#who-can-build-at-spawn).

## `storage`

Where the plugin keeps grants, revocations and their log. The game keeps each player's playtime itself. [Storage](storage.md) covers each database, TLS and sharing one database between servers.

| Key                              | Default            | Purpose                                                                                           |
| -------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------- |
| `storage.type`                   | `sqlite`           | `sqlite`, `postgres`, or `mysql` for MySQL and MariaDB (`mariadb` also works)                     |
| `storage.sqlite.file`            | `data.db`          | The SQLite file, relative to `plugins/SpawnBuildRights/`                                          |
| `storage.postgres.host`          | `localhost`        | PostgreSQL server                                                                                 |
| `storage.postgres.port`          | `5432`             | PostgreSQL port                                                                                   |
| `storage.postgres.database`      | `spawnbuildrights` | Database name                                                                                     |
| `storage.postgres.user`          | `spawnbuildrights` | User to connect as                                                                                |
| `storage.postgres.password`      | `''`               | That user's password                                                                              |
| `storage.postgres.schema`        | `''`               | Schema for the tables, which must already exist. Blank uses the user's default, normally `public` |
| `storage.postgres.ssl-mode`      | `prefer`           | TLS: `disable`, `allow`, `prefer`, `require`, `verify-ca` or `verify-full`                        |
| `storage.postgres.ssl-root-cert` | `''`               | The CA certificate for `verify-ca` and `verify-full`                                              |
| `storage.mysql.host`             | `localhost`        | MySQL or MariaDB server                                                                           |
| `storage.mysql.port`             | `3306`             | Port                                                                                              |
| `storage.mysql.database`         | `spawnbuildrights` | Database name                                                                                     |
| `storage.mysql.user`             | `spawnbuildrights` | User to connect as                                                                                |
| `storage.mysql.password`         | `''`               | That user's password                                                                              |
| `storage.mysql.ssl-mode`         | `disable`          | TLS: `disable`, `trust`, `verify-ca` or `verify-full`                                             |
| `storage.mysql.ssl-ca-cert`      | `''`               | The CA certificate for `verify-ca` and `verify-full`. Blank uses Java's own trusted CAs           |

Only the section matching `storage.type` is used. For a network of servers sharing one MySQL database:

```yaml
storage:
    type: mysql
    mysql:
        host: db.internal
        port: 3306
        database: minecraft
        user: survival
        password: 'change-me'
```

## `warn-on-vanilla-spawn-protection` and `alert-operators-on-join`

| Key                                | Default | Effect                                                                                                                                                                                    |
| ---------------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `warn-on-vanilla-spawn-protection` | `true`  | Logs a startup warning when `spawn-protection` in `server.properties` is above 0                                                                                                          |
| `alert-operators-on-join`          | `true`  | Privately sends `operator-conflict-warning` to each joining server operator or `spawnbuildrights.admin` holder while the conflict exists. This works independently of the console warning |

Vanilla spawn protection takes precedence over the plugin, so players can meet the playtime requirement and still be blocked by it. Leave both warnings on and set `spawn-protection=0` in `server.properties`. See [deployment.md](deployment.md#vanilla-spawn-protection).

If you want only the private join warning:

```yaml
warn-on-vanilla-spawn-protection: false
alert-operators-on-join: true
```

Set `alert-operators-on-join: false` to turn off that warning too.

## `messages`

Write player messages in [MiniMessage](https://docs.advntr.dev/minimessage/) format, which supports colours, bold text and gradients.

| Key                         | When it appears                                                                    | Where                          |
| --------------------------- | ---------------------------------------------------------------------------------- | ------------------------------ |
| `build-allowed`             | A player who can build runs `/sbr`                                                 | Chat                           |
| `build-denied`              | A player below the playtime requirement tries to change the region, or runs `/sbr` | Action bar, or chat for `/sbr` |
| `build-revoked`             | A player with revoked rights tries to change the region, or runs `/sbr`            | Action bar, or chat for `/sbr` |
| `operator-conflict-warning` | A server operator or admin joins while vanilla spawn protection is on              | Private chat                   |

Use these placeholders in your messages:

| Placeholder   | Replaced with                                                     | Example                  |
| ------------- | ----------------------------------------------------------------- | ------------------------ |
| `%required%`  | `required-playtime`                                               | `12 hours`               |
| `%played%`    | The player's playtime                                             | `2 hours and 29 minutes` |
| `%remaining%` | Time still needed                                                 | `9 hours and 31 minutes` |
| `%radius%`    | Vanilla `spawn-protection` radius, in `operator-conflict-warning` | `16`                     |

The action bar replaces the previous line, so repeated attempts keep the message current without filling chat.

A blank message (`''`) sends nothing, while omitting a message key uses the default.

Each of the first three is the one sentence a player sees about their own build rights, so keep it short. For example, you can write the denial in your own words and silence the revocation message:

```yaml
messages:
    build-denied: '<gold>Play for another <yellow>%remaining%</yellow> to build at spawn</gold>'
    build-revoked: ''
```

The full default file above includes an `operator-conflict-warning` example using `%radius%`.

## `protection`

Every protection defaults to `true`, including when its key is missing. Set a switch to `false` and run `/sbr reload` to turn it off.

[protections.md](protections.md) shows each protection with a before and after. The examples below describe what happens with the switch on.

| Key                   | What it stops                                                                                                                                                                 | Example                                                                                            |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `explosions`          | Explosions breaking blocks inside, TNT chain reactions and explosion ignition                                                                                                 | TNT outside can leave a crater outside. Blocks inside survive                                      |
| `hostile-mob-changes` | Block changes by hostile mobs, including endermen, withers, the dragon, silverfish and door-breaking zombies. Also stops mob projectile block changes and projectile ignition | An enderman cannot take a block from the spawn build                                               |
| `display-damage`      | Environmental damage to item frames, paintings and armour stands. Player changes still require build rights                                                                   | A skeleton's arrow cannot break a display                                                          |
| `fire-damage`         | Blocks burning, fire spreading inside and ignition by lava or lightning                                                                                                       | A forest fire cannot spread onto the wooden roofs at spawn                                         |
| `falling-blocks`      | Falling blocks from outside landing as blocks inside                                                                                                                          | Sand, gravel or an anvil from outside drops as an item where it would land                         |
| `fluid-flow`          | Water or lava flowing in from outside, and dragon eggs teleporting in                                                                                                         | Lava poured beside the boundary stops at the edge                                                  |
| `pistons`             | Pistons outside moving blocks into, out of or within the region                                                                                                               | An outside piston cannot push a block into the spawn wall                                          |
| `dispensers`          | Dispensers outside placing anything inside, and their projectiles entering                                                                                                    | An outside dispenser cannot pour lava in, and its arrows disappear before they hit anything inside |
| `growth`              | Trees, huge mushrooms and bone meal used outside placing blocks inside                                                                                                        | A tree beside spawn grows without the leaves that would cross the boundary                         |
| `portal-arrivals`     | Selected dangerous entities arriving through a portal                                                                                                                         | A wither skull sent through the End's exit portal is removed if it would arrive in the region      |

The boundary protections leave changes that start inside alone, so a builder's water feature can flow normally and a piston inside the region can move. Other enabled protections still apply.

`portal-arrivals` accepts six individual switches or one value for all six:

```yaml
protection:
    portal-arrivals: false   # every kind passes through portals
```

| Key                              | What it defuses                                                                                                  | Example                                                                     |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `portal-arrivals.explosives`     | Primed TNT and TNT minecarts                                                                                     | Primed TNT disappears before reaching the region through a portal           |
| `portal-arrivals.wither-skulls`  | Black and blue wither skulls                                                                                     | A skull sent through the End's exit portal cannot arrive at protected spawn |
| `portal-arrivals.fireballs`      | Ghast, blaze and dragon fireballs                                                                                | A ghast fireball is removed before its portal arrival                       |
| `portal-arrivals.projectiles`    | Plain, spectral and tipped arrows, tridents, llama spit, shulker bullets and harmful splash or lingering potions | A poison potion is removed. A healing potion passes                         |
| `portal-arrivals.fireworks`      | Firework rockets carrying stars, unless attached to an entity                                                    | A star-bearing rocket is removed. A plain rocket passes                     |
| `portal-arrivals.falling-blocks` | Sand, gravel and other falling blocks                                                                            | Sand arrives as its item instead of a block                                 |

For example, set `portal-arrivals.projectiles` to `false` to let those projectiles through while leaving the other five switches on. See [protections.md](protections.md) for item-drop rules and [compatibility.md](compatibility.md) for Folia support.

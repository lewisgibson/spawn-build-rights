# Hangar listing

The project's text and settings. This file does not publish anything; the project page is filled in from it. See [setting up publishing](../releasing.md#setting-up-publishing).

Hangar accepts the same Markdown as the [Modrinth description](modrinth.md#description). Keep both descriptions identical, from the banner image to the final line. The raw image URLs load once the repository is public.

## Project settings

| Field     | Value                                                                                                                  |
| --------- | ---------------------------------------------------------------------------------------------------------------------- |
| Name      | SpawnBuildRights                                                                                                       |
| Summary   | Extends vanilla spawn protection so players earn the right to build at spawn by playing (87 of 120 characters)         |
| Category  | Protection                                                                                                             |
| Keywords  | `spawn`, `protection`, `playtime`, `build`, `grief`; Hangar allows 5                                                   |
| License   | GPL-3.0 from Hangar's license list                                                                                     |
| Platforms | Paper, set per version by the release workflow. Hangar includes Purpur and Folia under Paper; tick "Supports Folia"    |
| Source    | https://github.com/lewisgibson/spawn-build-rights                                                                      |
| Issues    | https://github.com/lewisgibson/spawn-build-rights/issues                                                               |
| Wiki      | https://github.com/lewisgibson/spawn-build-rights/tree/main/docs                                                       |
| Icon      | [`../images/icon.png`](../images/icon.png)                                                                             |
| Banner    | [`../images/banner.png`](../images/banner.png). Check Hangar's preview because it crops the image to its header shape. |

## Description

![SpawnBuildRights](https://raw.githubusercontent.com/lewisgibson/spawn-build-rights/main/docs/images/banner.png)

SpawnBuildRights builds on vanilla spawn protection. Vanilla stops everyone except operators building near spawn. With SpawnBuildRights everyone can use spawn from their first minute, and each player can build there once they have played for as long as you choose. The build is also protected from explosions, mobs, fire and anything that reaches in from outside, whoever is behind it.

![A new player trying to build at spawn is told how much longer they need to play](https://raw.githubusercontent.com/lewisgibson/spawn-build-rights/main/docs/images/denied.png)

## How it works

- Anyone can walk around spawn, trade, sleep, open doors and use chests and workstations from their first minute.
- Building there, from placing blocks to using buckets and changing item frames, opens up once a player has played for the time you set. Playtime is the game's own statistic, so time played before you install the plugin counts.
- A player who tries too early is told in one sentence how much longer they need, and `/sbr` tells them the same.
- Admins can grant build rights early with `/sbr grant`, take them away with `/sbr revoke`, and see who decided what and why with `/sbr history`.

## Protections

Inside the region SpawnBuildRights stops everything below, whoever is behind it. Each protection has its own switch, and every one is on by default.

| Stops                                                                                           | Switch                |
| ----------------------------------------------------------------------------------------------- | --------------------- |
| Creepers blowing up blocks                                                                      | `explosions`          |
| TNT and TNT minecarts blowing up blocks                                                         | `explosions`          |
| End crystals blowing up blocks                                                                  | `explosions`          |
| Beds and respawn anchors exploding in the wrong dimension                                       | `explosions`          |
| The wither and its skulls blowing up blocks                                                     | `explosions`          |
| Ghast fireballs blowing up blocks                                                               | `explosions`          |
| Explosions lighting fires                                                                       | `explosions`          |
| An explosion outside setting off TNT inside                                                     | `explosions`          |
| Endermen picking up or placing blocks                                                           | `hostile-mob-changes` |
| Zombies and vindicators breaking doors                                                          | `hostile-mob-changes` |
| Silverfish hiding in stone or bursting out of it                                                | `hostile-mob-changes` |
| The wither and the ender dragon breaking blocks they touch                                      | `hostile-mob-changes` |
| Hostile mobs trampling farmland or turtle eggs                                                  | `hostile-mob-changes` |
| Hostile mobs' arrows breaking decorated pots, chorus flowers or pointed dripstone               | `hostile-mob-changes` |
| Blaze and ghast fireballs lighting fires                                                        | `hostile-mob-changes` |
| Flaming arrows lighting fires or setting off TNT, a player's included                           | `hostile-mob-changes` |
| Item frames, paintings and armour stands broken by mobs                                         | `display-damage`      |
| Item frames, paintings and armour stands broken by explosions and fireworks                     | `display-damage`      |
| Item frames, paintings and armour stands broken by fire, lava or lightning                      | `display-damage`      |
| Item frames, paintings and armour stands broken by arrows or potions with no player behind them | `display-damage`      |
| Item frames, paintings and armour stands broken by falling anvils or dripstone                  | `display-damage`      |
| Blocks burning away                                                                             | `fire-damage`         |
| Fire spreading in                                                                               | `fire-damage`         |
| Lava lighting fires                                                                             | `fire-damage`         |
| Lightning lighting fires                                                                        | `fire-damage`         |
| Fire setting off TNT                                                                            | `fire-damage`         |
| Sand, gravel, concrete powder, anvils and other falling blocks from outside landing inside      | `falling-blocks`      |
| Water flowing in from outside                                                                   | `fluid-flow`          |
| Lava flowing in from outside                                                                    | `fluid-flow`          |
| A dragon egg teleporting in from outside                                                        | `fluid-flow`          |
| Pistons outside pushing blocks in or extending into the region                                  | `pistons`             |
| Pistons outside moving or breaking blocks inside                                                | `pistons`             |
| Sticky pistons outside pulling blocks out                                                       | `pistons`             |
| Dispensers outside pouring water in                                                             | `dispensers`          |
| Dispensers outside pouring lava in                                                              | `dispensers`          |
| Dispensers outside placing fire, TNT, blocks or bone meal inside                                | `dispensers`          |
| Arrows, fire charges, snowballs, eggs, potions and fireworks fired in by dispensers outside     | `dispensers`          |
| Trees and huge mushrooms grown outside spreading in                                             | `growth`              |
| Bone meal used outside spreading grass and flowers in                                           | `growth`              |
| TNT and TNT minecarts arriving through a portal                                                 | `portal-arrivals`     |
| Wither skulls arriving through a portal                                                         | `portal-arrivals`     |
| Ghast, blaze and dragon fireballs arriving through a portal                                     | `portal-arrivals`     |
| Arrows, tridents, llama spit, shulker bullets and harmful potions arriving through a portal     | `portal-arrivals`     |
| Firework rockets with stars arriving through a portal                                           | `portal-arrivals`     |
| Sand and other falling blocks arriving through a portal                                         | `portal-arrivals`     |
| Water or lava poured from a bucket by a player without build rights                             | Build rights          |
| Fire or TNT lit by a player without build rights                                                | Build rights          |

![TNT set off beside the region: with explosions off the crater eats into the floor, with it on the crater stops at the edge](https://raw.githubusercontent.com/lewisgibson/spawn-build-rights/main/docs/images/protection-explosions.png)

## Requirements

- Paper, Purpur or Folia 26.3, and Java 25. On Folia every protection works except portal arrivals.

## Install

Put the jar in your server's `plugins` folder, set `spawn-protection=0` in `server.properties` and restart. Grants and revocations are kept in SQLite with nothing to set up, or in PostgreSQL, MySQL or MariaDB.

The [documentation](https://github.com/lewisgibson/spawn-build-rights/tree/main/docs) covers every setting, command and protection, with a before and after picture for each switch.

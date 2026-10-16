/**
 * SpawnBuildRights - playtime-gated build protection for a Minecraft spawn region.
 *
 * <p>Players may use the spawn build (doors, buttons, chests) immediately, but may only change it
 * (break or place blocks, edit signs, place liquids or entities, light fires) once they have
 * accumulated a configurable amount of playtime, or hold the build permission by hand.
 *
 * <p>The code is split into three layers:
 *
 * <ul>
 *   <li>the {@code core} packages ({@code region}, {@code access}, {@code time}, {@code conflict},
 *       {@code message}, {@code settings}, {@code grant}) - the pure, platform-free decision logic,
 *       unit-tested in isolation and held to full line and branch coverage;
 *   <li>the {@code storage} package - the JDBC store and its writer thread, behind the {@code
 *       grant} package's store interface; and
 *   <li>the {@link com.lewisgibson.spawnbuildrights.bukkit bukkit} packages - a thin adapter
 *       binding that logic to the Bukkit/Paper API (events, configuration, commands, messaging).
 * </ul>
 *
 * <p>Keeping the domain free of any server API is what makes the logic fully testable without a
 * mocked server and would let a future Fabric or NeoForge adapter reuse it unchanged.
 */
package com.lewisgibson.spawnbuildrights;

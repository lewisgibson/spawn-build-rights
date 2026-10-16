package com.lewisgibson.spawnbuildrights.core.conflict;

/**
 * Detects the conflict between this plugin and vanilla Minecraft's own {@code spawn-protection}.
 *
 * <p>Vanilla spawn-protection blocks all non-operator block editing within a radius of world spawn,
 * with no per-player nuance and no way for a plugin to grant exceptions. Left enabled alongside
 * SpawnBuildRights it would still block players who have earned build access, so the two must not
 * run together. This tiny rule is centralised here so it can be unit-tested and so the plugin can
 * warn operators loudly and consistently.
 */
public final class SpawnProtectionConflict {

    private SpawnProtectionConflict() {}

    /**
     * @param vanillaSpawnRadius the server's {@code spawn-protection} value (e.g. from {@code
     *     Bukkit.getServer().getSpawnRadius()})
     * @return {@code true} if vanilla spawn-protection is active and will conflict with this plugin
     */
    public static boolean conflicts(int vanillaSpawnRadius) {
        return vanillaSpawnRadius > 0;
    }
}

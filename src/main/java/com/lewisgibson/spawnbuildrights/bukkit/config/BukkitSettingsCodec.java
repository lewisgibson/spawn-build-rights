package com.lewisgibson.spawnbuildrights.bukkit.config;

import com.lewisgibson.spawnbuildrights.core.message.Messages;
import com.lewisgibson.spawnbuildrights.core.region.RegionShape;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import com.lewisgibson.spawnbuildrights.core.settings.EnvironmentalProtection;
import com.lewisgibson.spawnbuildrights.core.settings.PortalArrivals;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;
import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import com.lewisgibson.spawnbuildrights.core.settings.StorageType;
import com.lewisgibson.spawnbuildrights.core.time.PlaytimeDuration;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Translates between the on-disk {@link FileConfiguration} and the immutable {@link
 * SpawnBuildRightsSettings} domain model, and writes individual settings back for the in-game
 * {@code /sbr set} commands.
 */
public final class BukkitSettingsCodec {

    /**
     * Reads settings from {@code config}. The region is anchored to {@code resolvedWorld} (the
     * world the plugin already resolved from {@code region.world}, falling back to the default
     * world), and its centre falls back to the supplied spawn coordinates when not explicitly
     * configured.
     *
     * <p>The world name is taken solely from {@code resolvedWorld}, never re-read from config here:
     * a misconfigured {@code region.world} that names no loaded world must anchor the region to the
     * world the plugin actually fell back to, not to the nonexistent name (which would silently
     * protect nothing).
     *
     * @param config the plugin configuration
     * @param resolvedWorld the name of the world the region is anchored to
     * @param defaultCenterX the centre X to use when {@code region.center-x} is unset
     * @param defaultCenterZ the centre Z to use when {@code region.center-z} is unset
     * @return the resolved settings
     * @throws IllegalArgumentException if a configured value is invalid (bad shape, radius, Y
     *     bounds, or playtime), or a number or switch holds a value of the wrong kind, so the
     *     plugin surfaces the error instead of loading a broken region or silently using a default
     */
    public SpawnBuildRightsSettings load(
            FileConfiguration config,
            String resolvedWorld,
            int defaultCenterX,
            int defaultCenterZ) {
        Integer configuredX = optionalInteger(config, "region.center-x");
        Integer configuredZ = optionalInteger(config, "region.center-z");
        if ((configuredX == null) != (configuredZ == null)) {
            throw new IllegalArgumentException(
                    "region.center-x and region.center-z must both be set, or both left blank to"
                            + " follow the world spawn");
        }
        int centerX = configuredX == null ? defaultCenterX : configuredX;
        int centerZ = configuredZ == null ? defaultCenterZ : configuredZ;

        RegionShape shape = RegionShape.fromToken(config.getString("region.shape", "square"));
        int radius = integer(config, "region.radius", 16);
        int minY = integer(config, "region.min-y", -64);
        int maxY = integer(config, "region.max-y", 320);
        SpawnRegion region =
                new SpawnRegion(resolvedWorld, centerX, centerZ, radius, shape, minY, maxY);

        long requiredPlaytime =
                PlaytimeDuration.parseTicks(config.getString("required-playtime", "36h"));
        String buildPermission = config.getString("build-permission", "spawnbuildrights.build");
        boolean warn = bool(config, "warn-on-vanilla-spawn-protection", true);
        boolean alert = bool(config, "alert-operators-on-join", true);

        // No explicit fallback here, on purpose: with one, Bukkit never consults the defaults it
        // loaded from the jar's config.yml, so a config file that simply omits `messages` would
        // silently send nothing. Without one, a missing key means the shipped line, and only a
        // key deliberately set blank means silence.
        Messages messages =
                new Messages(
                        message(config, "messages.build-allowed"),
                        message(config, "messages.build-denied"),
                        message(config, "messages.build-revoked"),
                        message(config, "messages.operator-conflict-warning"));

        StorageSettings defaults = StorageSettings.sqliteDefault();
        StorageSettings storage =
                new StorageSettings(
                        StorageType.fromToken(
                                config.getString("storage.type", defaults.type().token())),
                        config.getString("storage.sqlite.file", defaults.sqliteFile()),
                        postgres(config, defaults.postgres()),
                        mysql(config, defaults.mysql()));

        EnvironmentalProtection protection = EnvironmentalProtection.defaults();
        return new SpawnBuildRightsSettings(
                region,
                requiredPlaytime,
                buildPermission,
                warn,
                alert,
                messages,
                storage,
                new EnvironmentalProtection(
                        bool(config, "protection.explosions", protection.explosions()),
                        bool(
                                config,
                                "protection.hostile-mob-changes",
                                protection.hostileMobChanges()),
                        bool(config, "protection.display-damage", protection.displayDamage()),
                        bool(config, "protection.fire-damage", protection.fireDamage()),
                        bool(config, "protection.falling-blocks", protection.fallingBlocks()),
                        bool(config, "protection.fluid-flow", protection.fluidFlow()),
                        bool(config, "protection.pistons", protection.pistons()),
                        bool(config, "protection.dispensers", protection.dispensers()),
                        bool(config, "protection.growth", protection.growth()),
                        portalArrivals(config, protection.portalArrivals())));
    }

    private static StorageSettings.Postgres postgres(
            FileConfiguration config, StorageSettings.Postgres defaults) {
        String path = "storage.postgres.";
        return new StorageSettings.Postgres(
                config.getString(path + "host", defaults.host()),
                integer(config, path + "port", defaults.port()),
                config.getString(path + "database", defaults.database()),
                config.getString(path + "user", defaults.user()),
                config.getString(path + "password", defaults.password()),
                config.getString(path + "schema", defaults.schema()),
                config.getString(path + "ssl-mode", defaults.sslMode()),
                config.getString(path + "ssl-root-cert", defaults.sslRootCert()));
    }

    private static StorageSettings.MySql mysql(
            FileConfiguration config, StorageSettings.MySql defaults) {
        String path = "storage.mysql.";
        return new StorageSettings.MySql(
                config.getString(path + "host", defaults.host()),
                integer(config, path + "port", defaults.port()),
                config.getString(path + "database", defaults.database()),
                config.getString(path + "user", defaults.user()),
                config.getString(path + "password", defaults.password()),
                config.getString(path + "ssl-mode", defaults.sslMode()),
                config.getString(path + "ssl-ca-cert", defaults.sslCaCert()));
    }

    /**
     * Reads {@code protection.portal-arrivals}: a section with one switch per kind, each missing
     * switch falling back to its default, or a single true or false that sets every kind at once.
     * Anything else fails rather than quietly leaving every kind at its default.
     */
    private static PortalArrivals portalArrivals(
            FileConfiguration config, PortalArrivals defaults) {
        String path = "protection.portal-arrivals";
        if (config.isBoolean(path)) {
            return PortalArrivals.all(config.getBoolean(path));
        }
        if (config.contains(path, true) && !config.isConfigurationSection(path)) {
            throw new IllegalArgumentException(
                    path
                            + " must be true, false or a section of switches, was '"
                            + config.get(path)
                            + "'");
        }
        return new PortalArrivals(
                bool(config, path + ".explosives", defaults.explosives()),
                bool(config, path + ".wither-skulls", defaults.witherSkulls()),
                bool(config, path + ".fireballs", defaults.fireballs()),
                bool(config, path + ".projectiles", defaults.projectiles()),
                bool(config, path + ".fireworks", defaults.fireworks()),
                bool(config, path + ".falling-blocks", defaults.fallingBlocks()));
    }

    /**
     * Whether the region centre is left blank, so it follows the world spawn rather than staying
     * where it was configured.
     *
     * @param config the plugin configuration, already loaded successfully by {@link #load}
     * @return {@code true} when neither centre coordinate is set
     */
    public boolean centreFollowsSpawn(FileConfiguration config) {
        return optionalInteger(config, "region.center-x") == null;
    }

    /**
     * Lists the keys in {@code config} that the shipped config.yml does not have, so a misspelt key
     * (which Bukkit would silently ignore, leaving the default in force) can be reported.
     *
     * @param config the plugin configuration, with the shipped file as its defaults
     * @return every unknown key, as a dotted path, in file order; empty when there are no defaults
     *     to compare against
     */
    public List<String> unknownKeys(FileConfiguration config) {
        Configuration known = config.getDefaults();
        if (known == null) {
            return List.of();
        }
        List<String> unknown = new ArrayList<>();
        for (String key : config.getKeys(true)) {
            if (!known.contains(key, false) && !underUnknown(unknown, key)) {
                unknown.add(key);
            }
        }
        return unknown;
    }

    /** A misspelt section is reported once, not once more for every key inside it. */
    private static boolean underUnknown(List<String> unknown, String key) {
        for (String parent : unknown) {
            if (key.startsWith(parent + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a whole number. Bukkit's own {@code getInt} silently returns the default for a value
     * that is not a number, so a typo such as {@code radius: 16a} would quietly protect a radius of
     * 16; here it fails, naming the key.
     */
    private static int integer(FileConfiguration config, String path, int fallback) {
        Integer value = optionalInteger(config, path);
        return value == null ? fallback : value;
    }

    /**
     * Reads a whole number that may be left out or blank, which is what {@code region.center-x}
     * does to follow the world spawn.
     *
     * @return the number, or {@code null} when the key is missing or blank
     * @throws IllegalArgumentException if the key holds anything else
     */
    private static Integer optionalInteger(FileConfiguration config, String path) {
        Object raw = config.get(path, null);
        if (raw == null || (raw instanceof String text && text.isBlank())) {
            return null;
        }
        if (raw instanceof Integer number) {
            return number;
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException notANumber) {
                // reported below with the key's name
            }
        }
        throw new IllegalArgumentException(path + " must be a whole number, was '" + raw + "'");
    }

    /**
     * Reads a true or false. Bukkit's own {@code getBoolean} silently returns the default for
     * anything else, so {@code explosions: flase} would leave the protection on without a word;
     * here it fails, naming the key.
     */
    private static boolean bool(FileConfiguration config, String path, boolean fallback) {
        Object raw = config.get(path, null);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException(path + " must be true or false, was '" + raw + "'");
    }

    private static String message(FileConfiguration config, String path) {
        String value = config.getString(path);
        return value == null ? "" : value;
    }

    /**
     * Writes the region shape back to {@code config} (not saved to disk until the caller saves).
     */
    public void writeShape(FileConfiguration config, RegionShape shape) {
        config.set("region.shape", shape.token());
    }

    /** Writes the region radius back to {@code config}. */
    public void writeRadius(FileConfiguration config, int radius) {
        config.set("region.radius", radius);
    }

    /** Writes an explicit region centre back to {@code config}. */
    public void writeCenter(FileConfiguration config, int centerX, int centerZ) {
        config.set("region.center-x", centerX);
        config.set("region.center-z", centerZ);
    }

    /** Writes the required playtime back to {@code config} in the compact human form. */
    public void writeRequiredPlaytime(FileConfiguration config, long ticks) {
        config.set("required-playtime", PlaytimeDuration.format(ticks));
    }
}

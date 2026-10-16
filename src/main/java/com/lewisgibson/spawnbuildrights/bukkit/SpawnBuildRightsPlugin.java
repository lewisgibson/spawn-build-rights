package com.lewisgibson.spawnbuildrights.bukkit;

import com.lewisgibson.spawnbuildrights.bukkit.command.SpawnBuildRightsCommand;
import com.lewisgibson.spawnbuildrights.bukkit.config.BukkitSettingsCodec;
import com.lewisgibson.spawnbuildrights.bukkit.listener.BuildProtectionListener;
import com.lewisgibson.spawnbuildrights.bukkit.listener.EnvironmentalProtectionListener;
import com.lewisgibson.spawnbuildrights.bukkit.listener.OperatorAlertListener;
import com.lewisgibson.spawnbuildrights.bukkit.listener.PlaytimeEarnedListener;
import com.lewisgibson.spawnbuildrights.bukkit.listener.SpawnFollowListener;
import com.lewisgibson.spawnbuildrights.bukkit.message.BukkitMessenger;
import com.lewisgibson.spawnbuildrights.core.conflict.SpawnProtectionConflict;
import com.lewisgibson.spawnbuildrights.core.grant.GrantRegistry;
import com.lewisgibson.spawnbuildrights.core.grant.GrantService;
import com.lewisgibson.spawnbuildrights.core.grant.GrantStore;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;
import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import com.lewisgibson.spawnbuildrights.storage.GrantStores;
import com.lewisgibson.spawnbuildrights.storage.StorageWorker;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point: loads configuration into the pure domain model, opens the store, registers
 * the listeners and the command, and warns about a vanilla spawn-protection conflict.
 */
public class SpawnBuildRightsPlugin extends JavaPlugin {

    /** The permission that guards every admin subcommand and the operator join alert. */
    public static final String ADMIN_PERMISSION = "spawnbuildrights.admin";

    // How long a stop or a store switch waits for queued writes. Not final only so a test can
    // shorten it to observe the timeout without waiting ten seconds.
    private volatile Duration storageTimeout = Duration.ofSeconds(10);

    private final BukkitMessenger messenger = new BukkitMessenger();
    private final BukkitSettingsCodec codec = new BukkitSettingsCodec();
    private StorageWorker worker;
    // store and storeSettings are only ever read or written under the plugin's monitor: on Folia a
    // console reload (global region) and a player-issued one (that player's region) run at once.
    private GrantStore store;
    private StorageSettings storeSettings;
    private volatile PluginRuntime runtime;
    // Whether the loaded config leaves the centre blank; read and written under the monitor.
    private boolean centreFollowsSpawn;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worker =
                new StorageWorker(
                        failure -> getLogger().log(Level.SEVERE, "A store write failed", failure));
        try {
            reloadSettings();
        } catch (RuntimeException ex) {
            getLogger().severe("Invalid configuration or unusable store: " + ex.getMessage());
            getLogger().severe("Disabling SpawnBuildRights; fix config.yml and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BuildProtectionListener(this), this);
        getServer()
                .getPluginManager()
                .registerEvents(new EnvironmentalProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlaytimeEarnedListener(this), this);
        getServer().getPluginManager().registerEvents(new OperatorAlertListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnFollowListener(this), this);

        PluginCommand command =
                Objects.requireNonNull(
                        getCommand("spawnbuildrights"), "plugin.yml declares spawnbuildrights");
        SpawnBuildRightsCommand handler = new SpawnBuildRightsCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        warnIfVanillaSpawnProtection();
    }

    @Override
    public void onDisable() {
        Duration timeout = storageTimeout;
        if (!worker.drain(timeout)) {
            getLogger().warning("Store writes were still pending after " + timeout);
        }
        closeStore();
    }

    private synchronized void closeStore() {
        if (store != null) {
            store.close();
            store = null;
        }
    }

    /**
     * Reloads config.yml from disk and atomically rebuilds the runtime state. The store is reopened
     * only when its settings changed, but its registry is read again every time, after the writes
     * already queued have landed, so a decision another server made in a shared database takes
     * effect here at the next reload.
     *
     * <p>Synchronised, because on Folia the console (global region) and any number of players (each
     * on their own region thread) can run a reload at the same moment, and the config, the store
     * and the runtime swap must not interleave.
     *
     * @throws IllegalArgumentException if the configuration is invalid
     * @throws com.lewisgibson.spawnbuildrights.core.grant.StorageException if the store cannot be
     *     opened, so the caller (startup or the reload command) can report it rather than run with
     *     no memory of who was granted or revoked
     */
    public synchronized void reloadSettings() {
        reloadConfig();
        World world = resolveRegionWorld();
        Location spawn = world.getSpawnLocation();
        SpawnBuildRightsSettings settings =
                codec.load(getConfig(), world.getName(), spawn.getBlockX(), spawn.getBlockZ());
        boolean followsSpawn = codec.centreFollowsSpawn(getConfig());
        for (String key : codec.unknownKeys(getConfig())) {
            getLogger()
                    .warning(
                            "config.yml has a key this plugin does not know, '"
                                    + key
                                    + "'; it is ignored. Check its spelling.");
        }

        boolean switching = store == null || !settings.storage().equals(storeSettings);
        GrantStore target =
                switching ? GrantStores.open(settings.storage(), getDataFolder().toPath()) : store;
        Supplier<GrantService> next =
                () -> {
                    // Every write queued so far lands first, so the registry read from the store
                    // includes the decisions this server made a moment ago.
                    Duration timeout = storageTimeout;
                    if (!worker.flush(timeout)) {
                        getLogger().warning("Store writes were still pending after " + timeout);
                    }
                    return new GrantService(
                            GrantRegistry.load(target), target, worker::submit, Clock.systemUTC());
                };
        GrantService grants;
        try {
            // The registry is read and the old service handed over in one step, under the old
            // service's lock, so a decision a region thread makes through the old service either
            // finishes first (and is flushed above) or is passed on to the new one: none is made
            // against a registry nobody reads again. The registry is read on every reload, even
            // from the same store, so decisions another server made in a shared PostgreSQL
            // database take effect here at the reload rather than at the next restart.
            grants = runtime == null ? next.get() : runtime.grants().handOver(next);
        } catch (RuntimeException ex) {
            if (switching) {
                target.close();
            }
            throw ex;
        }
        if (switching) {
            if (store != null) {
                store.close();
            }
            store = target;
            storeSettings = settings.storage();
            getLogger()
                    .info(
                            "Store: "
                                    + GrantStores.describe(
                                            settings.storage(), getDataFolder().toPath())
                                    + " ("
                                    + grants.registry().overrideCount()
                                    + " overrides)");
        }
        this.runtime = PluginRuntime.from(settings, grants);
        this.centreFollowsSpawn = followsSpawn;
    }

    /**
     * Moves the region to a world's new spawn, when the region is in that world and its centre is
     * left blank to follow the spawn: {@code /setworldspawn} takes effect at once instead of at the
     * next reload. Only the region changes; the store and the registry are kept. Synchronised with
     * {@link #reloadSettings()} so the two never interleave.
     *
     * @param world the world whose spawn moved
     */
    public synchronized void followWorldSpawn(World world) {
        PluginRuntime current = runtime;
        SpawnRegion region = current.settings().region();
        if (!centreFollowsSpawn || !world.getName().equals(region.world())) {
            return;
        }
        Location spawn = world.getSpawnLocation();
        if (spawn.getBlockX() == region.centerX() && spawn.getBlockZ() == region.centerZ()) {
            return;
        }
        runtime =
                new PluginRuntime(
                        current.settings()
                                .withRegion(region.centredAt(spawn.getBlockX(), spawn.getBlockZ())),
                        current.policy(),
                        current.grants());
        getLogger()
                .info(
                        "The world spawn moved; the region is now centred on "
                                + spawn.getBlockX()
                                + ", "
                                + spawn.getBlockZ()
                                + " in "
                                + world.getName()
                                + ".");
    }

    /**
     * Applies an edit to config.yml, saves it and reloads, as one step under the same lock as
     * {@link #reloadSettings()}. The Bukkit {@code FileConfiguration} is not thread-safe, so every
     * write to it goes through here rather than through {@link #getConfig()} on the caller's
     * thread.
     *
     * @param edit the change to make to the configuration
     * @throws IllegalArgumentException if the edited configuration is invalid
     * @throws com.lewisgibson.spawnbuildrights.core.grant.StorageException if the store cannot be
     *     opened
     */
    public synchronized void editConfig(Consumer<FileConfiguration> edit) {
        edit.accept(getConfig());
        saveConfig();
        reloadSettings();
    }

    private World resolveRegionWorld() {
        String configured = getConfig().getString("region.world", "");
        if (!configured.isBlank()) {
            World world = getServer().getWorld(configured);
            if (world != null) {
                return world;
            }
            getLogger()
                    .warning(
                            "Configured region.world '"
                                    + configured
                                    + "' not found; using the default world.");
        }
        return getServer().getWorlds().get(0);
    }

    private void warnIfVanillaSpawnProtection() {
        if (!runtime.settings().warnOnVanillaSpawnProtection()) {
            return;
        }
        int radius = getServer().getSpawnRadius();
        if (!SpawnProtectionConflict.conflicts(radius)) {
            return;
        }
        String line = "=".repeat(64);
        getLogger().warning(line);
        getLogger().warning("Vanilla spawn-protection is ENABLED (radius " + radius + ").");
        getLogger()
                .warning(
                        "It overrides SpawnBuildRights and blocks players who have earned build access.");
        getLogger().warning("Set 'spawn-protection=0' in server.properties and restart to fix.");
        getLogger().warning(line);
    }

    /**
     * @return the current runtime snapshot (settings, policy and grants), read atomically
     */
    public PluginRuntime runtime() {
        return runtime;
    }

    /**
     * @return the shared messenger for rendering and sending player messages
     */
    public BukkitMessenger messenger() {
        return messenger;
    }

    /**
     * @return the settings codec, used by the {@code /sbr set} commands to write back config
     */
    public BukkitSettingsCodec codec() {
        return codec;
    }

    /**
     * Waits for every store write queued so far to finish, so a read that follows sees them: {@code
     * /sbr history} straight after {@code /sbr grant} must show the grant.
     *
     * @param timeout how long to wait
     * @return whether the queue emptied in time
     */
    public boolean awaitStoreWrites(Duration timeout) {
        return worker.flush(timeout);
    }

    /** Queues work on the storage thread, behind every store write already queued. */
    void queueStoreWrite(Runnable write) {
        worker.submit(write);
    }

    /** Sets how long a stop or a store switch waits for queued writes. */
    void storageTimeout(Duration timeout) {
        this.storageTimeout = Objects.requireNonNull(timeout, "timeout");
    }
}

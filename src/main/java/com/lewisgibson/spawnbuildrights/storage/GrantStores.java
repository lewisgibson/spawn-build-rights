package com.lewisgibson.spawnbuildrights.storage;

import com.lewisgibson.spawnbuildrights.core.grant.GrantStore;
import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import java.nio.file.Path;
import java.util.Objects;

/** Builds and opens the store the settings name. */
public final class GrantStores {

    private GrantStores() {}

    /**
     * @param settings which backend and where
     * @param dataFolder the plugin's data folder, which the SQLite file lives under
     * @return an open store with its tables up to date
     * @throws com.lewisgibson.spawnbuildrights.core.grant.StorageException if it cannot be opened;
     *     nothing is left open behind it
     */
    public static GrantStore open(StorageSettings settings, Path dataFolder) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        JdbcGrantStore store = new JdbcGrantStore(source(settings, dataFolder));
        try {
            store.open();
        } catch (RuntimeException ex) {
            store.close();
            throw ex;
        }
        return store;
    }

    /**
     * @param settings which backend and where
     * @param dataFolder the plugin's data folder
     * @return the description the store logs, without connecting to anything
     */
    public static String describe(StorageSettings settings, Path dataFolder) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        return switch (settings.type()) {
            case SQLITE -> sqlite(settings, dataFolder).description();
            case POSTGRES -> DataSources.describe(settings.postgres());
            case MYSQL -> DataSources.describe(settings.mysql());
        };
    }

    private static ConnectionSource source(StorageSettings settings, Path dataFolder) {
        return switch (settings.type()) {
            case SQLITE -> sqlite(settings, dataFolder);
            case POSTGRES ->
                    PooledConnectionSource.start(
                            Dialect.POSTGRES,
                            DataSources.describe(settings.postgres()),
                            DataSources.postgres(settings.postgres()));
            case MYSQL ->
                    PooledConnectionSource.start(
                            Dialect.MYSQL,
                            DataSources.describe(settings.mysql()),
                            DataSources.mysql(settings.mysql()));
        };
    }

    private static SqliteConnectionSource sqlite(StorageSettings settings, Path dataFolder) {
        return new SqliteConnectionSource(dataFolder.resolve(settings.sqliteFile()));
    }
}

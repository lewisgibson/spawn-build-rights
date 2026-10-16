package com.lewisgibson.spawnbuildrights.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * The default store: one SQLite file, created on first use, needing no configuration.
 *
 * <p>Each operation opens the file and closes it again. That costs well under a millisecond, the
 * store is written a handful of times a day, and nothing is left open for a backup to copy half-way
 * through. A writer waits up to five seconds for a lock held by another program reading the file
 * instead of failing at once.
 *
 * <p>The driver is the one the server ships (Paper bundles sqlite-jdbc), created directly rather
 * than looked up through {@code DriverManager}, which does not see drivers loaded for a plugin.
 */
public final class SqliteConnectionSource implements ConnectionSource {

    private static final String BUSY_TIMEOUT_MILLIS = "5000";

    private final Path file;

    /**
     * @param file the database file; its parent directory is created if missing
     */
    public SqliteConnectionSource(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
    }

    @Override
    public Connection open() throws SQLException {
        Path parent = file.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new SQLException("cannot create " + parent, ex);
        }
        Properties properties = new Properties();
        properties.setProperty("busy_timeout", BUSY_TIMEOUT_MILLIS);
        return new org.sqlite.JDBC().connect("jdbc:sqlite:" + file, properties);
    }

    @Override
    public Dialect dialect() {
        return Dialect.SQLITE;
    }

    @Override
    public String description() {
        return "sqlite " + file;
    }

    /** Nothing is held open between operations. */
    @Override
    public void close() {}
}

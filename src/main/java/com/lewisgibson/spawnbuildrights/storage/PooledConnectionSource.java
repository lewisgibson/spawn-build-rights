package com.lewisgibson.spawnbuildrights.storage;

import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * A database server reached through a small HikariCP pool. The store needs very few connections
 * (writes come from one thread, reads happen at startup), so the pool is there for what it does
 * besides pooling: it checks a connection before handing it out, replaces one the server or a
 * firewall dropped, and keeps an idle one alive.
 */
public final class PooledConnectionSource implements ConnectionSource {

    /** One for the storage thread, one for a reload reading a store while a write runs. */
    static final int MAXIMUM_POOL_SIZE = 2;

    /** Well under MySQL's default eight-hour {@code wait_timeout}. */
    static final Duration MAX_LIFETIME = Duration.ofMinutes(30);

    /** Often enough to keep a NAT or firewall from dropping an idle connection. */
    static final Duration KEEPALIVE = Duration.ofMinutes(5);

    /** How long startup, a reload or a write waits for a connection before failing. */
    static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    private final Dialect dialect;
    private final String description;
    private final HikariDataSource pool;

    private PooledConnectionSource(Dialect dialect, String description, HikariDataSource pool) {
        this.dialect = dialect;
        this.description = description;
        this.pool = pool;
    }

    /**
     * Starts the pool and makes its first connection, so an unreachable database or a wrong
     * password fails here.
     *
     * @param dialect the database's dialect
     * @param description where it connects to, without the password
     * @param dataSource where the pool's connections come from
     * @return the started source
     * @throws StorageException if the first connection cannot be made
     */
    public static PooledConnectionSource start(
            Dialect dialect, String description, DataSource dataSource) {
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(dataSource, "dataSource");
        HikariConfig config = new HikariConfig();
        config.setPoolName("SpawnBuildRights");
        config.setDataSource(dataSource);
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setMaxLifetime(MAX_LIFETIME.toMillis());
        config.setKeepaliveTime(KEEPALIVE.toMillis());
        config.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
        try {
            return new PooledConnectionSource(dialect, description, new HikariDataSource(config));
        } catch (RuntimeException ex) {
            throw new StorageException(
                    "could not connect (" + description + "): " + rootMessage(ex), ex);
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    @Override
    public Connection open() throws SQLException {
        return pool.getConnection();
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void close() {
        pool.close();
    }
}

package com.lewisgibson.spawnbuildrights.storage;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import com.lewisgibson.spawnbuildrights.core.grant.GrantOverride;
import com.lewisgibson.spawnbuildrights.core.grant.GrantStore;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The one store implementation. Its SQL is the same on every database except where {@link Dialect}
 * says otherwise, and its tables are built and upgraded by {@link Schema}.
 *
 * <p>Two tables: {@code sbr_overrides}, the current by-hand decision per player, and {@code
 * sbr_events}, the append-only audit log, whose generated {@code id} orders events written in the
 * same millisecond. A decision and its audit row are written in one transaction, so neither is ever
 * stored without the other.
 */
public final class JdbcGrantStore implements GrantStore {

    private static final String EVENT_COLUMNS =
            "player_uuid, player_name, kind, actor, reason, playtime_ticks, at_millis";

    private final ConnectionSource source;
    private volatile boolean closed;

    /**
     * @param source where to connect; nothing is opened until {@link #open()}, and {@link #close()}
     *     closes it
     */
    public JdbcGrantStore(ConnectionSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** One unit of work on a connection, run inside a transaction by {@link #inTransaction}. */
    @FunctionalInterface
    private interface Work {
        void run(Connection connection) throws SQLException;
    }

    /** Brings the tables up to date (see {@link Schema}). Opening again changes nothing. */
    @Override
    public void open() {
        try (Connection connection = connection()) {
            Schema.migrate(connection, source.dialect(), source.description());
        } catch (SQLException ex) {
            throw failure("open", ex);
        }
    }

    /**
     * Runs the work in one transaction: committed if it completes, rolled back if it throws, and a
     * {@link SQLException} reported as a {@link StorageException} naming the operation.
     */
    private void inTransaction(String operation, Work work) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure(operation, ex);
        }
    }

    @Override
    public Map<UUID, GrantOverride> loadOverrides() {
        Map<UUID, GrantOverride> overrides = new HashMap<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT player_uuid, player_name, state, actor, reason, at_millis"
                                        + " FROM sbr_overrides")) {
            while (rows.next()) {
                UUID player = UUID.fromString(rows.getString(1));
                overrides.put(
                        player,
                        new GrantOverride(
                                player,
                                rows.getString(2),
                                OverrideState.valueOf(rows.getString(3)),
                                rows.getString(4),
                                rows.getString(5),
                                Instant.ofEpochMilli(rows.getLong(6))));
            }
        } catch (SQLException ex) {
            throw failure("read overrides", ex);
        }
        return overrides;
    }

    @Override
    public Set<UUID> loadEarned() {
        Set<UUID> earned = new HashSet<>();
        try (Connection connection = connection();
                PreparedStatement select =
                        connection.prepareStatement(
                                "SELECT DISTINCT player_uuid FROM sbr_events WHERE kind = ?")) {
            select.setString(1, GrantEvent.Kind.EARNED.name());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    earned.add(UUID.fromString(rows.getString(1)));
                }
            }
        } catch (SQLException ex) {
            throw failure("read earned players", ex);
        }
        return earned;
    }

    @Override
    public void putOverride(GrantOverride override, GrantEvent event) {
        Objects.requireNonNull(override, "override");
        Objects.requireNonNull(event, "event");
        inTransaction(
                "write override",
                connection -> {
                    try (PreparedStatement upsert =
                            connection.prepareStatement(source.dialect().upsertOverride())) {
                        upsert.setString(1, override.player().toString());
                        upsert.setString(2, override.playerName());
                        upsert.setString(3, override.state().name());
                        upsert.setString(4, override.actor());
                        upsert.setString(5, override.reason());
                        upsert.setLong(6, override.at().toEpochMilli());
                        upsert.executeUpdate();
                    }
                    insert(connection, event);
                });
    }

    @Override
    public void removeOverride(UUID player, GrantEvent event) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        inTransaction(
                "remove override",
                connection -> {
                    try (PreparedStatement delete =
                            connection.prepareStatement(
                                    "DELETE FROM sbr_overrides WHERE player_uuid = ?")) {
                        delete.setString(1, player.toString());
                        delete.executeUpdate();
                    }
                    insert(connection, event);
                });
    }

    @Override
    public void append(GrantEvent event) {
        Objects.requireNonNull(event, "event");
        inTransaction("append event", connection -> insert(connection, event));
    }

    private static void insert(Connection connection, GrantEvent event) throws SQLException {
        try (PreparedStatement insert =
                connection.prepareStatement(
                        "INSERT INTO sbr_events ("
                                + EVENT_COLUMNS
                                + ") VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, event.player().toString());
            insert.setString(2, event.playerName());
            insert.setString(3, event.kind().name());
            insert.setString(4, event.actor());
            insert.setString(5, event.reason());
            insert.setLong(6, event.playtimeTicks());
            insert.setLong(7, event.at().toEpochMilli());
            insert.executeUpdate();
        }
    }

    @Override
    public List<GrantEvent> history(UUID player, int limit) {
        Objects.requireNonNull(player, "player");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1, was " + limit);
        }
        List<GrantEvent> events = new ArrayList<>();
        try (Connection connection = connection();
                PreparedStatement select =
                        connection.prepareStatement(
                                "SELECT player_name, kind, actor, reason, playtime_ticks, at_millis"
                                        + " FROM sbr_events WHERE player_uuid = ?"
                                        + " ORDER BY at_millis DESC, id DESC LIMIT ?")) {
            select.setString(1, player.toString());
            select.setInt(2, limit);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    events.add(
                            new GrantEvent(
                                    player,
                                    rows.getString(1),
                                    GrantEvent.Kind.valueOf(rows.getString(2)),
                                    rows.getString(3),
                                    rows.getString(4),
                                    rows.getLong(5),
                                    Instant.ofEpochMilli(rows.getLong(6))));
                }
            }
        } catch (SQLException ex) {
            throw failure("read history", ex);
        }
        return events;
    }

    /** Closes the connection source; the store cannot be used again. Idempotent. */
    @Override
    public void close() {
        closed = true;
        source.close();
    }

    private Connection connection() throws SQLException {
        if (closed) {
            throw new SQLException("the store is closed");
        }
        return source.open();
    }

    private StorageException failure(String operation, SQLException cause) {
        return new StorageException(
                "could not " + operation + " (" + source.description() + "): " + cause.getMessage(),
                cause);
    }
}

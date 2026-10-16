package com.lewisgibson.spawnbuildrights.storage;

import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Function;

/**
 * The store's tables, built by numbered migrations. {@code sbr_schema} holds the number of the last
 * one applied; on open, every later migration runs in order, each in its own transaction together
 * with the new number, so a migration that fails leaves the number where it was. PostgreSQL and
 * SQLite roll the failed migration's changes back too; MySQL commits each {@code CREATE} or {@code
 * ALTER} as it runs, so a MySQL migration is one statement.
 *
 * <p>A database whose number is higher than this build knows was written by a newer plugin, and is
 * refused rather than read wrongly.
 *
 * <p>A change to the tables is a new migration appended to {@link #MIGRATIONS}, never an edit to
 * one that has shipped.
 */
final class Schema {

    /** Migration {@code n} is at index {@code n - 1}. */
    static final List<Function<Dialect, List<String>>> MIGRATIONS =
            List.of(Schema::version1, Schema::version2);

    /** The schema version this build writes and understands. */
    static final int LATEST = MIGRATIONS.size();

    private static final String EVENT_COLUMNS =
            "player_uuid, player_name, kind, actor, reason, playtime_ticks, at_millis";

    private Schema() {}

    /**
     * Brings the database up to {@link #LATEST}.
     *
     * @param connection an open connection, returned in the auto-commit mode it had
     * @param dialect the database's dialect
     * @param description the database, for the error message
     * @throws SQLException if a statement fails
     * @throws StorageException if the database is newer than this build
     */
    static void migrate(Connection connection, Dialect dialect, String description)
            throws SQLException {
        migrate(connection, dialect, description, MIGRATIONS);
    }

    static void migrate(
            Connection connection,
            Dialect dialect,
            String description,
            List<Function<Dialect, List<String>>> migrations)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS sbr_schema (version INTEGER NOT NULL)"
                            + dialect.tableOptions());
        }
        int current = version(connection);
        if (current == 0) {
            current = adoptUnnumbered(connection, dialect);
        }
        if (current > migrations.size()) {
            throw new StorageException(
                    description
                            + " holds schema version "
                            + current
                            + ", newer than this plugin understands ("
                            + migrations.size()
                            + "); upgrade the plugin",
                    null);
        }
        for (int version = current + 1; version <= migrations.size(); version++) {
            apply(connection, version, migrations.get(version - 1).apply(dialect));
        }
    }

    /**
     * The first release created its tables and then wrote its version in a separate statement, so a
     * store it left behind can have the tables and no number. Its events table says which version
     * it is: without an {@code id} it is version 1, with one it is version 2. MySQL was added after
     * both, so a MySQL database never needs this.
     *
     * @return the version recorded, 0 when there are no tables to adopt
     */
    static int adoptUnnumbered(Connection connection, Dialect dialect) throws SQLException {
        if (dialect == Dialect.MYSQL || !hasColumn(connection, "sbr_events", "player_uuid")) {
            return 0;
        }
        int version = hasColumn(connection, "sbr_events", "id") ? 2 : 1;
        apply(connection, version, List.of());
        return version;
    }

    /**
     * @return the version recorded in {@code sbr_schema}, 0 for a new database
     */
    static int version(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT MAX(version) FROM sbr_schema")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void apply(Connection connection, int version, List<String> statements)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement();
                PreparedStatement record =
                        connection.prepareStatement(
                                "INSERT INTO sbr_schema (version) VALUES (?)")) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("DELETE FROM sbr_schema");
            record.setInt(1, version);
            record.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Whether a table in the connection's current schema has a column, both named in lower case.
     */
    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        // The names are patterns to the driver, where an underscore matches any character.
        String escape = metadata.getSearchStringEscape();
        try (ResultSet columns =
                metadata.getColumns(
                        null,
                        connection.getSchema(),
                        table.replace("_", escape + "_"),
                        column.replace("_", escape + "_"))) {
            return columns.next();
        }
    }

    /**
     * The first schema: the current by-hand decision per player, and the audit log, which had no id
     * of its own.
     */
    private static List<String> version1(Dialect dialect) {
        return List.of(
                "CREATE TABLE sbr_overrides ("
                        + "player_uuid VARCHAR(36) PRIMARY KEY, "
                        + "player_name VARCHAR(64) NOT NULL, "
                        + "state VARCHAR(16) NOT NULL, "
                        + "actor VARCHAR(64) NOT NULL, "
                        + "reason VARCHAR(255) NOT NULL, "
                        + "at_millis BIGINT NOT NULL)"
                        + dialect.tableOptions(),
                "CREATE TABLE sbr_events ("
                        + "player_uuid VARCHAR(36) NOT NULL, "
                        + "player_name VARCHAR(64) NOT NULL, "
                        + "kind VARCHAR(16) NOT NULL, "
                        + "actor VARCHAR(64) NOT NULL, "
                        + "reason VARCHAR(255) NOT NULL, "
                        + "playtime_ticks BIGINT NOT NULL, "
                        + "at_millis BIGINT NOT NULL)"
                        + dialect.tableOptions(),
                "CREATE INDEX sbr_events_player ON sbr_events (player_uuid, at_millis)");
    }

    /**
     * Gives every event an id numbered in the order it was written, so events written in the same
     * millisecond read back in order. SQLite cannot add a key to a table, so on SQLite and
     * PostgreSQL the events are copied into a new table in write order: by time, then by where each
     * row sits in the table, which for a table that is only ever appended to is the order the rows
     * went in. MySQL adds the column in place.
     */
    private static List<String> version2(Dialect dialect) {
        if (dialect == Dialect.MYSQL) {
            return List.of(
                    "ALTER TABLE sbr_events ADD COLUMN "
                            + dialect.generatedIdColumn("id")
                            + " FIRST");
        }
        return List.of(
                "CREATE TABLE sbr_events_v2 ("
                        + dialect.generatedIdColumn("id")
                        + ", "
                        + "player_uuid VARCHAR(36) NOT NULL, "
                        + "player_name VARCHAR(64) NOT NULL, "
                        + "kind VARCHAR(16) NOT NULL, "
                        + "actor VARCHAR(64) NOT NULL, "
                        + "reason VARCHAR(255) NOT NULL, "
                        + "playtime_ticks BIGINT NOT NULL, "
                        + "at_millis BIGINT NOT NULL)",
                "INSERT INTO sbr_events_v2 ("
                        + EVENT_COLUMNS
                        + ") SELECT "
                        + EVENT_COLUMNS
                        + " FROM sbr_events ORDER BY at_millis, "
                        + (dialect == Dialect.SQLITE ? "rowid" : "ctid"),
                "DROP TABLE sbr_events",
                "ALTER TABLE sbr_events_v2 RENAME TO sbr_events",
                "CREATE INDEX sbr_events_player ON sbr_events (player_uuid, at_millis)");
    }
}

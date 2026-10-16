package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening stores written by earlier releases, built here with the exact statements those releases
 * ran. A version 1 store, which had no event id, has its events numbered in the order they were
 * written with nothing lost, and an upgrade that fails leaves it as it was. A version 2 store opens
 * as it is.
 */
@DisplayName("JdbcGrantStore schema upgrade (SQLite)")
class JdbcGrantStoreMigrationTest {

    /** Version 1's schema, statement for statement. */
    static final String[] VERSION_1 = {
        "CREATE TABLE IF NOT EXISTS sbr_schema (version INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS sbr_overrides ("
                + "player_uuid VARCHAR(36) PRIMARY KEY, "
                + "player_name VARCHAR(64) NOT NULL, "
                + "state VARCHAR(16) NOT NULL, "
                + "actor VARCHAR(64) NOT NULL, "
                + "reason VARCHAR(255) NOT NULL, "
                + "at_millis BIGINT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS sbr_events ("
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(64) NOT NULL, "
                + "kind VARCHAR(16) NOT NULL, "
                + "actor VARCHAR(64) NOT NULL, "
                + "reason VARCHAR(255) NOT NULL, "
                + "playtime_ticks BIGINT NOT NULL, "
                + "at_millis BIGINT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS sbr_events_player ON sbr_events (player_uuid, at_millis)",
    };

    /** Version 2's events table and index, as 1.0.0 created them on a new SQLite store. */
    static final String[] VERSION_2_EVENTS = {
        "CREATE TABLE IF NOT EXISTS sbr_events ("
                + "id INTEGER PRIMARY KEY, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(64) NOT NULL, "
                + "kind VARCHAR(16) NOT NULL, "
                + "actor VARCHAR(64) NOT NULL, "
                + "reason VARCHAR(255) NOT NULL, "
                + "playtime_ticks BIGINT NOT NULL, "
                + "at_millis BIGINT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS sbr_events_player ON sbr_events (player_uuid, at_millis)",
    };

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long T1 = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli();
    private static final long T2 = Instant.parse("2026-09-10T13:00:00Z").toEpochMilli();

    @TempDir Path directory;

    private SqliteConnectionSource source;

    @BeforeEach
    void setUp() {
        source = new SqliteConnectionSource(directory.resolve("data.db"));
    }

    @Test
    @DisplayName("a version 1 store is upgraded in place, its events numbered in write order")
    void upgradesVersionOne() throws SQLException {
        writeVersionOne(true);

        JdbcGrantStore store = new JdbcGrantStore(source);
        store.open();

        assertThat(version()).isEqualTo(Schema.LATEST);
        // Three events in one millisecond read back newest first in the order they were written,
        // which version 1 could not promise.
        assertThat(store.history(STEVE, 10))
                .extracting(GrantEvent::kind)
                .containsExactly(
                        GrantEvent.Kind.REVOKED,
                        GrantEvent.Kind.RESET,
                        GrantEvent.Kind.GRANTED,
                        GrantEvent.Kind.EARNED);
        assertThat(ids()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(store.loadOverrides().get(STEVE).state()).isEqualTo(OverrideState.REVOKED);
        assertThat(store.loadEarned()).containsExactly(STEVE);

        // Opening again changes nothing, and a new event is numbered after the old ones.
        store.open();
        store.append(
                new GrantEvent(
                        STEVE,
                        "Steve",
                        GrantEvent.Kind.RESET,
                        "console",
                        "",
                        -1,
                        Instant.ofEpochMilli(T2)));
        assertThat(ids()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(store.history(STEVE, 1))
                .singleElement()
                .extracting(GrantEvent::kind)
                .isEqualTo(GrantEvent.Kind.RESET);
    }

    @Test
    @DisplayName("version 1 tables whose version row was never written are upgraded too")
    void upgradesVersionOneWithoutItsVersionRow() throws SQLException {
        writeVersionOne(false);

        new JdbcGrantStore(source).open();

        assertThat(version()).isEqualTo(Schema.LATEST);
        assertThat(ids()).hasSize(4);
    }

    @Test
    @DisplayName("a current store that lost its version row is marked current again, untouched")
    void currentStoreWithoutItsVersionRow() throws SQLException {
        writeVersionOne(true);
        new JdbcGrantStore(source).open();
        try (Connection connection = source.open();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM sbr_schema");
        }

        new JdbcGrantStore(source).open();

        assertThat(version()).isEqualTo(Schema.LATEST);
        assertThat(ids()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("a version 2 store written by 1.0.0 opens as it is")
    void opensVersionTwo() throws SQLException {
        try (Connection connection = source.open();
                Statement statement = connection.createStatement()) {
            statement.execute(VERSION_1[0]);
            statement.execute(VERSION_1[1]);
            for (String ddl : VERSION_2_EVENTS) {
                statement.execute(ddl);
            }
            statement.execute("INSERT INTO sbr_schema (version) VALUES (2)");
            statement.execute(
                    "INSERT INTO sbr_events (id, player_uuid, player_name, kind, actor, reason,"
                            + " playtime_ticks, at_millis) VALUES (7, '"
                            + STEVE
                            + "', 'Steve', 'EARNED', 'console', '', 100, "
                            + T1
                            + ")");
        }

        JdbcGrantStore store = new JdbcGrantStore(source);
        store.open();

        assertThat(version()).isEqualTo(2);
        assertThat(ids()).containsExactly(7L);
        assertThat(store.loadEarned()).containsExactly(STEVE);
    }

    @Test
    @DisplayName("an upgrade that fails leaves the version 1 store exactly as it was")
    void failedUpgradeChangesNothing() throws SQLException {
        try (Connection connection = source.open();
                Statement statement = connection.createStatement()) {
            statement.execute(VERSION_1[0]);
            statement.execute("INSERT INTO sbr_schema (version) VALUES (1)");
            // A damaged version 1 events table: the copy into the new table cannot read it.
            statement.execute("CREATE TABLE sbr_events (player_uuid VARCHAR(36) NOT NULL)");
            statement.execute("INSERT INTO sbr_events VALUES ('" + STEVE + "')");
        }

        assertThatThrownBy(() -> new JdbcGrantStore(source).open())
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("could not open");

        assertThat(version()).isEqualTo(1);
        try (Connection connection = source.open();
                Statement statement = connection.createStatement();
                ResultSet tables =
                        statement.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type = 'table'"
                                        + " ORDER BY name")) {
            List<String> names = new ArrayList<>();
            while (tables.next()) {
                names.add(tables.getString(1));
            }
            assertThat(names).containsExactly("sbr_events", "sbr_schema");
        }
    }

    private void writeVersionOne(boolean withVersionRow) throws SQLException {
        try (Connection connection = source.open();
                Statement statement = connection.createStatement()) {
            for (String ddl : VERSION_1) {
                statement.execute(ddl);
            }
            if (withVersionRow) {
                statement.execute("INSERT INTO sbr_schema (version) VALUES (1)");
            }
            statement.execute(
                    "INSERT INTO sbr_overrides VALUES ('"
                            + STEVE
                            + "', 'Steve', 'REVOKED',"
                            + " 'console', 'grief', "
                            + T2
                            + ")");
            event(statement, "EARNED", T1);
            event(statement, "GRANTED", T2);
            event(statement, "RESET", T2);
            event(statement, "REVOKED", T2);
        }
    }

    private static void event(Statement statement, String kind, long at) throws SQLException {
        statement.execute(
                "INSERT INTO sbr_events VALUES ('"
                        + STEVE
                        + "', 'Steve', '"
                        + kind
                        + "', 'console', '', -1, "
                        + at
                        + ")");
    }

    private int version() throws SQLException {
        try (Connection connection = source.open();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT MAX(version) FROM sbr_schema")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private List<Long> ids() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = source.open();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id FROM sbr_events ORDER BY id")) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
            }
        }
        return ids;
    }
}

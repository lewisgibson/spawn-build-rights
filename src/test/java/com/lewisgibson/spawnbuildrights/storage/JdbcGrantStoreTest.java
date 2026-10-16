package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import com.lewisgibson.spawnbuildrights.core.grant.GrantOverride;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("JdbcGrantStore (SQLite)")
class JdbcGrantStoreTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant T1 = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-10T13:00:00Z");
    private static final Instant T3 = Instant.parse("2026-09-10T14:00:00Z");

    @TempDir Path directory;

    private static GrantOverride revoked(UUID player, Instant at) {
        return new GrantOverride(player, "Steve", OverrideState.REVOKED, "Alex", "grief", at);
    }

    private static GrantEvent event(UUID player, GrantEvent.Kind kind, Instant at) {
        return new GrantEvent(player, "Steve", kind, "Alex", "why", 100L, at);
    }

    /** The store contract, run once per kind of connection source. */
    abstract class Contract {

        ConnectionSource source;
        JdbcGrantStore store;

        abstract ConnectionSource source(Path file);

        @BeforeEach
        void open() {
            source = source(directory.resolve("nested/data.db"));
            store = new JdbcGrantStore(source);
            store.open();
        }

        @AfterEach
        void close() {
            store.close();
        }

        @Test
        @DisplayName("creates the file, its parent directory and the schema, and is idempotent")
        void createsSchema() throws SQLException {
            assertThat(directory.resolve("nested/data.db")).exists();
            store.open();
            store.open();
            assertThat(store.loadOverrides()).isEmpty();
            assertThat(store.loadEarned()).isEmpty();
            assertThat(source.description()).endsWith("data.db");
            try (Connection connection = source.open()) {
                assertThat(Schema.version(connection)).isEqualTo(Schema.LATEST);
            }
        }

        @Test
        @DisplayName("refuses a database written by a newer plugin")
        void refusesNewerSchema() throws SQLException {
            try (Connection connection = source.open();
                    Statement statement = connection.createStatement()) {
                statement.execute("UPDATE sbr_schema SET version = 99");
            }
            assertThatThrownBy(store::open)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("schema version 99")
                    .hasMessageContaining("upgrade the plugin");
        }

        @Test
        @DisplayName("stores, replaces, reloads and removes an override, each with its event")
        void overrides() {
            store.putOverride(revoked(STEVE, T1), event(STEVE, GrantEvent.Kind.REVOKED, T1));
            store.putOverride(
                    new GrantOverride(ALEX, "Alex", OverrideState.GRANTED, "console", "", T1),
                    event(ALEX, GrantEvent.Kind.GRANTED, T1));
            assertThat(store.loadOverrides()).containsOnlyKeys(STEVE, ALEX);
            assertThat(store.loadOverrides().get(STEVE)).isEqualTo(revoked(STEVE, T1));

            store.putOverride(
                    new GrantOverride(STEVE, "Steve2", OverrideState.GRANTED, "Bob", "ok", T2),
                    event(STEVE, GrantEvent.Kind.GRANTED, T2));
            assertThat(store.loadOverrides().get(STEVE))
                    .isEqualTo(
                            new GrantOverride(
                                    STEVE, "Steve2", OverrideState.GRANTED, "Bob", "ok", T2));

            store.removeOverride(STEVE, event(STEVE, GrantEvent.Kind.RESET, T3));
            store.removeOverride(STEVE, event(STEVE, GrantEvent.Kind.RESET, T3));
            assertThat(store.loadOverrides()).containsOnlyKeys(ALEX);
            assertThat(store.history(STEVE, 10))
                    .extracting(GrantEvent::kind)
                    .containsExactly(
                            GrantEvent.Kind.RESET,
                            GrantEvent.Kind.RESET,
                            GrantEvent.Kind.GRANTED,
                            GrantEvent.Kind.REVOKED);
        }

        @Test
        @DisplayName(
                "an override whose event cannot be written is not written either, and vice versa")
        void overrideAndEventAreOneTransaction() throws SQLException {
            store.putOverride(revoked(STEVE, T1), event(STEVE, GrantEvent.Kind.REVOKED, T1));
            execute(
                    "CREATE TRIGGER no_events BEFORE INSERT ON sbr_events"
                            + " BEGIN SELECT RAISE(ABORT, 'audit log full'); END");

            assertThatThrownBy(
                            () ->
                                    store.putOverride(
                                            revoked(ALEX, T2),
                                            event(ALEX, GrantEvent.Kind.REVOKED, T2)))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not write override")
                    .hasMessageContaining("audit log full");
            assertThatThrownBy(
                            () ->
                                    store.removeOverride(
                                            STEVE, event(STEVE, GrantEvent.Kind.RESET, T2)))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not remove override");
            assertThatThrownBy(() -> store.append(event(STEVE, GrantEvent.Kind.EARNED, T2)))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not append event");

            assertThat(store.loadOverrides()).containsOnlyKeys(STEVE);
            assertThat(store.loadOverrides().get(STEVE)).isEqualTo(revoked(STEVE, T1));
        }

        @Test
        @DisplayName(
                "events written in the same millisecond read back newest first, in write order")
        void sameMillisecondKeepsWriteOrder() {
            store.append(event(STEVE, GrantEvent.Kind.GRANTED, T1));
            store.append(event(STEVE, GrantEvent.Kind.REVOKED, T1));
            store.append(event(STEVE, GrantEvent.Kind.RESET, T1));

            assertThat(store.history(STEVE, 10))
                    .extracting(GrantEvent::kind)
                    .containsExactly(
                            GrantEvent.Kind.RESET,
                            GrantEvent.Kind.REVOKED,
                            GrantEvent.Kind.GRANTED);
        }

        @Test
        @DisplayName(
                "appends events, reads them newest first with a limit, and lists earned players")
        void events() {
            store.append(event(STEVE, GrantEvent.Kind.EARNED, T1));
            store.append(event(STEVE, GrantEvent.Kind.REVOKED, T3));
            store.append(event(STEVE, GrantEvent.Kind.RESET, T2));
            store.append(event(ALEX, GrantEvent.Kind.EARNED, T1));
            store.append(event(ALEX, GrantEvent.Kind.EARNED, T2));

            List<GrantEvent> history = store.history(STEVE, 10);
            assertThat(history)
                    .extracting(GrantEvent::kind)
                    .containsExactly(
                            GrantEvent.Kind.REVOKED, GrantEvent.Kind.RESET, GrantEvent.Kind.EARNED);
            assertThat(history.get(0)).isEqualTo(event(STEVE, GrantEvent.Kind.REVOKED, T3));
            assertThat(store.history(STEVE, 2)).hasSize(2);
            assertThat(store.history(UUID.randomUUID(), 5)).isEmpty();
            assertThat(store.loadEarned()).containsExactlyInAnyOrder(STEVE, ALEX);
        }

        @Test
        @DisplayName("rejects a history limit below one and null arguments")
        void rejectsBadArguments() {
            assertThatIllegalArgumentException().isThrownBy(() -> store.history(STEVE, 0));
            assertThatNullPointerException().isThrownBy(() -> store.history(null, 1));
            GrantEvent reset = event(STEVE, GrantEvent.Kind.RESET, T1);
            assertThatNullPointerException().isThrownBy(() -> store.putOverride(null, reset));
            assertThatNullPointerException()
                    .isThrownBy(() -> store.putOverride(revoked(STEVE, T1), null));
            assertThatNullPointerException().isThrownBy(() -> store.removeOverride(null, reset));
            assertThatNullPointerException().isThrownBy(() -> store.removeOverride(STEVE, null));
            assertThatNullPointerException().isThrownBy(() -> store.append(null));
            assertThatNullPointerException().isThrownBy(() -> new JdbcGrantStore(null));
            assertThatNullPointerException().isThrownBy(() -> new SqliteConnectionSource(null));
        }

        @Test
        @DisplayName("refuses every operation after close, and closes again harmlessly")
        void closed() {
            store.close();
            store.close();
            assertThatThrownBy(() -> store.loadOverrides())
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("closed");
            assertThatThrownBy(store::open)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("closed");
        }

        void execute(String sql) throws SQLException {
            try (Connection connection = source.open();
                    Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    @Nested
    @DisplayName("on a SQLite file")
    class OnSqlite extends Contract {

        @Override
        ConnectionSource source(Path file) {
            return new SqliteConnectionSource(file);
        }
    }

    /**
     * The same contract through the connection pool, so the pooled path is exercised without a
     * database server.
     */
    @Nested
    @DisplayName("through the connection pool")
    class ThroughThePool extends Contract {

        @Override
        ConnectionSource source(Path file) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return PooledConnectionSource.start(
                    Dialect.SQLITE,
                    "pooled " + file,
                    new DriverDataSource(
                            new org.sqlite.JDBC(), "jdbc:sqlite:" + file, new Properties()));
        }
    }

    @Nested
    @DisplayName("when the database cannot be reached")
    class Unreachable {

        private final ConnectionSource broken =
                new ConnectionSource() {
                    @Override
                    public Connection open() throws SQLException {
                        throw new SQLException("no such host");
                    }

                    @Override
                    public String description() {
                        return "broken";
                    }

                    @Override
                    public Dialect dialect() {
                        return Dialect.SQLITE;
                    }

                    @Override
                    public void close() {}
                };

        private final JdbcGrantStore unreachable = new JdbcGrantStore(broken);

        @Test
        @DisplayName("every operation fails with a StorageException naming the source")
        void everyOperationFails() {
            assertFails(unreachable::open, "open");
            assertFails(unreachable::loadOverrides, "read overrides");
            assertFails(unreachable::loadEarned, "read earned players");
            GrantEvent reset = event(STEVE, GrantEvent.Kind.RESET, T1);
            assertFails(() -> unreachable.putOverride(revoked(STEVE, T1), reset), "write override");
            assertFails(() -> unreachable.removeOverride(STEVE, reset), "remove override");
            assertFails(
                    () -> unreachable.append(event(STEVE, GrantEvent.Kind.RESET, T1)),
                    "append event");
            assertFails(() -> unreachable.history(STEVE, 1), "read history");
        }

        private void assertFails(Runnable operation, String what) {
            assertThatThrownBy(operation::run)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not " + what)
                    .hasMessageContaining("(broken)")
                    .hasMessageContaining("no such host");
        }
    }

    @Test
    @DisplayName("a SQLite path whose parent cannot be created fails as a SQLException")
    void unwritableParent() throws Exception {
        Path file = directory.resolve("file");
        Files.writeString(file, "not a directory");
        SqliteConnectionSource bad = new SqliteConnectionSource(file.resolve("data.db"));
        assertThatThrownBy(bad::open)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("cannot create");
    }
}

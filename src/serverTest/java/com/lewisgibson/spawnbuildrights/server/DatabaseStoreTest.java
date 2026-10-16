package com.lewisgibson.spawnbuildrights.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import com.lewisgibson.spawnbuildrights.core.grant.GrantOverride;
import com.lewisgibson.spawnbuildrights.core.grant.GrantStore;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import com.lewisgibson.spawnbuildrights.core.settings.StorageType;
import com.lewisgibson.spawnbuildrights.storage.DataSources;
import com.lewisgibson.spawnbuildrights.storage.GrantStores;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The store contract against real database servers: the same operations the SQLite unit test
 * proves, so the SQL each dialect sends is shown to work on the server it is written for, through
 * the same connection pool the plugin uses. On PostgreSQL, stores written by earlier releases are
 * opened too.
 */
@DisplayName("The store on real database servers")
class DatabaseStoreTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Path WORK = Path.of(System.getProperty("sbr.workDir"));
    private static final Path FOLDER = WORK.resolve("store");
    private static final Instant T1 = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-10T13:00:00Z");

    /** Version 1's schema, statement for statement. */
    private static final String[] VERSION_1 = {
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

    @BeforeAll
    static void requireDocker() {
        Docker.requireDocker();
    }

    @Test
    @DisplayName("PostgreSQL, in a named schema, without TLS")
    void postgres() throws Exception {
        try (PostgresServer postgres = new PostgresServer()) {
            postgres.start();
            postgres.query("CREATE SCHEMA plugins");
            StorageSettings settings = postgres(postgres, "plugins", "disable", "");
            contract(settings, sql -> postgres.query(sql.replace("sbr_", "plugins.sbr_")));

            // An override whose audit row cannot be written is rolled back with it.
            postgres.query(
                    "CREATE FUNCTION plugins.no_events() RETURNS trigger AS"
                            + " $$ BEGIN RAISE EXCEPTION 'audit log full'; END $$"
                            + " LANGUAGE plpgsql");
            postgres.query(
                    "CREATE TRIGGER no_events BEFORE INSERT ON plugins.sbr_events"
                            + " FOR EACH ROW EXECUTE FUNCTION plugins.no_events()");
            postgres.query("UPDATE plugins.sbr_schema SET version = 2");
            GrantStore store = GrantStores.open(settings, FOLDER);
            assertThatThrownBy(
                            () ->
                                    store.putOverride(
                                            override(OverrideState.REVOKED, "Alex", T2),
                                            event(GrantEvent.Kind.REVOKED, T2)))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("audit log full");
            assertThat(store.loadOverrides()).isEmpty();
            store.close();
        }
    }

    @Test
    @DisplayName("PostgreSQL stores written by 1.0.0 and by the first schema open in place")
    void postgresEarlierStores() throws Exception {
        try (PostgresServer postgres = new PostgresServer()) {
            postgres.start();

            // Version 1: no event id. Events in one millisecond are numbered in write order.
            postgres.query("CREATE SCHEMA legacy");
            StorageSettings legacy = postgres(postgres, "legacy", "disable", "");
            try (Connection connection = DataSources.postgres(legacy.postgres()).getConnection();
                    Statement statement = connection.createStatement()) {
                for (String ddl : VERSION_1) {
                    statement.execute(ddl);
                }
                statement.execute("INSERT INTO sbr_schema (version) VALUES (1)");
                statement.execute(
                        "INSERT INTO sbr_overrides VALUES ('"
                                + STEVE
                                + "', 'Steve', 'REVOKED', 'console', 'grief', "
                                + T2.toEpochMilli()
                                + ")");
                for (String kind : new String[] {"EARNED", "GRANTED", "RESET", "REVOKED"}) {
                    long at = kind.equals("EARNED") ? T1.toEpochMilli() : T2.toEpochMilli();
                    statement.execute(
                            "INSERT INTO sbr_events VALUES ('"
                                    + STEVE
                                    + "', 'Steve', '"
                                    + kind
                                    + "', 'console', '', -1, "
                                    + at
                                    + ")");
                }
            }
            GrantStore store = GrantStores.open(legacy, FOLDER);
            store.close();
            store = GrantStores.open(legacy, FOLDER);
            assertThat(store.history(STEVE, 10))
                    .extracting(GrantEvent::kind)
                    .containsExactly(
                            GrantEvent.Kind.REVOKED,
                            GrantEvent.Kind.RESET,
                            GrantEvent.Kind.GRANTED,
                            GrantEvent.Kind.EARNED);
            assertThat(store.loadOverrides().get(STEVE).state()).isEqualTo(OverrideState.REVOKED);
            assertThat(postgres.query("SELECT MAX(version) FROM legacy.sbr_schema").strip())
                    .isEqualTo("2");
            assertThat(postgres.query("SELECT id FROM legacy.sbr_events ORDER BY id").strip())
                    .isEqualTo("1\n2\n3\n4");
            store.append(event(GrantEvent.Kind.RESET, T2));
            assertThat(store.history(STEVE, 1))
                    .singleElement()
                    .extracting(GrantEvent::kind)
                    .isEqualTo(GrantEvent.Kind.RESET);
            store.close();

            // Version 2, as 1.0.0 created it: opened as it is.
            postgres.query("CREATE SCHEMA released");
            StorageSettings released = postgres(postgres, "released", "disable", "");
            try (Connection connection = DataSources.postgres(released.postgres()).getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute(VERSION_1[0]);
                statement.execute(VERSION_1[1]);
                statement.execute(
                        "CREATE TABLE IF NOT EXISTS sbr_events ("
                                + "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                                + "player_uuid VARCHAR(36) NOT NULL, "
                                + "player_name VARCHAR(64) NOT NULL, "
                                + "kind VARCHAR(16) NOT NULL, "
                                + "actor VARCHAR(64) NOT NULL, "
                                + "reason VARCHAR(255) NOT NULL, "
                                + "playtime_ticks BIGINT NOT NULL, "
                                + "at_millis BIGINT NOT NULL)");
                statement.execute(VERSION_1[3]);
                statement.execute("INSERT INTO sbr_schema (version) VALUES (2)");
                statement.execute(
                        "INSERT INTO sbr_events (player_uuid, player_name, kind, actor, reason,"
                                + " playtime_ticks, at_millis) VALUES ('"
                                + STEVE
                                + "', 'Steve', 'EARNED', 'console', '', 100, "
                                + T1.toEpochMilli()
                                + ")");
            }
            store = GrantStores.open(released, FOLDER);
            assertThat(store.loadEarned()).containsExactly(STEVE);
            store.append(event(GrantEvent.Kind.RESET, T2));
            assertThat(postgres.query("SELECT id FROM released.sbr_events ORDER BY id").strip())
                    .isEqualTo("1\n2");
            assertThat(postgres.query("SELECT MAX(version) FROM released.sbr_schema").strip())
                    .isEqualTo("2");
            store.close();
        }
    }

    @Test
    @DisplayName("PostgreSQL over TLS: verify-full against the server's CA works, plain is refused")
    void postgresTls() throws Exception {
        try (PostgresServer postgres = new PostgresServer()) {
            postgres.start();
            postgres.requireTls(WORK.resolve("postgres-store-tls"));
            String ca = postgres.caCertificate().toString();

            // pg_hba rejects every TCP connection without TLS, so getting through proves TLS.
            GrantStore store = GrantStores.open(postgres(postgres, "", "verify-full", ca), FOLDER);
            store.append(event(GrantEvent.Kind.EARNED, T1));
            assertThat(store.loadEarned()).containsExactly(STEVE);
            store.close();

            assertThatThrownBy(
                            () -> GrantStores.open(postgres(postgres, "", "disable", ""), FOLDER))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not connect");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MySqlServer.Flavour.class)
    @DisplayName("MySQL and MariaDB, without TLS")
    void mysql(MySqlServer.Flavour flavour) throws Exception {
        try (MySqlServer server = new MySqlServer(flavour)) {
            server.start();
            contract(mysql(server, "disable"), server::query);
            assertThat(
                            server.query(
                                    "SELECT TABLE_COLLATION FROM information_schema.TABLES"
                                            + " WHERE TABLE_NAME = 'sbr_events'"))
                    .contains("utf8mb4_bin");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MySqlServer.Flavour.class)
    @DisplayName("MySQL and MariaDB over TLS")
    void mysqlTls(MySqlServer.Flavour flavour) throws Exception {
        try (MySqlServer server = new MySqlServer(flavour)) {
            server.start();
            StorageSettings settings = mysql(server, "trust");
            try (Connection connection = DataSources.mysql(settings.mysql()).getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows =
                            statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                rows.next();
                assertThat(rows.getString(2)).as("the TLS cipher in use").isNotBlank();
            }
            GrantStore store = GrantStores.open(settings, FOLDER);
            store.append(event(GrantEvent.Kind.EARNED, T1));
            assertThat(store.loadEarned()).containsExactly(STEVE);
            store.close();
        }
    }

    /**
     * Opens the store twice, replaces and removes an override, reads events written in the same
     * millisecond back in write order, then refuses a schema newer than the plugin.
     */
    private static void contract(StorageSettings settings, Consumer<String> sql) {
        GrantStore store = GrantStores.open(settings, FOLDER);
        store.close();
        store = GrantStores.open(settings, FOLDER);
        assertThat(store.loadOverrides()).isEmpty();

        store.putOverride(
                override(OverrideState.REVOKED, "Alex", T1), event(GrantEvent.Kind.REVOKED, T1));
        store.putOverride(
                override(OverrideState.GRANTED, "Bob", T2), event(GrantEvent.Kind.GRANTED, T2));
        assertThat(store.loadOverrides().get(STEVE))
                .isEqualTo(override(OverrideState.GRANTED, "Bob", T2));

        store.append(
                new GrantEvent(
                        STEVE, "Steve", GrantEvent.Kind.EARNED, "playtime", "", 72_000L, T1));
        store.append(event(GrantEvent.Kind.REVOKED, T2));
        // Same millisecond: newest first, in the order written.
        assertThat(store.history(STEVE, 10))
                .extracting(GrantEvent::kind)
                .containsExactly(
                        GrantEvent.Kind.REVOKED,
                        GrantEvent.Kind.GRANTED,
                        GrantEvent.Kind.EARNED,
                        GrantEvent.Kind.REVOKED);
        assertThat(store.history(STEVE, 1)).hasSize(1);
        assertThat(store.loadEarned()).containsExactly(STEVE);

        store.removeOverride(STEVE, event(GrantEvent.Kind.RESET, T2));
        assertThat(store.loadOverrides()).isEmpty();
        store.close();

        sql.accept("UPDATE sbr_schema SET version = 99");
        assertThatThrownBy(() -> GrantStores.open(settings, FOLDER))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("schema version 99");
    }

    private static GrantOverride override(OverrideState state, String actor, Instant at) {
        return new GrantOverride(STEVE, "Steve", state, actor, "why", at);
    }

    private static GrantEvent event(GrantEvent.Kind kind, Instant at) {
        return new GrantEvent(STEVE, "Steve", kind, "console", "", -1, at);
    }

    private static StorageSettings postgres(
            PostgresServer postgres, String schema, String sslMode, String sslRootCert) {
        StorageSettings defaults = StorageSettings.sqliteDefault();
        return new StorageSettings(
                StorageType.POSTGRES,
                defaults.sqliteFile(),
                new StorageSettings.Postgres(
                        "127.0.0.1",
                        postgres.hostPort(),
                        PostgresServer.DATABASE,
                        PostgresServer.USER,
                        PostgresServer.PASSWORD,
                        schema,
                        sslMode,
                        sslRootCert),
                defaults.mysql());
    }

    private static StorageSettings mysql(MySqlServer server, String sslMode) {
        StorageSettings defaults = StorageSettings.sqliteDefault();
        return new StorageSettings(
                StorageType.MYSQL,
                defaults.sqliteFile(),
                defaults.postgres(),
                new StorageSettings.MySql(
                        "127.0.0.1",
                        server.hostPort(),
                        MySqlServer.DATABASE,
                        MySqlServer.USER,
                        MySqlServer.PASSWORD,
                        sslMode,
                        ""));
    }
}

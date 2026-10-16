package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.grant.GrantStore;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import com.lewisgibson.spawnbuildrights.core.settings.StorageType;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GrantStores")
class GrantStoresTest {

    private static final StorageSettings DEFAULTS = StorageSettings.sqliteDefault();

    @TempDir Path folder;

    @Test
    @DisplayName("opens the SQLite default under the data folder")
    void opensSqlite() {
        GrantStore store = GrantStores.open(DEFAULTS, folder);
        assertThat(folder.resolve("data.db")).exists();
        assertThat(store.loadOverrides()).isEmpty();
        store.close();
        assertThat(GrantStores.describe(DEFAULTS, folder))
                .isEqualTo("sqlite " + folder.resolve("data.db").toAbsolutePath());
    }

    @Test
    @DisplayName("closes the store again when opening it fails")
    void closesOnFailure() throws Exception {
        try (Connection connection = new SqliteConnectionSource(folder.resolve("data.db")).open();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sbr_schema (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO sbr_schema VALUES (99)");
        }
        assertThatThrownBy(() -> GrantStores.open(DEFAULTS, folder))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("schema version 99");
    }

    @Test
    @DisplayName("connects to PostgreSQL as configured and fails plainly when it is down")
    void postgresUnreachable() throws Exception {
        int port = closedPort();
        StorageSettings settings =
                new StorageSettings(
                        StorageType.POSTGRES,
                        "data.db",
                        new StorageSettings.Postgres(
                                "127.0.0.1", port, "sbr", "u", "p", "s", "disable", ""),
                        DEFAULTS.mysql());
        String description = "postgres u@127.0.0.1:" + port + "/sbr schema s sslmode disable";
        assertThat(GrantStores.describe(settings, folder)).isEqualTo(description);
        assertThatThrownBy(() -> GrantStores.open(settings, folder))
                .isInstanceOf(StorageException.class)
                .hasMessageStartingWith("could not connect (" + description + ")");
    }

    @Test
    @DisplayName("connects to MySQL as configured and fails plainly when it is down")
    void mysqlUnreachable() throws Exception {
        int port = closedPort();
        StorageSettings settings =
                new StorageSettings(
                        StorageType.MYSQL,
                        "data.db",
                        DEFAULTS.postgres(),
                        new StorageSettings.MySql(
                                "127.0.0.1", port, "sbr", "u", "p", "disable", ""));
        String description = "mysql u@127.0.0.1:" + port + "/sbr sslmode disable";
        assertThat(GrantStores.describe(settings, folder)).isEqualTo(description);
        assertThatThrownBy(() -> GrantStores.open(settings, folder))
                .isInstanceOf(StorageException.class)
                .hasMessageStartingWith("could not connect (" + description + ")");
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> GrantStores.open(null, folder));
        assertThatNullPointerException().isThrownBy(() -> GrantStores.open(DEFAULTS, null));
        assertThatNullPointerException().isThrownBy(() -> GrantStores.describe(null, folder));
        assertThatNullPointerException().isThrownBy(() -> GrantStores.describe(DEFAULTS, null));
    }

    private static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

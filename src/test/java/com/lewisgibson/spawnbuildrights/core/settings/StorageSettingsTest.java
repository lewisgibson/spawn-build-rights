package com.lewisgibson.spawnbuildrights.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("StorageSettings")
class StorageSettingsTest {

    private static final StorageSettings.Postgres PG =
            new StorageSettings.Postgres("db", 5432, "d", "u", "p", "s", "prefer", "");
    private static final StorageSettings.MySql MY =
            new StorageSettings.MySql("db", 3306, "d", "u", "p", "disable", "");

    @Test
    @DisplayName("the default is SQLite in data.db with placeholder server details")
    void sqliteDefault() {
        StorageSettings settings = StorageSettings.sqliteDefault();
        assertThat(settings.type()).isEqualTo(StorageType.SQLITE);
        assertThat(settings.sqliteFile()).isEqualTo("data.db");
        assertThat(settings.postgres())
                .isEqualTo(
                        new StorageSettings.Postgres(
                                "localhost",
                                5432,
                                "spawnbuildrights",
                                "spawnbuildrights",
                                "",
                                "",
                                "prefer",
                                ""));
        assertThat(settings.mysql())
                .isEqualTo(
                        new StorageSettings.MySql(
                                "localhost",
                                3306,
                                "spawnbuildrights",
                                "spawnbuildrights",
                                "",
                                "disable",
                                ""));
    }

    @Test
    @DisplayName("rejects nulls and a blank SQLite file")
    void rejectsBad() {
        assertThatNullPointerException().isThrownBy(() -> new StorageSettings(null, "f", PG, MY));
        assertThatNullPointerException()
                .isThrownBy(() -> new StorageSettings(StorageType.SQLITE, null, PG, MY));
        assertThatNullPointerException()
                .isThrownBy(() -> new StorageSettings(StorageType.SQLITE, "f", null, MY));
        assertThatNullPointerException()
                .isThrownBy(() -> new StorageSettings(StorageType.SQLITE, "f", PG, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StorageSettings(StorageType.SQLITE, " ", PG, MY))
                .withMessageContaining("storage.sqlite.file");
    }

    @Nested
    @DisplayName("Postgres")
    class Postgres {

        @Test
        @DisplayName("rejects nulls, naming the setting")
        void rejectsNulls() {
            assertThatNullPointerException()
                    .isThrownBy(() -> pg(null, 1, "d", "u", "p", "s", "prefer", ""))
                    .withMessage("storage.postgres.host");
            assertThatNullPointerException()
                    .isThrownBy(() -> pg("h", 1, null, "u", "p", "s", "prefer", ""))
                    .withMessage("storage.postgres.database");
            assertThatNullPointerException()
                    .isThrownBy(() -> pg("h", 1, "d", null, "p", "s", "prefer", ""))
                    .withMessage("storage.postgres.user");
            assertThatNullPointerException()
                    .isThrownBy(() -> pg("h", 1, "d", "u", null, "s", "prefer", ""));
            assertThatNullPointerException()
                    .isThrownBy(() -> pg("h", 1, "d", "u", "p", null, "prefer", ""));
            assertThatNullPointerException()
                    .isThrownBy(() -> pg("h", 1, "d", "u", "p", "s", null, ""))
                    .withMessage("storage.postgres.ssl-mode");
            assertThatNullPointerException()
                    .isThrownBy(() -> pg("h", 1, "d", "u", "p", "s", "prefer", null));
        }

        @Test
        @DisplayName("rejects a blank host, database or user and a port out of range")
        void rejectsBlanksAndPorts() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pg(" ", 1, "d", "u", "p", "s", "prefer", ""))
                    .withMessageContaining("storage.postgres.host");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pg("h", 1, "", "u", "p", "s", "prefer", ""))
                    .withMessageContaining("storage.postgres.database");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pg("h", 1, "d", " ", "p", "s", "prefer", ""))
                    .withMessageContaining("storage.postgres.user");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pg("h", 0, "d", "u", "p", "s", "prefer", ""))
                    .withMessage("storage.postgres.port must be 1-65535, was 0");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pg("h", 65536, "d", "u", "p", "s", "prefer", ""))
                    .withMessageContaining("was 65536");
            assertThat(pg("h", 65535, "d", "u", "p", "s", "prefer", "").port()).isEqualTo(65535);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {"disable", "allow", "prefer", "require", "verify-ca", "verify-full"})
        @DisplayName("accepts every sslmode the driver knows")
        void acceptsSslModes(String mode) {
            assertThat(pg("h", 1, "d", "u", "p", "", mode, "/ca.crt").sslMode()).isEqualTo(mode);
        }

        @Test
        @DisplayName("rejects an unknown sslmode, listing the valid ones")
        void rejectsUnknownSslMode() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pg("h", 1, "d", "u", "p", "", "on", ""))
                    .withMessage(
                            "storage.postgres.ssl-mode must be one of disable, allow, prefer,"
                                    + " require, verify-ca, verify-full, was 'on'");
        }

        private static StorageSettings.Postgres pg(
                String host,
                int port,
                String database,
                String user,
                String password,
                String schema,
                String sslMode,
                String sslRootCert) {
            return new StorageSettings.Postgres(
                    host, port, database, user, password, schema, sslMode, sslRootCert);
        }
    }

    @Nested
    @DisplayName("MySql")
    class MySql {

        @Test
        @DisplayName("rejects nulls, naming the setting")
        void rejectsNulls() {
            assertThatNullPointerException()
                    .isThrownBy(() -> my(null, 1, "d", "u", "p", "disable", ""))
                    .withMessage("storage.mysql.host");
            assertThatNullPointerException()
                    .isThrownBy(() -> my("h", 1, null, "u", "p", "disable", ""))
                    .withMessage("storage.mysql.database");
            assertThatNullPointerException()
                    .isThrownBy(() -> my("h", 1, "d", null, "p", "disable", ""))
                    .withMessage("storage.mysql.user");
            assertThatNullPointerException()
                    .isThrownBy(() -> my("h", 1, "d", "u", null, "disable", ""));
            assertThatNullPointerException()
                    .isThrownBy(() -> my("h", 1, "d", "u", "p", null, ""))
                    .withMessage("storage.mysql.ssl-mode");
            assertThatNullPointerException()
                    .isThrownBy(() -> my("h", 1, "d", "u", "p", "disable", null));
        }

        @Test
        @DisplayName("rejects a blank host, database or user and a port out of range")
        void rejectsBlanksAndPorts() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> my("", 1, "d", "u", "p", "disable", ""))
                    .withMessageContaining("storage.mysql.host");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> my("h", 1, " ", "u", "p", "disable", ""))
                    .withMessageContaining("storage.mysql.database");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> my("h", 1, "d", "", "p", "disable", ""))
                    .withMessageContaining("storage.mysql.user");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> my("h", 70000, "d", "u", "p", "disable", ""))
                    .withMessage("storage.mysql.port must be 1-65535, was 70000");
        }

        @ParameterizedTest
        @ValueSource(strings = {"disable", "trust", "verify-ca", "verify-full"})
        @DisplayName("accepts every sslMode the driver knows")
        void acceptsSslModes(String mode) {
            assertThat(my("h", 1, "d", "u", "p", mode, "/ca.crt").sslMode()).isEqualTo(mode);
        }

        @Test
        @DisplayName("rejects an unknown sslMode, listing the valid ones")
        void rejectsUnknownSslMode() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> my("h", 1, "d", "u", "p", "prefer", ""))
                    .withMessage(
                            "storage.mysql.ssl-mode must be one of disable, trust, verify-ca,"
                                    + " verify-full, was 'prefer'");
        }

        private static StorageSettings.MySql my(
                String host,
                int port,
                String database,
                String user,
                String password,
                String sslMode,
                String sslCaCert) {
            return new StorageSettings.MySql(
                    host, port, database, user, password, sslMode, sslCaCert);
        }
    }

    @Nested
    @DisplayName("StorageType")
    class Type {

        @ParameterizedTest
        @ValueSource(strings = {"sqlite", "SQLite", " sqlite "})
        @DisplayName("parses sqlite")
        void parsesSqlite(String token) {
            assertThat(StorageType.fromToken(token)).isEqualTo(StorageType.SQLITE);
            assertThat(StorageType.SQLITE.token()).isEqualTo("sqlite");
        }

        @ParameterizedTest
        @ValueSource(strings = {"postgres", "postgresql", "POSTGRES"})
        @DisplayName("parses postgres and postgresql")
        void parsesPostgres(String token) {
            assertThat(StorageType.fromToken(token)).isEqualTo(StorageType.POSTGRES);
            assertThat(StorageType.POSTGRES.token()).isEqualTo("postgres");
        }

        @ParameterizedTest
        @ValueSource(strings = {"mysql", "MySQL", "mariadb", " MariaDB "})
        @DisplayName("parses mysql and mariadb")
        void parsesMySql(String token) {
            assertThat(StorageType.fromToken(token)).isEqualTo(StorageType.MYSQL);
            assertThat(StorageType.MYSQL.token()).isEqualTo("mysql");
        }

        @Test
        @DisplayName("rejects null and unknown tokens")
        void rejectsUnknown() {
            assertThatIllegalArgumentException().isThrownBy(() -> StorageType.fromToken(null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StorageType.fromToken("h2"))
                    .withMessage(
                            "unknown storage.type 'h2' (expected 'sqlite', 'postgres' or 'mysql')");
        }
    }
}

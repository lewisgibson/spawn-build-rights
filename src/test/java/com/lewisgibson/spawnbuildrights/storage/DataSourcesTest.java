package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What each driver is handed. The drivers themselves are run against real servers, TLS included, in
 * the server tests.
 */
@DisplayName("DataSources")
class DataSourcesTest {

    @Nested
    @DisplayName("PostgreSQL")
    class Postgres {

        private final StorageSettings.Postgres plain =
                new StorageSettings.Postgres("db", 5433, "sbr", "u", "secret", "", "prefer", "");
        private final StorageSettings.Postgres full =
                new StorageSettings.Postgres(
                        "db", 5433, "sbr", "u", "secret", "plugins", "verify-full", "/ca.crt");

        @Test
        @DisplayName("uses the PostgreSQL driver")
        void driver() {
            assertThat(DataSources.postgres(plain))
                    .isInstanceOfSatisfying(
                            DriverDataSource.class,
                            source ->
                                    assertThat(source.driver())
                                            .isInstanceOf(org.postgresql.Driver.class));
        }

        @Test
        @DisplayName(
                "hands it the URL, credentials, timeouts and sslmode, and the rest only when set")
        void properties() {
            DriverDataSource source = DataSources.postgres(plain, FakeDriver.of((u, p) -> null));
            assertThat(source.url()).isEqualTo("jdbc:postgresql://db:5433/sbr");
            assertThat(source.properties())
                    .containsEntry("user", "u")
                    .containsEntry("password", "secret")
                    .containsEntry("ApplicationName", "SpawnBuildRights")
                    .containsEntry("connectTimeout", "10")
                    .containsEntry("socketTimeout", "30")
                    .containsEntry("sslmode", "prefer")
                    .doesNotContainKey("currentSchema")
                    .doesNotContainKey("sslrootcert");
            assertThat(DataSources.postgres(full, FakeDriver.of((u, p) -> null)).properties())
                    .containsEntry("currentSchema", "plugins")
                    .containsEntry("sslmode", "verify-full")
                    .containsEntry("sslrootcert", "/ca.crt");
        }

        @Test
        @DisplayName("describes the target without the password, with the schema only when set")
        void description() {
            assertThat(DataSources.describe(plain))
                    .isEqualTo("postgres u@db:5433/sbr sslmode prefer")
                    .doesNotContain("secret");
            assertThat(DataSources.describe(full))
                    .isEqualTo("postgres u@db:5433/sbr schema plugins sslmode verify-full");
        }
    }

    @Nested
    @DisplayName("MySQL")
    class MySql {

        private final StorageSettings.MySql plain =
                new StorageSettings.MySql("db", 3307, "sbr", "u", "secret", "disable", "");
        private final StorageSettings.MySql verified =
                new StorageSettings.MySql(
                        "db", 3307, "sbr", "u", "secret", "verify-full", "/ca.crt");

        @Test
        @DisplayName("uses the MariaDB driver")
        void driver() {
            assertThat(DataSources.mysql(plain))
                    .isInstanceOfSatisfying(
                            DriverDataSource.class,
                            source ->
                                    assertThat(source.driver())
                                            .isInstanceOf(org.mariadb.jdbc.Driver.class));
        }

        @Test
        @DisplayName(
                "allows the server's RSA key only without TLS, and passes the CA only when set")
        void properties() {
            DriverDataSource source = DataSources.mysql(plain, FakeDriver.of((u, p) -> null));
            assertThat(source.url()).isEqualTo("jdbc:mariadb://db:3307/sbr");
            assertThat(source.properties())
                    .containsEntry("user", "u")
                    .containsEntry("password", "secret")
                    .containsEntry("connectTimeout", "10000")
                    .containsEntry("socketTimeout", "30000")
                    .containsEntry("sslMode", "disable")
                    .containsEntry("allowPublicKeyRetrieval", "true")
                    .doesNotContainKey("serverSslCert");
            assertThat(DataSources.mysql(verified, FakeDriver.of((u, p) -> null)).properties())
                    .containsEntry("sslMode", "verify-full")
                    .containsEntry("serverSslCert", "/ca.crt")
                    .doesNotContainKey("allowPublicKeyRetrieval");
        }

        @Test
        @DisplayName("describes the target without the password")
        void description() {
            assertThat(DataSources.describe(plain))
                    .isEqualTo("mysql u@db:3307/sbr sslmode disable")
                    .doesNotContain("secret");
        }
    }

    @Test
    @DisplayName("rejects null settings")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> DataSources.describe((StorageSettings.Postgres) null));
        assertThatNullPointerException()
                .isThrownBy(() -> DataSources.describe((StorageSettings.MySql) null));
    }
}

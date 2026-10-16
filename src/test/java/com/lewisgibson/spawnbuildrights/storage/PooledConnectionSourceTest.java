package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PooledConnectionSource")
class PooledConnectionSourceTest {

    @TempDir Path directory;

    private DataSource sqlite() {
        return new DriverDataSource(
                new org.sqlite.JDBC(),
                "jdbc:sqlite:" + directory.resolve("pool.db"),
                new Properties());
    }

    @Test
    @DisplayName("hands out working connections, and closes")
    void pools() throws SQLException {
        PooledConnectionSource source =
                PooledConnectionSource.start(Dialect.MYSQL, "a server", sqlite());
        assertThat(source.dialect()).isEqualTo(Dialect.MYSQL);
        assertThat(source.description()).isEqualTo("a server");
        try (Connection connection = source.open()) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(connection.getAutoCommit()).isTrue();
        }
        source.close();
        source.close();
        assertThatThrownBy(source::open).isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("fails to start when the first connection fails, with the driver's reason")
    void unreachable() {
        DataSource refused =
                new DriverDataSource(
                        FakeDriver.of(
                                (url, properties) -> {
                                    throw new SQLException("Connection refused");
                                }),
                        "jdbc:x:y",
                        new Properties());
        assertThatThrownBy(
                        () ->
                                PooledConnectionSource.start(
                                        Dialect.POSTGRES, "postgres u@db", refused))
                .isInstanceOf(StorageException.class)
                .hasMessage("could not connect (postgres u@db): Connection refused");
    }

    @Test
    @DisplayName("is small, recycles connections and keeps an idle one alive")
    void settings() {
        assertThat(PooledConnectionSource.MAXIMUM_POOL_SIZE).isEqualTo(2);
        assertThat(PooledConnectionSource.MAX_LIFETIME).hasMinutes(30);
        assertThat(PooledConnectionSource.KEEPALIVE).hasMinutes(5);
        assertThat(PooledConnectionSource.CONNECTION_TIMEOUT).hasSeconds(10);
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> PooledConnectionSource.start(null, "d", sqlite()));
        assertThatNullPointerException()
                .isThrownBy(() -> PooledConnectionSource.start(Dialect.SQLITE, null, sqlite()));
        assertThatNullPointerException()
                .isThrownBy(() -> PooledConnectionSource.start(Dialect.SQLITE, "d", null));
    }
}

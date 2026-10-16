package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DriverDataSource")
class DriverDataSourceTest {

    private static final Connection CONNECTION =
            (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> null);

    @Test
    @DisplayName("connects through the driver with its URL and a copy of the properties")
    void connects() throws SQLException {
        List<String> urls = new ArrayList<>();
        List<Properties> seen = new ArrayList<>();
        Properties properties = new Properties();
        properties.setProperty("user", "u");
        DriverDataSource source =
                new DriverDataSource(
                        FakeDriver.of(
                                (url, given) -> {
                                    urls.add(url);
                                    seen.add(given);
                                    return CONNECTION;
                                }),
                        "jdbc:test://db",
                        properties);
        properties.setProperty("user", "changed");

        assertThat(source.getConnection()).isSameAs(CONNECTION);
        assertThat(urls).containsExactly("jdbc:test://db");
        assertThat(seen.get(0)).containsEntry("user", "u");
        assertThat(source.properties()).containsEntry("user", "u");
    }

    @Test
    @DisplayName("a driver that declines the URL is a SQLException, not a null connection")
    void declined() {
        DriverDataSource source =
                new DriverDataSource(
                        FakeDriver.of((url, given) -> null), "jdbc:x:y", new Properties());
        assertThatThrownBy(source::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessage("the driver did not accept jdbc:x:y");
    }

    @Test
    @DisplayName("takes credentials only from the properties")
    void noSeparateCredentials() {
        DriverDataSource source = source();
        assertThatThrownBy(() -> source.getConnection("u", "p"))
                .isInstanceOf(SQLFeatureNotSupportedException.class);
    }

    @Test
    @DisplayName("ignores the log writer and the login timeout, and has no parent logger")
    void unusedParts() {
        DriverDataSource source = source();
        source.setLogWriter(new PrintWriter(new StringWriter()));
        source.setLoginTimeout(30);
        assertThat(source.getLogWriter()).isNull();
        assertThat(source.getLoginTimeout()).isZero();
        assertThatThrownBy(source::getParentLogger)
                .isInstanceOf(SQLFeatureNotSupportedException.class);
    }

    @Test
    @DisplayName("unwraps to itself and nothing else")
    void unwraps() throws SQLException {
        DriverDataSource source = source();
        assertThat(source.isWrapperFor(DataSource.class)).isTrue();
        assertThat(source.isWrapperFor(Connection.class)).isFalse();
        assertThat(source.unwrap(DataSource.class)).isSameAs(source);
        assertThatThrownBy(() -> source.unwrap(Connection.class))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("java.sql.Connection");
    }

    @Test
    @DisplayName("prints the URL and never the password")
    void hidesPassword() {
        Properties properties = new Properties();
        properties.setProperty("password", "secret");
        assertThat(
                        new DriverDataSource(
                                FakeDriver.of((url, given) -> null), "jdbc:x:y", properties))
                .hasToString("DriverDataSource[jdbc:x:y]");
    }

    @Test
    @DisplayName("rejects null parts")
    void rejectsNulls() {
        Driver driver = FakeDriver.of((url, given) -> null);
        assertThatNullPointerException()
                .isThrownBy(() -> new DriverDataSource(null, "u", new Properties()));
        assertThatNullPointerException()
                .isThrownBy(() -> new DriverDataSource(driver, null, new Properties()));
        assertThatNullPointerException().isThrownBy(() -> new DriverDataSource(driver, "u", null));
    }

    private static DriverDataSource source() {
        return new DriverDataSource(
                FakeDriver.of((url, given) -> CONNECTION), "jdbc:x:y", new Properties());
    }
}

package com.lewisgibson.spawnbuildrights.storage;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A {@link DataSource} over one driver instance, a URL and its properties, which the connection
 * pool draws from.
 *
 * <p>The driver is handed over as an instance rather than found by class name through {@code
 * DriverManager} or a pool's {@code driverClassName}, because a driver the server loaded for this
 * plugin alone is not visible to either lookup.
 *
 * @param driver the JDBC driver
 * @param url the JDBC URL
 * @param properties the connection properties, credentials included; copied
 */
record DriverDataSource(Driver driver, String url, Properties properties) implements DataSource {

    DriverDataSource {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(url, "url");
        Properties copy = new Properties();
        copy.putAll(Objects.requireNonNull(properties, "properties"));
        properties = copy;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new SQLException("the driver did not accept " + url);
        }
        return connection;
    }

    /** Credentials come from the properties; a pool never asks for them separately. */
    @Override
    public Connection getConnection(String username, String password)
            throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("credentials are set in the properties");
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    /** Ignored: the drivers log through their own loggers. */
    @Override
    public void setLogWriter(PrintWriter out) {}

    /** Ignored: each driver's own connect timeout is set in the properties. */
    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("no parent logger");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException("not a wrapper for " + type.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }

    /** Leaves the password out, so a logged or printed data source never shows it. */
    @Override
    public String toString() {
        return "DriverDataSource[" + url + "]";
    }
}

package com.lewisgibson.spawnbuildrights.storage;

import com.lewisgibson.spawnbuildrights.core.settings.StorageSettings;
import java.sql.Driver;
import java.util.Objects;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * Turns the PostgreSQL and MySQL settings into a driver, a URL and connection properties, and into
 * the line the plugin logs about where its store is. Both drivers are given a connect timeout and a
 * read timeout, so a database that stops answering fails a write instead of holding the storage
 * thread for good.
 */
public final class DataSources {

    private static final String APPLICATION_NAME = "SpawnBuildRights";

    private DataSources() {}

    /**
     * @param settings the PostgreSQL connection details
     * @return a data source over the PostgreSQL driver
     */
    public static DataSource postgres(StorageSettings.Postgres settings) {
        return postgres(settings, new org.postgresql.Driver());
    }

    static DriverDataSource postgres(StorageSettings.Postgres settings, Driver driver) {
        Properties properties = new Properties();
        properties.setProperty("user", settings.user());
        properties.setProperty("password", settings.password());
        properties.setProperty("ApplicationName", APPLICATION_NAME);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "30");
        properties.setProperty("sslmode", settings.sslMode());
        if (!settings.schema().isBlank()) {
            properties.setProperty("currentSchema", settings.schema());
        }
        if (!settings.sslRootCert().isBlank()) {
            properties.setProperty("sslrootcert", settings.sslRootCert());
        }
        return new DriverDataSource(
                driver,
                "jdbc:postgresql://"
                        + settings.host()
                        + ":"
                        + settings.port()
                        + "/"
                        + settings.database(),
                properties);
    }

    /**
     * @param settings the PostgreSQL connection details
     * @return where they point, without the password
     */
    public static String describe(StorageSettings.Postgres settings) {
        Objects.requireNonNull(settings, "settings");
        return "postgres "
                + settings.user()
                + "@"
                + settings.host()
                + ":"
                + settings.port()
                + "/"
                + settings.database()
                + (settings.schema().isBlank() ? "" : " schema " + settings.schema())
                + " sslmode "
                + settings.sslMode();
    }

    /**
     * @param settings the MySQL or MariaDB connection details
     * @return a data source over the MariaDB driver, which talks to either server
     */
    public static DataSource mysql(StorageSettings.MySql settings) {
        return mysql(settings, new org.mariadb.jdbc.Driver());
    }

    static DriverDataSource mysql(StorageSettings.MySql settings, Driver driver) {
        Properties properties = new Properties();
        properties.setProperty("user", settings.user());
        properties.setProperty("password", settings.password());
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "30000");
        properties.setProperty("sslMode", settings.sslMode());
        if (settings.sslMode().equals("disable")) {
            // MySQL 8's default login (caching_sha2_password) sends the password encrypted with
            // the server's RSA key when the connection has no TLS, and this lets the driver ask
            // the server for that key. With TLS on, it is not needed.
            properties.setProperty("allowPublicKeyRetrieval", "true");
        }
        if (!settings.sslCaCert().isBlank()) {
            properties.setProperty("serverSslCert", settings.sslCaCert());
        }
        return new DriverDataSource(
                driver,
                "jdbc:mariadb://"
                        + settings.host()
                        + ":"
                        + settings.port()
                        + "/"
                        + settings.database(),
                properties);
    }

    /**
     * @param settings the MySQL or MariaDB connection details
     * @return where they point, without the password
     */
    public static String describe(StorageSettings.MySql settings) {
        Objects.requireNonNull(settings, "settings");
        return "mysql "
                + settings.user()
                + "@"
                + settings.host()
                + ":"
                + settings.port()
                + "/"
                + settings.database()
                + " sslmode "
                + settings.sslMode();
    }
}

package com.lewisgibson.spawnbuildrights.core.settings;

import java.util.List;
import java.util.Objects;

/**
 * Where the store lives. Only the part matching {@code type} is used, but every part is always
 * present so switching between them is a one-key edit.
 *
 * @param type the backend
 * @param sqliteFile the SQLite file name, relative to the plugin's data folder
 * @param postgres the PostgreSQL connection details
 * @param mysql the MySQL or MariaDB connection details
 */
public record StorageSettings(StorageType type, String sqliteFile, Postgres postgres, MySql mysql) {

    /**
     * PostgreSQL connection details.
     *
     * @param host the server host
     * @param port the server port, 1 to 65535
     * @param database the database name
     * @param user the role to connect as
     * @param password its password, possibly empty
     * @param schema the schema to create the tables in; blank means the role's default
     * @param sslMode the PostgreSQL driver's {@code sslmode}, one of {@link #SSL_MODES}
     * @param sslRootCert the CA certificate file {@code verify-ca} and {@code verify-full} check
     *     the server against; blank means the driver's default
     */
    public record Postgres(
            String host,
            int port,
            String database,
            String user,
            String password,
            String schema,
            String sslMode,
            String sslRootCert) {

        /** The {@code sslmode} values the PostgreSQL JDBC driver accepts, weakest first. */
        public static final List<String> SSL_MODES =
                List.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

        public Postgres {
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(sslRootCert, "sslRootCert");
            requireServer("storage.postgres", host, port, database, user);
            requireOneOf("storage.postgres.ssl-mode", sslMode, SSL_MODES);
        }
    }

    /**
     * MySQL or MariaDB connection details. Both are reached through the MariaDB driver, which
     * supports either server.
     *
     * @param host the server host
     * @param port the server port, 1 to 65535
     * @param database the database name
     * @param user the user to connect as
     * @param password its password, possibly empty
     * @param sslMode the MariaDB driver's {@code sslMode}, one of {@link #SSL_MODES}
     * @param sslCaCert the CA certificate file {@code verify-ca} and {@code verify-full} check the
     *     server against; blank means the Java trust store
     */
    public record MySql(
            String host,
            int port,
            String database,
            String user,
            String password,
            String sslMode,
            String sslCaCert) {

        /** The {@code sslMode} values the MariaDB driver accepts, weakest first. */
        public static final List<String> SSL_MODES =
                List.of("disable", "trust", "verify-ca", "verify-full");

        public MySql {
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(sslCaCert, "sslCaCert");
            requireServer("storage.mysql", host, port, database, user);
            requireOneOf("storage.mysql.ssl-mode", sslMode, SSL_MODES);
        }
    }

    public StorageSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sqliteFile, "sqliteFile");
        Objects.requireNonNull(postgres, "postgres");
        Objects.requireNonNull(mysql, "mysql");
        if (sqliteFile.isBlank()) {
            throw new IllegalArgumentException("storage.sqlite.file must not be blank");
        }
    }

    /**
     * @return the zero-configuration default: SQLite in {@code data.db}
     */
    public static StorageSettings sqliteDefault() {
        return new StorageSettings(
                StorageType.SQLITE,
                "data.db",
                new Postgres(
                        "localhost",
                        5432,
                        "spawnbuildrights",
                        "spawnbuildrights",
                        "",
                        "",
                        "prefer",
                        ""),
                new MySql(
                        "localhost",
                        3306,
                        "spawnbuildrights",
                        "spawnbuildrights",
                        "",
                        "disable",
                        ""));
    }

    private static void requireServer(
            String prefix, String host, int port, String database, String user) {
        requireNotBlank(host, prefix + ".host");
        requireNotBlank(database, prefix + ".database");
        requireNotBlank(user, prefix + ".user");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(prefix + ".port must be 1-65535, was " + port);
        }
    }

    private static void requireNotBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireOneOf(String name, String value, List<String> allowed) {
        Objects.requireNonNull(value, name);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(
                    name
                            + " must be one of "
                            + String.join(", ", allowed)
                            + ", was '"
                            + value
                            + "'");
        }
    }
}

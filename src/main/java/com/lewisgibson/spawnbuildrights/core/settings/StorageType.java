package com.lewisgibson.spawnbuildrights.core.settings;

import java.util.Locale;

/** Which database the store lives in. */
public enum StorageType {

    /** One file under the plugin's data folder. The default; needs no configuration. */
    SQLITE,

    /** A PostgreSQL database. */
    POSTGRES,

    /** A MySQL or MariaDB database. */
    MYSQL;

    /**
     * @param value the configured token, case-insensitive: {@code sqlite}, {@code postgres} or
     *     {@code postgresql}, {@code mysql} or {@code mariadb}
     * @return the matching type
     * @throws IllegalArgumentException if {@code value} is null or unknown
     */
    public static StorageType fromToken(String value) {
        if (value == null) {
            throw new IllegalArgumentException("storage.type must not be null");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "postgres", "postgresql" -> POSTGRES;
            case "mysql", "mariadb" -> MYSQL;
            default ->
                    throw new IllegalArgumentException(
                            "unknown storage.type '"
                                    + value
                                    + "' (expected 'sqlite', 'postgres' or 'mysql')");
        };
    }

    /**
     * @return the lower-case token used in configuration
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }
}

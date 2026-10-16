package com.lewisgibson.spawnbuildrights.storage;

/**
 * The SQL that differs between the databases the store supports. Everything else the store runs is
 * the same text on all of them.
 *
 * <p>SQLite (3.24 or newer) and PostgreSQL (9.5 or newer) share {@code INSERT ... ON CONFLICT DO
 * UPDATE}. MySQL and MariaDB use {@code ON DUPLICATE KEY UPDATE} with {@code VALUES()}, which MySQL
 * 8.4 still accepts and which is the only form both servers run. {@code REPLACE} is not used
 * because it deletes the row and inserts a new one.
 */
public enum Dialect {

    /** SQLite. */
    SQLITE,

    /** PostgreSQL. */
    POSTGRES,

    /** MySQL and MariaDB. */
    MYSQL;

    private static final String UPSERT_OVERRIDE =
            "INSERT INTO sbr_overrides"
                    + " (player_uuid, player_name, state, actor, reason, at_millis)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";

    /**
     * @return what follows the closing parenthesis of a {@code CREATE TABLE}. On MySQL that is a
     *     case-sensitive UTF-8 collation, so names keep their case and any character fits.
     */
    String tableOptions() {
        return this == MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin" : "";
    }

    /**
     * @param name the column name
     * @return a primary key the database numbers itself, in the order rows are inserted, which
     *     orders events written in the same millisecond
     */
    String generatedIdColumn(String name) {
        return switch (this) {
            case SQLITE -> name + " INTEGER PRIMARY KEY";
            case POSTGRES -> name + " BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
            case MYSQL -> name + " BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        };
    }

    /**
     * @return the insert-or-replace for one override, its six columns bound in table order
     */
    String upsertOverride() {
        return switch (this) {
            case SQLITE, POSTGRES ->
                    UPSERT_OVERRIDE
                            + " ON CONFLICT (player_uuid) DO UPDATE SET"
                            + " player_name = excluded.player_name, state = excluded.state,"
                            + " actor = excluded.actor, reason = excluded.reason,"
                            + " at_millis = excluded.at_millis";
            case MYSQL ->
                    UPSERT_OVERRIDE
                            + " ON DUPLICATE KEY UPDATE"
                            + " player_name = VALUES(player_name), state = VALUES(state),"
                            + " actor = VALUES(actor), reason = VALUES(reason),"
                            + " at_millis = VALUES(at_millis)";
        };
    }
}

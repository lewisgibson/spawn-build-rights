package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SQLite text runs in the store tests; the PostgreSQL and MySQL text runs against real servers
 * in the server tests. Here each is pinned down as written.
 */
@DisplayName("Dialect")
class DialectTest {

    @Test
    @DisplayName("SQLite and PostgreSQL share an ON CONFLICT upsert")
    void onConflict() {
        assertThat(Dialect.SQLITE.upsertOverride())
                .isEqualTo(Dialect.POSTGRES.upsertOverride())
                .isEqualTo(
                        "INSERT INTO sbr_overrides (player_uuid, player_name, state, actor,"
                                + " reason, at_millis) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT"
                                + " (player_uuid) DO UPDATE SET player_name ="
                                + " excluded.player_name, state = excluded.state, actor ="
                                + " excluded.actor, reason = excluded.reason, at_millis ="
                                + " excluded.at_millis");
    }

    @Test
    @DisplayName("MySQL uses ON DUPLICATE KEY UPDATE, never REPLACE")
    void onDuplicateKey() {
        assertThat(Dialect.MYSQL.upsertOverride())
                .isEqualTo(
                        "INSERT INTO sbr_overrides (player_uuid, player_name, state, actor,"
                                + " reason, at_millis) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY"
                                + " UPDATE player_name = VALUES(player_name), state ="
                                + " VALUES(state), actor = VALUES(actor), reason ="
                                + " VALUES(reason), at_millis = VALUES(at_millis)");
    }

    @Test
    @DisplayName("each numbers its own ids")
    void generatedIds() {
        assertThat(Dialect.SQLITE.generatedIdColumn("id")).isEqualTo("id INTEGER PRIMARY KEY");
        assertThat(Dialect.POSTGRES.generatedIdColumn("id"))
                .isEqualTo("id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY");
        assertThat(Dialect.MYSQL.generatedIdColumn("id"))
                .isEqualTo("id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
    }

    @Test
    @DisplayName("only MySQL tables carry options")
    void tableOptions() {
        assertThat(Dialect.SQLITE.tableOptions()).isEmpty();
        assertThat(Dialect.POSTGRES.tableOptions()).isEmpty();
        assertThat(Dialect.MYSQL.tableOptions())
                .isEqualTo(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
    }
}

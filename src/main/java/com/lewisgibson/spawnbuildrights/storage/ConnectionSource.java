package com.lewisgibson.spawnbuildrights.storage;

import java.sql.Connection;
import java.sql.SQLException;

/** Where the store gets its connections, and which SQL dialect they speak. */
public interface ConnectionSource extends AutoCloseable {

    /**
     * @return an open connection in auto-commit mode; the caller closes it
     * @throws SQLException if the database cannot be reached
     */
    Connection open() throws SQLException;

    /**
     * @return the database's dialect
     */
    Dialect dialect();

    /**
     * @return where this source connects to, for log lines and error messages (never a password)
     */
    String description();

    /** Releases what the source holds open. Idempotent. */
    @Override
    void close();
}

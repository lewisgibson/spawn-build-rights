/**
 * The JDBC implementation of the store: one store class over SQLite, PostgreSQL or MySQL and
 * MariaDB, the SQL that differs between them, the numbered migrations that build its tables, and
 * the single thread that runs writes off the server thread. Nothing in this package imports a
 * server type.
 */
package com.lewisgibson.spawnbuildrights.storage;

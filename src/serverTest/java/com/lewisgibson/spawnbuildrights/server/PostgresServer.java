package com.lewisgibson.spawnbuildrights.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A throwaway PostgreSQL in Docker, reachable from the host and from other containers, optionally
 * with TLS required on every TCP connection.
 */
final class PostgresServer implements AutoCloseable {

    static final String IMAGE = "postgres:16.15-alpine";
    static final String USER = "sbr";
    static final String PASSWORD = "sbr-test";
    static final String DATABASE = "sbr";

    private String container;
    private Path caCertificate;

    void start() {
        container =
                Docker.start(
                        List.of(
                                "-e",
                                "POSTGRES_USER=" + USER,
                                "-e",
                                "POSTGRES_PASSWORD=" + PASSWORD,
                                "-e",
                                "POSTGRES_DB=" + DATABASE,
                                "-p",
                                "127.0.0.1::5432",
                                IMAGE));
        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(deadline)) {
            if (Docker.execExitCode(container, "pg_isready", "-U", USER, "-d", DATABASE) == 0) {
                // pg_isready can answer before the init script's restart; one more beat is cheap.
                Docker.sleep(Duration.ofSeconds(2));
                return;
            }
            Docker.sleep(Duration.ofSeconds(1));
        }
        throw new IllegalStateException(
                "PostgreSQL never became ready:\n" + Docker.tail(Docker.logs(container)));
    }

    /**
     * Turns TLS on with a certificate signed by a throwaway CA, valid for 127.0.0.1 and the
     * container's bridge address, and rejects every TCP connection that is not TLS, so a client
     * that connects at all has proved it spoke TLS.
     *
     * @param directory where the CA and server keys are written on the host
     */
    void requireTls(Path directory) {
        try {
            Files.createDirectories(directory);
            Path caKey = directory.resolve("ca.key");
            Path ca = directory.resolve("ca.crt");
            Path key = directory.resolve("server.key");
            Path request = directory.resolve("server.csr");
            Path certificate = directory.resolve("server.crt");
            Path extensions = directory.resolve("server.ext");
            Files.writeString(
                    extensions, "subjectAltName=IP:127.0.0.1,IP:" + bridgeAddress() + "\n");
            Docker.require(
                    "openssl CA",
                    "openssl",
                    "req",
                    "-x509",
                    "-newkey",
                    "rsa:2048",
                    "-nodes",
                    "-days",
                    "2",
                    "-subj",
                    "/CN=SpawnBuildRights test CA",
                    "-keyout",
                    caKey.toString(),
                    "-out",
                    ca.toString());
            Docker.require(
                    "openssl server key",
                    "openssl",
                    "req",
                    "-newkey",
                    "rsa:2048",
                    "-nodes",
                    "-subj",
                    "/CN=postgres",
                    "-keyout",
                    key.toString(),
                    "-out",
                    request.toString());
            Docker.require(
                    "openssl sign",
                    "openssl",
                    "x509",
                    "-req",
                    "-in",
                    request.toString(),
                    "-CA",
                    ca.toString(),
                    "-CAkey",
                    caKey.toString(),
                    "-CAcreateserial",
                    "-days",
                    "2",
                    "-extfile",
                    extensions.toString(),
                    "-out",
                    certificate.toString());
            Docker.copyInto(container, certificate, "/var/lib/postgresql/server.crt");
            Docker.copyInto(container, key, "/var/lib/postgresql/server.key");
            Docker.require(
                    "TLS setup",
                    "docker",
                    "exec",
                    container,
                    "sh",
                    "-c",
                    "chown postgres:postgres /var/lib/postgresql/server.*"
                            + " && chmod 600 /var/lib/postgresql/server.key"
                            + " && printf 'local all all trust\\n"
                            + "hostssl all all all scram-sha-256\\n"
                            + "hostnossl all all all reject\\n' > \"$PGDATA/pg_hba.conf\""
                            + " && psql -U "
                            + USER
                            + " -d "
                            + DATABASE
                            + " -v ON_ERROR_STOP=1"
                            + " -c \"ALTER SYSTEM SET ssl_cert_file = '/var/lib/postgresql/server.crt'\""
                            + " -c \"ALTER SYSTEM SET ssl_key_file = '/var/lib/postgresql/server.key'\""
                            + " -c \"ALTER SYSTEM SET ssl = on\""
                            + " -c \"SELECT pg_reload_conf()\"");
            Docker.sleep(Duration.ofSeconds(2));
            caCertificate = ca;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** The throwaway CA's certificate on the host, after {@link #requireTls}. */
    Path caCertificate() {
        return caCertificate;
    }

    /** The port on 127.0.0.1 the harness itself connects to. */
    int hostPort() {
        return Docker.hostPort(container, 5432);
    }

    /** The address the Paper container connects to. */
    String bridgeAddress() {
        return Docker.bridgeAddress(container);
    }

    /** Runs SQL inside the container over the local socket and returns psql's unaligned output. */
    String query(String sql) {
        return Docker.require(
                "psql", "docker", "exec", container, "psql", "-U", USER, "-d", DATABASE, "-At",
                "-c", sql);
    }

    @Override
    public void close() {
        if (container != null) {
            Docker.stop(container);
        }
    }
}

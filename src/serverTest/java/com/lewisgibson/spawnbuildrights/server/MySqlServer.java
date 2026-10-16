package com.lewisgibson.spawnbuildrights.server;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A throwaway MariaDB or MySQL in Docker, reachable from the host and from other containers. Both
 * images turn TLS on with a generated certificate, so a client can ask for TLS without any setup.
 */
final class MySqlServer implements AutoCloseable {

    /** Which server, by its pinned image and its command-line client. */
    enum Flavour {
        MARIADB("mariadb:11.8.9", "mariadb", "MARIADB"),
        MYSQL("mysql:8.4.11", "mysql", "MYSQL");

        final String image;
        final String client;
        final String environmentPrefix;

        Flavour(String image, String client, String environmentPrefix) {
            this.image = image;
            this.client = client;
            this.environmentPrefix = environmentPrefix;
        }
    }

    static final String USER = "sbr";
    static final String PASSWORD = "sbr-test";
    static final String DATABASE = "sbr";

    private final Flavour flavour;
    private String container;

    MySqlServer(Flavour flavour) {
        this.flavour = flavour;
    }

    void start() {
        String prefix = flavour.environmentPrefix;
        container =
                Docker.start(
                        List.of(
                                "-e",
                                prefix + "_ROOT_PASSWORD=" + PASSWORD,
                                "-e",
                                prefix + "_DATABASE=" + DATABASE,
                                "-e",
                                prefix + "_USER=" + USER,
                                "-e",
                                prefix + "_PASSWORD=" + PASSWORD,
                                "-p",
                                "127.0.0.1::3306",
                                flavour.image));
        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(deadline)) {
            // Over TCP, which the image's first-run setup server does not listen on, so a
            // successful query means the real server is up.
            if (Docker.execExitCode(
                            container,
                            flavour.client,
                            "--protocol=tcp",
                            "-h127.0.0.1",
                            "-uroot",
                            "-p" + PASSWORD,
                            "-e",
                            "SELECT 1")
                    == 0) {
                return;
            }
            Docker.sleep(Duration.ofSeconds(1));
        }
        throw new IllegalStateException(
                flavour + " never became ready:\n" + Docker.tail(Docker.logs(container)));
    }

    /** The port on 127.0.0.1 the harness itself connects to. */
    int hostPort() {
        return Docker.hostPort(container, 3306);
    }

    /** The address the Paper container connects to. */
    String bridgeAddress() {
        return Docker.bridgeAddress(container);
    }

    /** Runs SQL as root inside the container and returns the tab-separated rows. */
    String query(String sql) {
        return Docker.require(
                flavour.client,
                "docker",
                "exec",
                container,
                flavour.client,
                "-uroot",
                "-p" + PASSWORD,
                "-N",
                "-B",
                DATABASE,
                "-e",
                sql);
    }

    @Override
    public void close() {
        if (container != null) {
            Docker.stop(container);
        }
    }
}

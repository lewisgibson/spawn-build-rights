package com.lewisgibson.spawnbuildrights.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * One throwaway Paper 26.3 server in Docker with the built plugin and the fixture installed. The
 * image is itzg/minecraft-server, pinned by digest, on a small heap; the first run downloads the
 * image, the Paper jar and the plugin's JDBC libraries, so it is slow once and quick after.
 */
final class PaperServer implements AutoCloseable {

    static final String IMAGE =
            "itzg/minecraft-server:java25@sha256:5cf064b103ea7470c8ec6d90ad9a602863fea8fb5dfd4e3a0224a19d55e9e50d";
    static final String RCON_PASSWORD = "server-test";

    private final Path data;
    private String container;
    private int rconPort;

    PaperServer(Path data) {
        this.data = data;
    }

    /**
     * Writes the plugin config the test wants and installs both jars, before the server starts. The
     * data folder is kept between runs so the downloads are cached, but the world and the plugin's
     * own data are removed first: every scenario starts from a fresh world and an empty store, as
     * it does on CI.
     */
    void install(Path pluginJar, Path fixtureJar, String pluginConfigYaml) throws IOException {
        Path plugins = data.resolve("plugins");
        deleteRecursively(data.resolve("world"));
        deleteRecursively(plugins.resolve("SpawnBuildRights"));
        Files.createDirectories(plugins.resolve("SpawnBuildRights"));
        // A jar from an earlier version is still there, and Paper would load both.
        try (var previous = Files.newDirectoryStream(plugins, "SpawnBuildRights*.jar")) {
            for (Path jar : previous) {
                Files.delete(jar);
            }
        }
        Files.copy(
                pluginJar,
                plugins.resolve(pluginJar.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(
                fixtureJar,
                plugins.resolve(fixtureJar.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(plugins.resolve("SpawnBuildRights/config.yml"), pluginConfigYaml);
    }

    /**
     * Boots as the bind mount's owner so server-created files remain writable for reload tests,
     * including on CI hosts whose UID differs from the container image default.
     */
    String start() throws IOException {
        container =
                Docker.start(
                        List.of(
                                "-e",
                                "EULA=TRUE",
                                "-e",
                                "UID=" + Files.getAttribute(data, "unix:uid"),
                                "-e",
                                "GID=" + Files.getAttribute(data, "unix:gid"),
                                "-e",
                                "TYPE=PAPER",
                                "-e",
                                "VERSION=26.3",
                                "-e",
                                "PAPER_BUILD=194",
                                "-e",
                                "ONLINE_MODE=FALSE",
                                "-e",
                                "MEMORY=1G",
                                "-e",
                                "MAX_TICK_TIME=-1",
                                "-e",
                                "SPAWN_PROTECTION=0",
                                "-e",
                                "LEVEL_TYPE=FLAT",
                                "-e",
                                "VIEW_DISTANCE=2",
                                "-e",
                                "ENABLE_RCON=TRUE",
                                "-e",
                                "RCON_PASSWORD=" + RCON_PASSWORD,
                                "-p",
                                "127.0.0.1::25575",
                                "-v",
                                data.toAbsolutePath() + ":/data",
                                IMAGE));
        String logs =
                Docker.awaitLogs(
                        container,
                        text ->
                                text.contains("Done (")
                                        || text.contains(
                                                "Error occurred while enabling SpawnBuildRights"),
                        Duration.ofMinutes(15),
                        "Paper to finish starting");
        rconPort = Docker.hostPort(container, 25575);
        return logs;
    }

    String logs() {
        return Docker.logs(container);
    }

    /**
     * Runs one command over RCON and returns the reply text. Only connecting and logging in are
     * retried: the command itself is sent once, because a retry after it reached the server would
     * run a scenario or a grant twice and report the second run.
     */
    String rcon(String command) throws IOException {
        try (Rcon rcon = connect()) {
            // Replies carry a trailing newline; the tests compare the words.
            return rcon.command(command).trim();
        }
    }

    private Rcon connect() throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                return new Rcon("127.0.0.1", rconPort, RCON_PASSWORD);
            } catch (IOException ex) {
                last = ex;
                Docker.sleep(Duration.ofSeconds(2));
            }
        }
        throw last;
    }

    @Override
    public void close() {
        if (container != null) {
            Docker.stop(container);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}

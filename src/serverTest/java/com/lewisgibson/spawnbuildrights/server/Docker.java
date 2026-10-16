package com.lewisgibson.spawnbuildrights.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** The few docker commands the harness needs, run through the docker CLI on the PATH. */
final class Docker {

    private Docker() {}

    /** Fails loudly, rather than skipping, when Docker is not usable: this suite exists to run. */
    static void requireDocker() {
        Result result =
                run(Duration.ofSeconds(30), "docker", "version", "--format", "{{.Server.Version}}");
        if (result.exitCode != 0) {
            throw new IllegalStateException(
                    "the server tests need a working Docker daemon on the PATH:\n"
                            + result.output());
        }
    }

    /**
     * {@code docker run -d --rm ...}; returns the container id. The id is the last line of STDOUT
     * alone: on a first run the image pull writes its progress to stderr, and merging the two
     * turned the id into a page of text that no later command could use.
     */
    static String start(List<String> runArguments) {
        List<String> command = new ArrayList<>(List.of("docker", "run", "-d", "--rm"));
        command.addAll(runArguments);
        Result result = run(Duration.ofMinutes(10), command.toArray(String[]::new));
        String id = result.stdout.trim().lines().reduce((first, second) -> second).orElse("");
        if (result.exitCode != 0 || !id.matches("[0-9a-f]{12,64}")) {
            throw new IllegalStateException("docker run failed:\n" + result.output());
        }
        return id;
    }

    static void stop(String container) {
        run(Duration.ofMinutes(2), "docker", "stop", "-t", "20", container);
    }

    static String logs(String container) {
        // Paper logs to stdout; the entrypoint's own lines go to stderr. Both are the log.
        return run(Duration.ofMinutes(1), "docker", "logs", container).output();
    }

    static String exec(String container, String... command) {
        List<String> full = new ArrayList<>(List.of("docker", "exec", container));
        full.addAll(List.of(command));
        return run(Duration.ofMinutes(1), full.toArray(String[]::new)).output();
    }

    /** {@code docker cp source container:target}, failing loudly. */
    static void copyInto(String container, java.nio.file.Path source, String target) {
        Result result =
                run(
                        Duration.ofMinutes(1),
                        "docker",
                        "cp",
                        source.toString(),
                        container + ":" + target);
        if (result.exitCode != 0) {
            throw new IllegalStateException("docker cp failed:\n" + result.output());
        }
    }

    /** Runs a host command and fails loudly when it does not exit 0; returns its output. */
    static String require(String what, String... command) {
        Result result = run(Duration.ofMinutes(2), command);
        if (result.exitCode != 0) {
            throw new IllegalStateException(what + " failed:\n" + result.output());
        }
        return result.output();
    }

    static int execExitCode(String container, String... command) {
        List<String> full = new ArrayList<>(List.of("docker", "exec", container));
        full.addAll(List.of(command));
        return run(Duration.ofMinutes(1), full.toArray(String[]::new)).exitCode;
    }

    /** The host port a container port was published to, from {@code docker port}. */
    static int hostPort(String container, int containerPort) {
        Result result =
                run(Duration.ofSeconds(30), "docker", "port", container, containerPort + "/tcp");
        String line = result.stdout.trim().lines().findFirst().orElse("");
        int colon = line.lastIndexOf(':');
        if (result.exitCode != 0 || colon < 0) {
            throw new IllegalStateException("docker port gave no mapping: " + result.output());
        }
        return Integer.parseInt(line.substring(colon + 1).trim());
    }

    /** The container's address on the default bridge network, reachable from other containers. */
    static String bridgeAddress(String container) {
        Result result =
                run(
                        Duration.ofSeconds(30),
                        "docker",
                        "inspect",
                        "-f",
                        "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
                        container);
        String address = result.stdout.trim();
        if (result.exitCode != 0 || address.isEmpty()) {
            throw new IllegalStateException("docker inspect gave no address: " + result.output());
        }
        return address;
    }

    /** Polls the container's logs until {@code ready} matches them, or fails after the timeout. */
    static String awaitLogs(
            String container, Predicate<String> ready, Duration timeout, String what) {
        Instant deadline = Instant.now().plus(timeout);
        String logs = "";
        while (Instant.now().isBefore(deadline)) {
            logs = logs(container);
            if (ready.test(logs)) {
                return logs;
            }
            sleep(Duration.ofSeconds(2));
        }
        throw new IllegalStateException(
                "timed out after "
                        + timeout
                        + " waiting for "
                        + what
                        + "; last log lines:\n"
                        + tail(logs));
    }

    static String tail(String text) {
        List<String> lines = text.lines().toList();
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size()));
    }

    static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }

    /** A finished command: its exit code, its stdout, and its stderr, kept apart. */
    record Result(int exitCode, String stdout, String stderr) {
        /** Both streams, for a message. */
        String output() {
            return stdout + (stderr.isEmpty() ? "" : "\n" + stderr);
        }
    }

    static Result run(Duration timeout, String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            // Drain stderr on its own thread so a chatty command cannot block on a full pipe.
            var stderrBytes = new java.io.ByteArrayOutputStream();
            Thread drain =
                    new Thread(
                            () -> {
                                try (var err = process.getErrorStream()) {
                                    err.transferTo(stderrBytes);
                                } catch (IOException ignored) {
                                    // the process is going away; whatever was read is enough
                                }
                            },
                            "docker-stderr");
            drain.start();
            String stdout;
            try (var in = process.getInputStream()) {
                stdout = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            drain.join(5_000);
            String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
            if (!finished) {
                return new Result(-1, stdout, stderr + "\n(timed out)");
            }
            return new Result(process.exitValue(), stdout, stderr);
        } catch (IOException ex) {
            return new Result(-1, "", ex.toString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", "interrupted");
        }
    }
}

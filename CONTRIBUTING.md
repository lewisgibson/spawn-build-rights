# Contributing

Issues and pull requests are welcome.

## Prerequisites

- Use a recent JDK to run Gradle. The Foojay resolver downloads the Java 25 toolchain automatically, so you do not need to install it yourself.
- Install Docker if you want to run `./gradlew serverTest`, which starts real Paper servers. Building and running the unit and integration tests needs no real server.

## Build and test

| Command                     | What it does                                                                                                                                           |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `./gradlew build`           | Compile, format validation with Spotless, unit and integration tests, and 100% line and branch coverage                                                |
| `./gradlew test`            | Run fast unit tests without a server                                                                                                                   |
| `./gradlew integrationTest` | Run MockBukkit integration tests                                                                                                                       |
| `./gradlew serverTest`      | Start real Paper 26.3 servers and databases in Docker: the protection scenarios, and the store on SQLite, PostgreSQL, MySQL and MariaDB. Takes minutes |
| `./gradlew spotlessApply`   | Format Java with google-java-format using AOSP / 4-space style                                                                                         |
| `./gradlew runServer`       | Start a real Paper server with the plugin installed                                                                                                    |
| `./gradlew jmh`             | Run the hot-path microbenchmarks described in [docs/performance.md](docs/performance.md)                                                               |

Run `./gradlew build` before opening a PR, as CI runs the same command. The unit and integration tests together must cover every line and branch of the plugin, and the build fails if coverage is below 100%. You can find the HTML report in `build/reports/jacoco/`.

Nothing is excluded from coverage. Where MockBukkit cannot reach code directly, provide a small boundary that a test can exercise.

## Project layout

- `src/main/java/.../core` contains region geometry, the access policy, durations, conflict detection, settings, messages, the store contract and the in-memory registry. It must have no `org.bukkit.*` imports.
- `src/main/java/.../storage` contains the JDBC store, with the SQL that differs between SQLite, PostgreSQL and MySQL in a `Dialect`, numbered migrations, a connection source for each backend and the writer thread. It has no server imports.
- `src/main/java/.../bukkit` connects the domain to the server through the plugin class, listeners, command, config codec, messenger and playtime reader.
- `src/test` contains JUnit and AssertJ unit tests, with store tests using a real SQLite file.
- `src/integrationTest` contains MockBukkit integration tests for the adapter.
- `src/serverTest` and `src/serverTestFixture` contain the Docker harness and a test-only plugin that fires real events for a fake player.
- `src/jmh` contains microbenchmarks.
- `docs/` contains user and technical documentation.

Read [docs/architecture.md](docs/architecture.md) for the layer boundaries and [docs/performance.md](docs/performance.md) for the hot-path budget. The detailed coding standards are in [.claude/rules/](.claude/rules/).

## Coding standards (the ones that matter most)

- Keep `core` and `storage` free of `org.bukkit.*` imports. The adapter extracts primitive values and asks `core` for a decision.
- Keep store access out of event handlers, so block events read the in-memory registry and writes run on the store's own thread.
- Use immutable records for domain values and validate their invariants in the constructor.
- Use stable API only. Avoid NMS, CraftBukkit internals and reflection so the released jar can keep working on later Paper builds.
- Pin dependency and plugin versions in `gradle/libs.versions.toml`. The build writes the library versions into `plugin.yml`, so they are never typed twice.
- Run `./gradlew spotlessApply` to format Java, as the build rejects unformatted code. Use prettier for non-code files.
- Write Javadoc for public types and methods, and give every package a `package-info.java`.

## Pull requests

1. Fork the repository and create a branch.
2. Add your change and its tests, then run `./gradlew build` and fix any failures. Also run `./gradlew serverTest` if you change the store, schema, `plugin.yml` libraries, drivers or behaviour that depends on the real server.
3. Open a PR and complete the template. CI runs the build, server tests, lint and a security scan on every PR.

Your contributions are licensed under the project's [GPL-3.0 licence](LICENSE).

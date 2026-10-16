# Review Checklist

Before a change lands:

- [ ] `./gradlew build` passes: compile, Spotless, the unit and integration tests, and 100% line and branch coverage over the whole plugin.
- [ ] No `org.bukkit.*` import under `core` or `storage`, and no store call in a block event handler.
- [ ] New or changed decision logic is in `core` and unit-tested, edge cases and validation failures included; adapter wiring has an integration test.
- [ ] New domain types are immutable and validate their invariants in the constructor.
- [ ] Any new dependency or plugin version is pinned exactly in `gradle/libs.versions.toml`.
- [ ] Public types and methods have Javadoc, and new packages have a `package-info.java`.
- [ ] Hot-path code (anything a block event reaches) does the cheapest check first and allocates nothing on the common path. If in doubt, add a JMH benchmark.
- [ ] A change to anything a player reads (the messages, `/sbr` for players) is called out in the commit message, because it changes what every server shows.
- [ ] A change to the store, the schema, `plugin.yml` `libraries:` or the drivers was run through `./gradlew serverTest`.
- [ ] A script change keeps `make test-scripts` at 100%.
- [ ] The docs are updated if behaviour, config, commands, permissions, protection scope, storage or performance changed, and a released version has its `CHANGELOG.md` entry.
- [ ] Prose is not hard-wrapped.

# Consistency

New code must look like the code already here. Before writing, find the closest existing example and follow its structure, naming and idiom.

- **Formatting is not a choice.** Spotless (google-java-format, AOSP style, 4 spaces) is part of the build; run `./gradlew spotlessApply`. Prettier formats YAML, JSON and Markdown.
- **Versions live in one place.** Every dependency and plugin version is pinned in `gradle/libs.versions.toml`. The build writes the library versions into `plugin.yml`, so they are never typed twice.
- **Names are full words.** No abbreviations where a full word reads clearly.
- **Documentation.** Every package has a `package-info.java`, and every public type and method has Javadoc that says why, not only what.
- **Tests follow the split.** `core` and `storage` are unit-tested with JUnit and AssertJ and no server, the store against real SQLite files, through the pool as well. The adapter is covered by MockBukkit tests in `src/integrationTest`. Together they hold the whole plugin to 100% line and branch coverage with nothing excluded; code only a real server can reach goes behind a seam the tests can drive. What only a real server can show (loading, Paper downloading the libraries, real events, the real databases) is covered by `src/serverTest` in Docker.
- **Scripts are Python and tested.** Every script in `scripts/` is stdlib Python with tests in `scripts/tests/`, held to 100% line and branch coverage.

## Prose

Prose is never hard-wrapped; one line per paragraph (prettier runs `proseWrap: never`). Anything a person reads (docs, comments, workflow text, commit messages, the listings, the messages players see) is plain British English in short sentences. No em or en dashes, curly quotes or ellipsis characters. None of the usual signs of machine-written text: words such as delve, robust, seamless, leverage, comprehensive, crucial, ensure, utilise, streamline, showcase and underscore; phrases such as "it's worth noting" and "plays a key role"; and patterns such as "not just X but Y", lists of three for rhythm, and a last line that sums up what was just said.

A message a player reads about their own build rights is one short sentence with no hyphens, brackets or commas. Docs never state the playtime requirement as a fact about the plugin: it is "the configured playtime", and a number appears only where it is clearly an example.

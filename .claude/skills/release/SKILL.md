---
name: release
description: Cut a new SpawnBuildRights release. Use when asked to release, publish or ship a new version, bump the version, or write a changelog entry.
---

# Releasing SpawnBuildRights

A release is a push to `main` that changes the version. The Release workflow does the rest. `docs/releasing.md` is the full reference; this is the order to do it in.

## 1. Pick the version

Read the current one with `./gradlew -q printVersion`. Bump `version = "X.Y.Z+mc$gameVersion"` in `build.gradle.kts`:

- PATCH for fixes and dependency updates,
- MINOR for a new feature or setting,
- MAJOR for a change that breaks existing configs or stores.

The `+mc` part follows the Paper API pin and is never typed.

## 2. Write the changelog entry

Add `## <version>` at the top of `CHANGELOG.md` with one paragraph:

- Start with "This release", list what changed as a comma list with "and" before the last item.
- Name every moved dependency with both versions, "PostgreSQL JDBC 42.7.14 to 42.7.15". Find them with `git diff <previous tag> -- gradle/libs.versions.toml`.
- No colon, and no full stop at the end. A short second sentence is allowed for a note.
- Only what a server owner or player notices. No refactors, tests or tooling.

Check it with `python3 scripts/changelog.py check`.

## 3. Prove it

```
./gradlew build serverTest
make test-scripts
make lint
```

`serverTest` needs Docker and takes several minutes. Fix anything that fails before pushing.

## 4. Push and watch

Commit and push to `main`, then follow the Release workflow with `gh run watch`. When it finishes, check:

- `gh release view v<version>` shows the jar, the `.sha256` and the changelog entry under "What's changed",
- the Modrinth and Hangar steps say they uploaded, or that their token is not set.

If the release step fails on the changelog, the message names what is wrong or which moved dependency is missing. Fix the entry and push again; nothing was published.

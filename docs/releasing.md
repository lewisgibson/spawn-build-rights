# Releasing

Releases are automatic. Every push to `main` checks the version in `build.gradle.kts`, and if that version has no GitHub release yet, [`.github/workflows/release.yaml`](../.github/workflows/release.yaml) runs the full gate and publishes it. A push that keeps a released version publishes nothing. Tags do not start a release; the workflow creates the tag.

## Versions

Versions are `MAJOR.MINOR.PATCH+mc<minecraft>`, for example `1.0.0+mc26.3`. The plugin version follows [SemVer](https://semver.org/), and the Minecraft version is build metadata read from the Paper API pin in `gradle/libs.versions.toml`.

| Where                       | Example                                                      |
| --------------------------- | ------------------------------------------------------------ |
| `build.gradle.kts`          | `version = "1.0.0+mc$gameVersion"`, the only place it is set |
| `plugin.yml`                | `version: 1.0.0+mc26.3`, filled in by the build              |
| Jar                         | `SpawnBuildRights-1.0.0+mc26.3.jar`                          |
| Git tag                     | `v1.0.0+mc26.3`, created by the release workflow             |
| GitHub, Modrinth and Hangar | `1.0.0+mc26.3`                                               |

`./gradlew -q printVersion` prints the version.

## The changelog

`CHANGELOG.md` has one section per version, newest first, except the first release, which has nothing before it to compare with. Each section is a `## <version>` heading and one paragraph:

- It starts "This release" and says what changed as a comma list, with "and" before the last item.
- A dependency that moved is named with both versions: "updates PostgreSQL JDBC 42.7.14 to 42.7.15". Adds and removes read the same way: "adds MySQL storage", "removes the `foo` setting".
- It has no colon and no full stop at the end.
- A short note may follow as a second sentence, such as "PostgreSQL JDBC 42.8.0 is a beta build".
- It covers what a server owner or a player notices. Refactors, tests and build tooling are left out.

```markdown
## 1.1.0+mc26.3

This release updates Paper 26.3 build 194 to 26.3 build 201 and HikariCP 7.1.0 to 7.2.0, adds a protection for minecart rails and fixes a grant lost during a reload
```

`scripts/changelog.py check` checks every section's format; the Lint workflow and the pre-commit hook run it. At release time, `scripts/release-notes.py` also checks that the entry names every tracked dependency that moved since the previous release (Paper, HikariCP and the PostgreSQL and MariaDB drivers), and stops the release if one is missing.

## Cutting a release

1. Bump the version in `build.gradle.kts`.
2. Add the matching section to `CHANGELOG.md`.
3. Run `./gradlew build serverTest` and fix anything that fails.
4. Push to `main`.

For a version without a GitHub release, the workflow then:

1. Reads the version with `./gradlew -q printVersion` and runs the full gate.
2. Builds the jar and writes its `.sha256`.
3. Writes the notes with `scripts/release-notes.py`: [`.github/release-body-template.md`](../.github/release-body-template.md) with the version filled in, then the changelog entry, then the commit it was built from.
4. Creates the GitHub release `v<version>` with the jar and its checksum.
5. Uploads the jar to Modrinth with `scripts/publish-modrinth.py` when `MODRINTH_TOKEN` is set: loaders Paper, Purpur and Folia.
6. Uploads the jar to Hangar with `scripts/publish-hangar.py` when `HANGAR_TOKEN` is set: platform Paper.

Both uploads skip a version the store already has, so running the workflow again is safe. Only the publish job sees the store tokens. `DRY_RUN=1 scripts/publish-modrinth.py <version> <changelog> <jar>` prints what would be sent.

## Setting up publishing

The repository owner does this once:

1. Create the Modrinth and Hangar projects from the text and images in [docs/listing/](listing/).
2. Create a Modrinth personal access token with the "Create versions" scope, and a Hangar API key with the `create_version` permission.
3. In Settings > Secrets and variables > Actions, add:

| Kind     | Name                  | Value                          |
| -------- | --------------------- | ------------------------------ |
| Secret   | `MODRINTH_TOKEN`      | Modrinth personal access token |
| Secret   | `HANGAR_TOKEN`        | Hangar API key                 |
| Variable | `MODRINTH_PROJECT_ID` | Modrinth project id            |
| Variable | `HANGAR_PROJECT`      | Hangar project slug            |

## Dependency updates

Dependabot opens a pull request each week for newer Gradle dependencies and GitHub Actions, grouped into one for minor and patch updates. Those merge by themselves once Build, Lint and Security pass; major updates wait for a person. The Paper API is held to its current Minecraft version and the SQLite driver to the one Paper ships, because moving either is a deliberate change. A dependency update does not change the plugin's version, so it does not publish a release.

For the merge to wait for the checks, the repository needs "Allow auto-merge" turned on and a rule on `main` that requires the Build, Lint and Security checks. Until `main` has required checks, the workflow merges nothing and leaves each update for a person, because GitHub would otherwise merge it before the checks finish.

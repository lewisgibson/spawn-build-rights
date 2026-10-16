#!/usr/bin/env python3
"""Checks CHANGELOG.md against the changelog format, and reads one version's entry from it.

Every version after the first has a "## <version>" section holding one paragraph. It starts "This release", lists
what changed as a comma list with "and" before the last item, names a dependency that moved with both versions
("PostgreSQL JDBC 42.7.14 to 42.7.15"), and has no colon and no full stop at the end. A short note may follow as a second
sentence ("PostgreSQL JDBC 42.8.0 is a beta build"). The first release has no section, because nothing came before
it. docs/releasing.md has the format with examples.

Usage: changelog.py check
"""

from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The pins in gradle/libs.versions.toml a server owner has reason to know about, by the name the changelog uses: what
# the plugin is built for, and what Paper downloads onto their server for it.
TRACKED = {
    "paperApi": "Paper",
    "hikaricp": "HikariCP",
    "postgresql": "PostgreSQL JDBC",
    "mariadbJdbc": "MariaDB JDBC",
}

BANNED = {
    "\u2014": "an em dash",
    "\u2013": "an en dash",
    "\u2018": "a curly quote",
    "\u2019": "a curly quote",
    "\u201c": "a curly quote",
    "\u201d": "a curly quote",
    "\u2026": "an ellipsis character",
}


def sections(text: str) -> dict[str, str]:
    """Each "## <version>" heading and the text under it, in file order."""
    found: dict[str, str] = {}
    for match in re.finditer(r"^## (\S+)[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S):
        found[match.group(1)] = match.group(2).strip()
    return found


def problems(entry: str) -> list[str]:
    """What is wrong with one entry, in the format's own words; empty when it is right."""
    found = []
    if not entry.startswith("This release "):
        found.append('does not start "This release"')
    if "\n" in entry:
        found.append("is not one paragraph on one line")
    if ":" in entry:
        found.append("has a colon")
    if entry.endswith("."):
        found.append("ends in a full stop")
    found.extend(f"has {what}" for char, what in BANNED.items() if char in entry)
    return found


def check(text: str) -> list[str]:
    """Every problem in the file, each naming its version."""
    return [f"{version}: {problem}" for version, entry in sections(text).items() for problem in problems(entry)]


def pin_name(key: str, version: str) -> str:
    """How the changelog writes a pin: Paper's API build "26.3.build.174-beta" reads as "26.3 build 174"."""
    if key == "paperApi":
        match = re.fullmatch(r"(.+)\.build\.(\d+)(?:-\w+)?", version)
        if match:
            return f"{match.group(1)} build {match.group(2)}"
    return version


def moved(old_catalog: str, new_catalog: str) -> list[str]:
    """Each tracked pin that changed between two copies of the version catalog, as "Name old to new"."""
    old = tomllib.loads(old_catalog)["versions"]
    new = tomllib.loads(new_catalog)["versions"]
    lines = []
    for key, name in TRACKED.items():
        if key in old and key in new and old[key] != new[key]:
            lines.append(f"{name} {pin_name(key, old[key])} to {pin_name(key, new[key])}")
    return lines


def unnamed(entry: str, old_catalog: str, new_catalog: str) -> list[str]:
    """The moved pins an entry does not name."""
    return [line for line in moved(old_catalog, new_catalog) if line not in entry]


def main(argv: list[str], root: Path = ROOT) -> int:
    if argv[1:] != ["check"]:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    found = check((root / "CHANGELOG.md").read_text(encoding="utf-8"))
    for problem in found:
        print(f"CHANGELOG.md {problem}", file=sys.stderr)
    return 1 if found else 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))

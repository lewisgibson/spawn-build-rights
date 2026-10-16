#!/usr/bin/env python3
"""Uploads a release's jar to Modrinth as a new version of the project.

Reads MODRINTH_TOKEN and MODRINTH_PROJECT_ID from the environment. Without a token it does nothing and says so, which
is how publishing stays off until the token is set. A version already on Modrinth is skipped, so running a release
again never uploads twice. With DRY_RUN=1 it prints what it would send and sends nothing.

Usage: publish-modrinth.py <version> <changelog-file> <jar>
"""

from __future__ import annotations

import json
import os
import sys
import uuid
from collections.abc import Callable, Mapping
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import storeapi  # noqa: E402 (after the path it is found on)

API = "https://api.modrinth.com/v2"
# Modrinth keeps a version's sides as one environment. It stores the field only for loaders that declare it (the mod
# loaders do; paper, purpur and folia did not when checked on 2026-10-15), so for a plugin the sides come from the
# project type. It is sent anyway so the version is right if Modrinth starts storing it.
ENVIRONMENT = "server_only"
LOADERS = ["paper", "purpur", "folia"]


def version_data(project: str, version: str, changelog: str, jar: str) -> dict[str, object]:
    return {
        "project_id": project,
        "name": f"SpawnBuildRights {version}",
        "version_number": version,
        "changelog": changelog,
        "dependencies": [],
        "game_versions": [version.split("+mc", 1)[1]],
        "environment": ENVIRONMENT,
        "version_type": "release",
        "loaders": LOADERS,
        "featured": True,
        "status": "listed",
        "file_parts": [jar],
        "primary_file": jar,
    }


def published(project: str, token: str, transport: storeapi.Send) -> set[str]:
    listed = transport("GET", f"{API}/project/{project}/version", {"Authorization": token}, None)
    return {str(v["version_number"]) for v in listed} if isinstance(listed, list) else set()


def publish(
    version: str,
    changelog: str,
    jar: Path,
    env: Mapping[str, str],
    transport: storeapi.Send = storeapi.send,
    boundary: Callable[[], str] = lambda: uuid.uuid4().hex,
) -> str:
    """Uploads the jar unless it is already there; returns what was done."""
    if jar.name != f"SpawnBuildRights-{version}.jar":
        raise RuntimeError(f"{jar.name} is not the SpawnBuildRights {version} jar")
    if "+mc" not in version:
        raise RuntimeError(f"{version} is not a <semver>+mc<minecraft> version")
    token = env.get("MODRINTH_TOKEN", "")
    project = env.get("MODRINTH_PROJECT_ID", "")
    data = version_data(project, version, changelog, jar.name)
    if env.get("DRY_RUN") == "1":
        return f"would POST {API}/version with {json.dumps(data, indent=2)} and {jar.name}"
    if not token:
        return "MODRINTH_TOKEN is not set, so nothing was uploaded to Modrinth"
    if not project:
        raise RuntimeError("MODRINTH_TOKEN is set but MODRINTH_PROJECT_ID is not")
    if version in published(project, token, transport):
        return f"{version} is already on Modrinth, skipped"
    mark = boundary()
    body = storeapi.multipart(
        mark,
        [
            ("data", None, "application/json", json.dumps(data).encode()),
            (jar.name, jar.name, "application/java-archive", storeapi.jar_bytes(jar)),
        ],
    )
    answer = transport(
        "POST", f"{API}/version", {"Authorization": token, "Content-Type": f"multipart/form-data; boundary={mark}"}, body
    )
    created = answer.get("id") if isinstance(answer, dict) else None
    return f"uploaded {jar.name} to Modrinth as version {created}"


def main(argv: list[str], env: Mapping[str, str] = os.environ, transport: storeapi.Send = storeapi.send) -> int:
    if len(argv) != 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    changelog = Path(argv[2]).read_text(encoding="utf-8").strip()
    print(publish(argv[1], changelog, Path(argv[3]), env, transport))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))

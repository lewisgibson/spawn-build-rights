#!/usr/bin/env python3
"""Uploads a release's jar to Hangar (hangar.papermc.io) as a new version of the project.

Reads HANGAR_TOKEN (an API key with the create_version permission) and HANGAR_PROJECT (the project's slug) from the
environment. Without a key it does nothing and says so. A version already on Hangar is skipped, so running a release
again never uploads twice. The key is traded for a short-lived token first, as Hangar's API requires.

Usage: publish-hangar.py <version> <changelog-file> <jar>
"""

from __future__ import annotations

import json
import os
import sys
import urllib.parse
import uuid
from collections.abc import Callable, Mapping
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import storeapi  # noqa: E402 (after the path it is found on)

API = "https://hangar.papermc.io/api/v1"
# The first release has no changelog entry, so it gets this as its description rather than an empty one.
FIRST_RELEASE = "The first release"


def version_data(version: str, changelog: str) -> dict[str, object]:
    return {
        "version": version,
        "channel": "Release",
        "description": changelog or FIRST_RELEASE,
        "files": [{"platforms": ["PAPER"]}],
        "platformDependencies": {"PAPER": [version.split("+mc", 1)[1]]},
    }


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
    key = env.get("HANGAR_TOKEN", "")
    if not key:
        return "HANGAR_TOKEN is not set, so nothing was uploaded to Hangar"
    project = env.get("HANGAR_PROJECT", "")
    if not project:
        raise RuntimeError("HANGAR_TOKEN is set but HANGAR_PROJECT is not")
    answer = transport("POST", f"{API}/authenticate?apiKey={urllib.parse.quote(key)}", {}, None)
    if not isinstance(answer, dict) or not answer.get("token"):
        raise RuntimeError("Hangar did not return a token for HANGAR_TOKEN")
    auth = {"Authorization": str(answer["token"])}
    try:
        transport("GET", f"{API}/projects/{project}/versions/{urllib.parse.quote(version)}", auth, None)
        return f"{version} is already on Hangar, skipped"
    except storeapi.NotFound:
        pass
    mark = boundary()
    body = storeapi.multipart(
        mark,
        [
            ("versionUpload", None, "application/json", json.dumps(version_data(version, changelog)).encode()),
            ("files", jar.name, "application/octet-stream", storeapi.jar_bytes(jar)),
        ],
    )
    result = transport(
        "POST",
        f"{API}/projects/{project}/upload",
        {**auth, "Content-Type": f"multipart/form-data; boundary={mark}"},
        body,
    )
    url = result.get("url") if isinstance(result, dict) else None
    return f"uploaded {jar.name} to Hangar: {url}"


def main(argv: list[str], env: Mapping[str, str] = os.environ, transport: storeapi.Send = storeapi.send) -> int:
    if len(argv) != 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    changelog = Path(argv[2]).read_text(encoding="utf-8").strip()
    print(publish(argv[1], changelog, Path(argv[3]), env, transport))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))

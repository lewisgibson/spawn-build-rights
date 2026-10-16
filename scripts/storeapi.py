"""The HTTP calls the Modrinth and Hangar uploads share: one JSON request, and a multipart body."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from collections.abc import Callable, Mapping
from pathlib import Path

USER_AGENT = "lewisgibson/spawn-build-rights release workflow (https://github.com/lewisgibson/spawn-build-rights)"

# (method, url, headers, body) -> the parsed JSON answer, or None for an empty one
Send = Callable[[str, str, Mapping[str, str], "bytes | None"], object]


class NotFound(Exception):
    """The store answered 404."""


def send(method: str, url: str, headers: Mapping[str, str], body: bytes | None) -> object:
    request = urllib.request.Request(url, data=body, method=method, headers={"User-Agent": USER_AGENT, **headers})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise NotFound(url) from error
        raise RuntimeError(f"{method} {url} failed: {error.code} {error.read().decode(errors='replace')}") from error


def multipart(boundary: str, parts: list[tuple[str, str | None, str, bytes]]) -> bytes:
    """A multipart/form-data body from (field name, file name or None, content type, content) parts."""
    chunks = []
    for name, filename, content_type, content in parts:
        disposition = f'form-data; name="{name}"' + (f'; filename="{filename}"' if filename else "")
        chunks.append(
            f"--{boundary}\r\nContent-Disposition: {disposition}\r\nContent-Type: {content_type}\r\n\r\n".encode()
            + content
            + b"\r\n"
        )
    return b"".join(chunks) + f"--{boundary}--\r\n".encode()


def jar_bytes(jar: Path) -> bytes:
    if not jar.is_file():
        raise RuntimeError(f"no such jar: {jar}")
    return jar.read_bytes()

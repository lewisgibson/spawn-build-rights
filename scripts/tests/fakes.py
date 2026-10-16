"""A stand-in for a store's API that records every request, and a parser for the multipart bodies sent to it."""

from __future__ import annotations

import email.parser
import email.policy
from collections.abc import Callable, Mapping


class FakeStore:
    """Answers each request from a handler and records it."""

    def __init__(self, answer: Callable[[str, str], object]) -> None:
        self.answer = answer
        self.requests: list[tuple[str, str, Mapping[str, str], bytes | None]] = []

    def __call__(self, method: str, url: str, headers: Mapping[str, str], body: bytes | None) -> object:
        self.requests.append((method, url, headers, body))
        return self.answer(method, url)


def parts(content_type: str, body: bytes) -> dict[str, tuple[str, str | None, bytes]]:
    """Each part of a multipart body by field name: its content type, file name and content."""
    message = email.parser.BytesParser(policy=email.policy.HTTP).parsebytes(
        b"Content-Type: " + content_type.encode() + b"\r\n\r\n" + body
    )
    return {
        part.get_param("name", header="content-disposition"): (
            part.get_content_type(),
            part.get_filename(),
            part.get_payload(decode=True),
        )
        for part in message.iter_parts()
    }

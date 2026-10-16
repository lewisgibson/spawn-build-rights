from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from fakes import FakeStore, parts
from support import load

modrinth = load("publish-modrinth")

VERSION = "1.0.1+mc26.3"
ENV = {"MODRINTH_TOKEN": "mr-token", "MODRINTH_PROJECT_ID": "42X3xMvM"}


def store(published: list[str]) -> FakeStore:
    return FakeStore(
        lambda method, url: [{"version_number": v} for v in published] if method == "GET" else {"id": "new-id"}
    )


class PublishTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.jar = Path(self.tmp.name, f"SpawnBuildRights-{VERSION}.jar")
        self.jar.write_bytes(b"PK\x03\x04 a jar")

    def test_uploads_the_jar_as_a_listed_release(self) -> None:
        fake = store([])
        report = modrinth.publish(VERSION, 'This release does a; b, and "c"', self.jar, ENV, fake, lambda: "BOUNDARY")
        self.assertEqual(report, f"uploaded {self.jar.name} to Modrinth as version new-id")
        (get, post) = fake.requests
        self.assertEqual(get[:3], ("GET", "https://api.modrinth.com/v2/project/42X3xMvM/version", {"Authorization": "mr-token"}))
        method, url, headers, body = post
        self.assertEqual((method, url, headers["Authorization"]), ("POST", "https://api.modrinth.com/v2/version", "mr-token"))
        sent = parts(headers["Content-Type"], body or b"")
        self.assertEqual(sent[self.jar.name], ("application/java-archive", self.jar.name, b"PK\x03\x04 a jar"))
        content_type, _, data = sent["data"]
        self.assertEqual(content_type, "application/json")
        self.assertEqual(
            json.loads(data),
            {
                "project_id": "42X3xMvM",
                "name": f"SpawnBuildRights {VERSION}",
                "version_number": VERSION,
                "changelog": 'This release does a; b, and "c"',
                "dependencies": [],
                "game_versions": ["26.3"],
                "environment": "server_only",
                "version_type": "release",
                "loaders": ["paper", "purpur", "folia"],
                "featured": True,
                "status": "listed",
                "file_parts": [self.jar.name],
                "primary_file": self.jar.name,
            },
        )

    def test_a_version_already_on_modrinth_is_skipped(self) -> None:
        fake = store(["1.0.0+mc26.3", VERSION])
        self.assertEqual(modrinth.publish(VERSION, "n", self.jar, ENV, fake), f"{VERSION} is already on Modrinth, skipped")
        self.assertEqual([r[0] for r in fake.requests], ["GET"])

    def test_an_unexpected_answer_counts_as_nothing_published(self) -> None:
        fake = FakeStore(lambda method, url: {"error": "x"} if method == "GET" else None)
        self.assertEqual(
            modrinth.publish(VERSION, "n", self.jar, ENV, fake), f"uploaded {self.jar.name} to Modrinth as version None"
        )

    def test_without_a_token_nothing_is_sent(self) -> None:
        fake = store([])
        self.assertEqual(
            modrinth.publish(VERSION, "n", self.jar, {}, fake), "MODRINTH_TOKEN is not set, so nothing was uploaded to Modrinth"
        )
        self.assertEqual(fake.requests, [])

    def test_a_token_without_a_project_is_an_error(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "MODRINTH_PROJECT_ID"):
            modrinth.publish(VERSION, "n", self.jar, {"MODRINTH_TOKEN": "t"}, store([]))

    def test_a_dry_run_prints_the_request_and_sends_nothing(self) -> None:
        fake = store([])
        report = modrinth.publish(VERSION, "n", self.jar, {**ENV, "DRY_RUN": "1"}, fake)
        self.assertIn('"environment": "server_only"', report)
        self.assertIn('"project_id": "42X3xMvM"', report)
        self.assertEqual(fake.requests, [])

    def test_refuses_a_jar_of_another_version(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "is not the SpawnBuildRights 1.0.2"):
            modrinth.publish("1.0.2+mc26.3", "n", self.jar, ENV, store([]))

    def test_refuses_a_version_without_minecraft(self) -> None:
        jar = Path(self.tmp.name, "SpawnBuildRights-1.0.1.jar")
        with self.assertRaisesRegex(RuntimeError, "not a <semver>\\+mc<minecraft> version"):
            modrinth.publish("1.0.1", "n", jar, ENV, store([]))

    def test_a_missing_jar_is_an_error_before_anything_is_uploaded(self) -> None:
        self.jar.unlink()
        fake = store([])
        with self.assertRaisesRegex(RuntimeError, "no such jar"):
            modrinth.publish(VERSION, "n", self.jar, ENV, fake)
        self.assertEqual([r[0] for r in fake.requests], ["GET"])


class MainTest(unittest.TestCase):
    def test_reads_the_changelog_and_prints_what_was_done(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            changelog = Path(tmp, "changelog.md")
            changelog.write_text("\n  The notes  \n")
            jar = Path(tmp, f"SpawnBuildRights-{VERSION}.jar")
            jar.write_bytes(b"jar")
            fake = store([])
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = modrinth.main(["publish-modrinth.py", VERSION, str(changelog), str(jar)], ENV, fake)
        self.assertEqual(code, 0)
        self.assertEqual(out.getvalue(), f"uploaded {jar.name} to Modrinth as version new-id\n")
        self.assertIn(b'"changelog": "The notes"', fake.requests[1][3] or b"")

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(modrinth.main(["publish-modrinth.py", VERSION]), 2)
        self.assertIn("Usage: publish-modrinth.py", err.getvalue())


if __name__ == "__main__":
    unittest.main()

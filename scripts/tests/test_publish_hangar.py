from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from fakes import FakeStore, parts
from support import load

hangar = load("publish-hangar")
storeapi = hangar.storeapi

VERSION = "1.0.1+mc26.3"
ENV = {"HANGAR_TOKEN": "key+/=", "HANGAR_PROJECT": "SpawnBuildRights"}
API = "https://hangar.papermc.io/api/v1"


def answers(published: bool = False, token: object = None) -> FakeStore:
    def answer(method: str, url: str) -> object:
        if url.startswith(f"{API}/authenticate"):
            return {"token": "jwt-123", "expiresIn": 1000} if token is None else token
        if method == "GET":
            if published:
                return {"name": VERSION}
            raise storeapi.NotFound(url)
        return {"url": "https://hangar.papermc.io/lewisgibson/SpawnBuildRights/versions/1.0.1"}

    return FakeStore(answer)


class PublishTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.jar = Path(self.tmp.name, f"SpawnBuildRights-{VERSION}.jar")
        self.jar.write_bytes(b"PK jar")

    def test_trades_the_key_for_a_token_and_uploads_for_paper(self) -> None:
        fake = answers()
        report = hangar.publish(VERSION, "This release does a; b", self.jar, ENV, fake, lambda: "B")
        self.assertEqual(report, f"uploaded {self.jar.name} to Hangar: https://hangar.papermc.io/lewisgibson/SpawnBuildRights/versions/1.0.1")
        auth, check, upload = fake.requests
        self.assertEqual(auth[:2], ("POST", f"{API}/authenticate?apiKey=key%2B/%3D"))
        self.assertEqual(check[:3], ("GET", f"{API}/projects/SpawnBuildRights/versions/1.0.1%2Bmc26.3", {"Authorization": "jwt-123"}))
        method, url, headers, body = upload
        self.assertEqual((method, url, headers["Authorization"]), ("POST", f"{API}/projects/SpawnBuildRights/upload", "jwt-123"))
        sent = parts(headers["Content-Type"], body or b"")
        self.assertEqual(sent["files"], ("application/octet-stream", self.jar.name, b"PK jar"))
        self.assertEqual(
            json.loads(sent["versionUpload"][2]),
            {
                "version": VERSION,
                "channel": "Release",
                "description": "This release does a; b",
                "files": [{"platforms": ["PAPER"]}],
                "platformDependencies": {"PAPER": ["26.3"]},
            },
        )

    def test_the_first_release_gets_a_description(self) -> None:
        self.assertEqual(hangar.version_data("1.0.0+mc26.3", "")["description"], "The first release")

    def test_a_version_already_on_hangar_is_skipped(self) -> None:
        fake = answers(published=True)
        self.assertEqual(hangar.publish(VERSION, "n", self.jar, ENV, fake), f"{VERSION} is already on Hangar, skipped")
        self.assertEqual([r[0] for r in fake.requests], ["POST", "GET"])

    def test_without_a_key_nothing_is_sent(self) -> None:
        fake = answers()
        self.assertEqual(hangar.publish(VERSION, "n", self.jar, {}, fake), "HANGAR_TOKEN is not set, so nothing was uploaded to Hangar")
        self.assertEqual(fake.requests, [])

    def test_a_key_without_a_project_is_an_error(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "HANGAR_PROJECT"):
            hangar.publish(VERSION, "n", self.jar, {"HANGAR_TOKEN": "k"}, answers())

    def test_no_token_back_is_an_error(self) -> None:
        for token in ({}, ["x"]):
            with self.assertRaisesRegex(RuntimeError, "did not return a token"):
                hangar.publish(VERSION, "n", self.jar, ENV, answers(token=token))

    def test_an_answer_without_a_url_still_reports_the_upload(self) -> None:
        fake = FakeStore(lambda method, url: {"token": "t"} if "authenticate" in url else self._missing(method, url))
        self.assertEqual(hangar.publish(VERSION, "n", self.jar, ENV, fake), f"uploaded {self.jar.name} to Hangar: None")

    @staticmethod
    def _missing(method: str, url: str) -> object:
        if method == "GET":
            raise storeapi.NotFound(url)
        return None

    def test_refuses_a_jar_of_another_version(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "is not the SpawnBuildRights 1.0.2"):
            hangar.publish("1.0.2+mc26.3", "n", self.jar, ENV, answers())

    def test_refuses_a_version_without_minecraft(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "not a <semver>\\+mc<minecraft> version"):
            hangar.publish("1.0.1", "n", Path("SpawnBuildRights-1.0.1.jar"), ENV, answers())


class MainTest(unittest.TestCase):
    def test_reads_the_changelog_and_prints_what_was_done(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            changelog = Path(tmp, "changelog.md")
            changelog.write_text("\n")
            jar = Path(tmp, f"SpawnBuildRights-{VERSION}.jar")
            jar.write_bytes(b"jar")
            fake = answers()
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = hangar.main(["publish-hangar.py", VERSION, str(changelog), str(jar)], ENV, fake)
        self.assertEqual(code, 0)
        self.assertTrue(out.getvalue().startswith(f"uploaded {jar.name} to Hangar"))
        self.assertIn(b'"description": "The first release"', fake.requests[2][3] or b"")

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(hangar.main(["publish-hangar.py"]), 2)
        self.assertIn("Usage: publish-hangar.py", err.getvalue())


if __name__ == "__main__":
    unittest.main()

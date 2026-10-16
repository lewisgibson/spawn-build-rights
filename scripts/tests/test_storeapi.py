from __future__ import annotations

import http.server
import json
import tempfile
import threading
import unittest
from pathlib import Path

import storeapi
from fakes import parts


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802 (the name http.server calls)
        if self.path == "/missing":
            self.send_response(404)
            self.end_headers()
            return
        if self.path == "/fail":
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error":"invalid_input"}')
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(json.dumps({"agent": self.headers["User-Agent"], "auth": self.headers["Authorization"]}).encode())

    def do_POST(self) -> None:  # noqa: N802
        self.rfile.read(int(self.headers["Content-Length"]))
        self.send_response(204)
        self.end_headers()

    def log_message(self, *args: object) -> None:
        pass


class SendTest(unittest.TestCase):
    """The real transport, against a server on this machine."""

    def setUp(self) -> None:
        self.server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def test_sends_the_user_agent_and_headers_and_parses_json(self) -> None:
        self.assertEqual(
            storeapi.send("GET", f"{self.base}/ok", {"Authorization": "t"}, None),
            {"agent": storeapi.USER_AGENT, "auth": "t"},
        )

    def test_an_empty_answer_is_none(self) -> None:
        self.assertIsNone(storeapi.send("POST", f"{self.base}/version", {}, b"x"))

    def test_404_is_not_found(self) -> None:
        with self.assertRaises(storeapi.NotFound):
            storeapi.send("GET", f"{self.base}/missing", {}, None)

    def test_an_error_names_the_request_and_the_answer(self) -> None:
        with self.assertRaisesRegex(RuntimeError, r"GET .*/fail failed: 400 .*invalid_input"):
            storeapi.send("GET", f"{self.base}/fail", {}, None)


class MultipartTest(unittest.TestCase):
    def test_builds_fields_and_files_that_parse_back(self) -> None:
        body = storeapi.multipart(
            "XYZ", [("data", None, "application/json", b'{"a": 1}'), ("file", "a.jar", "application/java-archive", b"\x00jar")]
        )
        self.assertEqual(
            parts("multipart/form-data; boundary=XYZ", body),
            {"data": ("application/json", None, b'{"a": 1}'), "file": ("application/java-archive", "a.jar", b"\x00jar")},
        )
        self.assertTrue(body.endswith(b"--XYZ--\r\n"))


class JarBytesTest(unittest.TestCase):
    def test_reads_a_jar_and_refuses_a_missing_one(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp, "a.jar")
            jar.write_bytes(b"jar")
            self.assertEqual(storeapi.jar_bytes(jar), b"jar")
            with self.assertRaisesRegex(RuntimeError, "no such jar"):
                storeapi.jar_bytes(Path(tmp, "b.jar"))


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from support import load

changelog = load("changelog")

GOOD = (
    "This release updates HikariCP 7.1.0 to 7.2.0, adds MySQL storage and fixes a grant lost on reload"
)

CATALOG = """[versions]
paperApi = "26.3.build.174-beta"
hikaricp = "7.1.0"
postgresql = "42.7.14"
mariadbJdbc = "3.5.10"
junit = "6.1.3"
"""


class SectionsTest(unittest.TestCase):
    def test_reads_each_version_in_file_order(self) -> None:
        text = "# Changelog\n\nIntro.\n\n## 1.1.0+mc26.3\n\nNewer\n\n## 1.0.1+mc26.3  \n\nOlder\n"
        self.assertEqual(changelog.sections(text), {"1.1.0+mc26.3": "Newer", "1.0.1+mc26.3": "Older"})

    def test_a_file_with_no_versions_has_none(self) -> None:
        self.assertEqual(changelog.sections("# Changelog\n\nNothing yet.\n"), {})


class ProblemsTest(unittest.TestCase):
    def test_a_good_entry_has_none(self) -> None:
        self.assertEqual(changelog.problems(GOOD), [])
        self.assertEqual(changelog.problems(GOOD + ". PostgreSQL JDBC 42.8.0 is a beta build"), [])

    def test_names_every_problem(self) -> None:
        self.assertEqual(
            changelog.problems("Updates: the driver.\nMore."),
            [
                'does not start "This release"',
                "is not one paragraph on one line",
                "has a colon",
                "ends in a full stop",
            ],
        )

    def test_banned_typography(self) -> None:
        self.assertEqual(
            changelog.problems("This release fixes \u2014 \u2013 \u2018 \u201c \u2026"),
            ["has an em dash", "has an en dash", "has a curly quote", "has a curly quote", "has an ellipsis character"],
        )

    def test_check_names_the_version(self) -> None:
        text = f"## 1.0.1+mc26.3\n\n{GOOD}\n\n## 1.0.2+mc26.3\n\nFixed things.\n"
        self.assertEqual(
            changelog.check(text),
            ['1.0.2+mc26.3: does not start "This release"', "1.0.2+mc26.3: ends in a full stop"],
        )


class PinsTest(unittest.TestCase):
    def test_names_the_tracked_pins_that_moved(self) -> None:
        new = (
            CATALOG.replace("26.3.build.174-beta", "26.3.build.180-stable")
            .replace('"7.1.0"', '"7.2.0"')
            .replace('"6.1.3"', '"6.2.0"')
        )
        self.assertEqual(changelog.moved(CATALOG, new), ["Paper 26.3 build 174 to 26.3 build 180", "HikariCP 7.1.0 to 7.2.0"])

    def test_a_pin_added_or_removed_is_not_a_move(self) -> None:
        self.assertEqual(changelog.moved(CATALOG, CATALOG.replace('hikaricp = "7.1.0"\n', "")), [])

    def test_a_paper_version_in_another_shape_is_shown_as_it_is(self) -> None:
        self.assertEqual(changelog.pin_name("paperApi", "26.4-SNAPSHOT"), "26.4-SNAPSHOT")
        self.assertEqual(changelog.pin_name("paperApi", "26.3.build.7"), "26.3 build 7")

    def test_unnamed_lists_what_the_entry_leaves_out(self) -> None:
        new = CATALOG.replace('"7.1.0"', '"7.2.0"').replace('"42.7.14"', '"42.7.15"')
        self.assertEqual(changelog.unnamed(GOOD, CATALOG, new), ["PostgreSQL JDBC 42.7.14 to 42.7.15"])


class MainTest(unittest.TestCase):
    def run_check(self, text: str) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "CHANGELOG.md").write_text(text, encoding="utf-8")
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                code = changelog.main(["changelog.py", "check"], Path(tmp))
        return code, err.getvalue()

    def test_a_good_file_passes(self) -> None:
        self.assertEqual(self.run_check(f"# Changelog\n\n## 1.0.1+mc26.3\n\n{GOOD}\n"), (0, ""))

    def test_a_bad_file_fails_with_each_problem(self) -> None:
        self.assertEqual(
            self.run_check("## 1.0.1+mc26.3\n\nThis release fixes it.\n"),
            (1, "CHANGELOG.md 1.0.1+mc26.3: ends in a full stop\n"),
        )

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(changelog.main(["changelog.py"]), 2)
        self.assertIn("Usage: changelog.py check", err.getvalue())

    def test_the_repository_changelog_passes(self) -> None:
        self.assertEqual(changelog.main(["changelog.py", "check"]), 0)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Behavior and safety regression tests for the reviewed Zero Slop package."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "builtin-skills/skills/zero-slop/scripts/slopscore.py"
sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("zero_slop_scorer", SCRIPT)
assert SPEC and SPEC.loader
SCORER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SCORER)


class ZeroSlopTests(unittest.TestCase):
    def run_cli(self, *args: str, stdin: str | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(SCRIPT), *args], input=stdin, text=True,
            capture_output=True, check=False,
        )

    def test_score_is_offline_json_in_zero_to_one_hundred_range(self) -> None:
        result = self.run_cli("--json", "-", stdin="We are thrilled to announce a seamless pilot.")
        self.assertEqual(0, result.returncode, result.stderr)
        payload = json.loads(result.stdout)
        self.assertGreaterEqual(payload["ai_likelihood"], 0)
        self.assertLessEqual(payload["ai_likelihood"], 100)
        self.assertTrue(payload["hits"])

    def test_fidelity_preserves_facts_and_rejects_dropped_figure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before = root / "before.md"
            after = root / "after.md"
            before.write_text("On 12 March, Maya said \"keep it read-only.\" Retries fell 17%.", encoding="utf-8")
            after.write_text("Retries fell 17%. On 12 March, Maya said \"keep it read-only.\"", encoding="utf-8")
            self.assertEqual(0, self.run_cli("--fidelity", str(before), str(after)).returncode)
            after.write_text("On 12 March, Maya said \"keep it read-only.\"", encoding="utf-8")
            self.assertEqual(1, self.run_cli("--fidelity", str(before), str(after)).returncode)

    def test_fidelity_rejects_dropped_or_reversed_limitation(self) -> None:
        before = "The pilot included 48 users. We did not measure retention."
        self.assertFalse(SCORER.fidelity(before, "The pilot included 48 users.")["preserved"])
        reversed_claim = "The pilot included 48 users. We measured retention."
        self.assertFalse(SCORER.fidelity(before, reversed_claim)["preserved"])

    def test_recursive_input_rejects_file_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            outside = root.parent / f"{root.name}-private.md"
            outside.write_text("private five word phrase must stay private", encoding="utf-8")
            try:
                (root / "outside.md").symlink_to(outside)
                result = self.run_cli("--portfolio", str(root))
                self.assertNotEqual(0, result.returncode)
                self.assertIn("symbolic links are not allowed", result.stderr)
                self.assertNotIn("private five word phrase", result.stdout)
            finally:
                outside.unlink(missing_ok=True)

    def test_recursive_input_enforces_file_count_and_size_budgets(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "one.md").write_text("one", encoding="utf-8")
            (root / "two.md").write_text("two", encoding="utf-8")
            old_count, old_size = SCORER.MAX_BATCH_FILES, SCORER.MAX_BATCH_FILE_BYTES
            try:
                SCORER.MAX_BATCH_FILES = 1
                with self.assertRaisesRegex(SystemExit, "exceeds 1 text files"):
                    SCORER._text_files(root)
                SCORER.MAX_BATCH_FILES = old_count
                SCORER.MAX_BATCH_FILE_BYTES = 2
                with self.assertRaisesRegex(SystemExit, "exceeds 2 bytes"):
                    SCORER._text_files(root)
            finally:
                SCORER.MAX_BATCH_FILES = old_count
                SCORER.MAX_BATCH_FILE_BYTES = old_size

    def test_all_reviewed_patterns_compile_and_batch_gate_exit_codes(self) -> None:
        data = SCORER.load_patterns()
        self.assertEqual(len(data["patterns"]), len(SCORER._pattern_plan(data)))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "draft.md").write_text(
                "We are thrilled to announce a transformative seamless experience.",
                encoding="utf-8",
            )
            self.assertEqual(1, self.run_cli("--batch", str(root), "--gate", "0").returncode)
            self.assertEqual(0, self.run_cli("--batch", str(root), "--gate", "100").returncode)

    def test_one_thousand_short_documents_finish_within_generous_budget(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for index in range(1_000):
                (root / f"draft-{index:04d}.md").write_text(
                    "A direct sentence with concrete wording.", encoding="utf-8"
                )
            started = time.monotonic()
            result = self.run_cli("--batch", str(root), "--json", "--gate", "100")
            elapsed = time.monotonic() - started
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(1_000, json.loads(result.stdout)["documents"])
            self.assertLess(elapsed, 30, f"batch regression: {elapsed:.2f}s")


if __name__ == "__main__":
    unittest.main()

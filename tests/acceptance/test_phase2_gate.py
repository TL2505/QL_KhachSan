"""Unit fixtures only: never mark real acceptance evidence as passed."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("gate", ROOT / "scripts/validate-phase2-acceptance.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class Phase2GateTest(unittest.TestCase):
    def test_automated_success_does_not_imply_manual_acceptance(self):
        result = gate.validate({}, [{"suite": "Synthetic unit fixture", "tests": 109,
                                    "failures": 0, "errors": 0, "skipped": 0}])
        self.assertFalse(result["phase2_complete"])
        self.assertEqual(set(gate.GATES) | {"D8.4"}, set(result["pending_tasks"]))

    def test_status_alone_cannot_close_a_task(self):
        result = gate.validate({"tasks": {"C5.1": {"status": "passed"}}}, [])
        self.assertIn("C5.1", result["pending_tasks"])
        self.assertIn("D8.4", result["pending_tasks"])

    def test_render_boundary_and_external_evidence_are_rejected(self):
        record = {"status": "passed", "measurements": {"render_ms": 500},
                  "checks": {key: True for key in gate.GATES["C5.1"][1]},
                  "verified_by": "Synthetic unit fixture", "verified_at": "2026-10-15",
                  "evidence": ["../outside-project.txt"]}
        result = gate.validate({"tasks": {"C5.1": record}}, [])
        self.assertTrue(any("render_ms" in reason for reason in result["pending_tasks"]["C5.1"]))
        self.assertTrue(any("out-of-workspace" in reason for reason in result["pending_tasks"]["C5.1"]))

    def test_skipped_automated_suite_blocks_signoff(self):
        result = gate.validate({}, [{"suite": "Synthetic skipped fixture", "tests": 1,
                                    "failures": 0, "errors": 0, "skipped": 1}])
        self.assertTrue(any("Synthetic skipped fixture" in reason for reason in result["pending_tasks"]["D8.4"]))


if __name__ == "__main__":
    unittest.main()

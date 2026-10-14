"""Read-only Phase 2 sign-off validator; never invents manual evidence."""
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATES = {
    "C5.1": ({"render_ms": 500}, ["responsive_375", "responsive_800", "responsive_1920", "no_console_errors"]),
    "C5.3": ({"edt_ms": 16, "room_grid_update_ms": 200}, ["ctrl_w_verified", "atomic_check_in_verified"]),
    "C6.1": ({"edt_ms": 16}, ["qr_visible", "countdown_verified", "server_polling_verified"]),
    "C6.2": ({"edt_ms": 16}, ["settlement_overlay_visible", "chime_heard", "no_duplicate_notification"]),
    "C7.1": ({"edt_ms": 16, "room_update_ms": 100}, ["dirty_filter_verified", "cleaning_audit_verified"]),
    "C7.2": ({"edt_ms": 16}, ["pms_prices_verified", "exact_posting_verified", "stock_decrement_verified"]),
    "C7.3": ({"edt_ms": 16}, ["conflict_without_pin_verified", "authorized_override_verified", "audit_verified"]),
    "C8.1": ({"edt_ms": 16, "reader_open_ms": 500}, ["default_reader_opened", "invoice_details_verified"]),
    "C8.3": ({"edt_ms": 16, "dashboard_update_ms": 300}, ["kpis_verified", "csv_verified"]),
}


def evidence_valid(references):
    return (isinstance(references, list) and bool(references)
            and all(isinstance(name, str) and (ROOT / name).resolve().is_relative_to(ROOT)
                    and (ROOT / name).resolve().is_file() for name in references))


def validate(manifest, summary):
    pending = {}
    for task, (limits, checks) in GATES.items():
        record = manifest.get("tasks", {}).get(task, {})
        reasons = []
        if record.get("status") != "passed":
            reasons.append("manual execution is not marked passed")
        for metric, maximum in limits.items():
            value = record.get("measurements", {}).get(metric)
            if (isinstance(value, bool) or not isinstance(value, (int, float))
                    or not math.isfinite(value) or not 0 <= value < maximum):
                reasons.append(f"requires measured {metric} < {maximum}")
        for check in checks:
            if record.get("checks", {}).get(check) is not True:
                reasons.append(f"requires {check}")
        evidence = record.get("evidence", [])
        if not isinstance(evidence, list) or not evidence:
            reasons.append("requires evidence files")
        else:
            for name in evidence:
                if not isinstance(name, str):
                    reasons.append("invalid evidence path")
                    continue
                path = (ROOT / name).resolve()
                if not path.is_relative_to(ROOT) or not path.is_file():
                    reasons.append(f"missing or out-of-workspace evidence: {name}")
        if not record.get("verified_by") or not record.get("verified_at"):
            reasons.append("requires verifier and verification date")
        if reasons:
            pending[task] = reasons

    gate_reasons = []
    if not isinstance(summary, list) or not summary:
        gate_reasons.append("automated test summary is missing")
    else:
        if sum(item.get("tests", 0) for item in summary) < 1:
            gate_reasons.append("no automated tests executed")
        for item in summary:
            if any(item.get(key, 0) for key in ("failures", "errors", "skipped")):
                gate_reasons.append(f"non-passing automated suite: {item.get('suite')}")
    if manifest.get("baseline_inventory_reconciled") is not True:
        gate_reasons.append("reconcile WBS 47-test baseline with the checked-in inventory")
    elif not evidence_valid(manifest.get("baseline_reconciliation_evidence")):
        gate_reasons.append("baseline reconciliation requires its supporting record")
    docker = manifest.get("docker", {})
    if docker.get("build_passed") is not True or docker.get("health_check_passed") is not True:
        gate_reasons.append("Docker build and health check are unverified")
    if not evidence_valid(docker.get("evidence")) or not docker.get("verified_by") or not docker.get("verified_at"):
        gate_reasons.append("Docker verification requires logs, verifier and date")
    if pending:
        gate_reasons.append("manual task evidence is incomplete")
    if gate_reasons:
        pending["D8.4"] = gate_reasons
    return {"phase2_complete": not pending, "pending_tasks": pending,
            "automated_tests": sum(item.get("tests", 0) for item in summary) if isinstance(summary, list) else 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=ROOT / "docs/verification/phase2/manual-acceptance.json")
    parser.add_argument("--summary", type=Path, default=ROOT / "docs/verification/phase2/test-summary.json")
    arguments = parser.parse_args()
    result = validate(json.loads(arguments.manifest.read_text(encoding="utf-8-sig")),
                      json.loads(arguments.summary.read_text(encoding="utf-8-sig")))
    print(json.dumps(result, indent=2))
    return 0 if result["phase2_complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

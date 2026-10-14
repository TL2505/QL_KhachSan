"""Collect results from a clean Phase2RegressionSuite run, including the 47-case baseline."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/verification/phase2"
BASELINE_CLASSES = {
 "quanlykhachsan.backend.booking.FinancialCalculatorTest",
 "quanlykhachsan.backend.booking.FinancialEdgeCaseTest",
 "quanlykhachsan.backend.booking.FolioPaymentMariaDbIntegrationTest",
 "quanlykhachsan.backend.booking.IdempotencyMariaDbIntegrationTest",
 "quanlykhachsan.backend.report.DbrReportServiceTest",
 "quanlykhachsan.backend.room.RoomAllocationMariaDbIntegrationTest",
 "quanlykhachsan.backend.utils.MariaDbMigrationIntegrationTest",
 "quanlykhachsan.frontend.api.ApiClientTest",
 "quanlykhachsan.frontend.view.DenseTableRendererTest",
 "quanlykhachsan.frontend.view.RoomGridPanelTest",
}
RECOVERED_METHODS = [
 "testHealthCheck", "testAuthLogin_Success", "testAuthLogin_InvalidCredentials",
 "testRoomsList_CompliantStatuses", "testRoomsAvailability", "testCustomerCreation",
 "testBookingsList", "testBookingGetById", "testBookingGetById_NotFound",
 "testBookingCreate_MissingIdempotencyKey", "testBookingCreate_WithIdempotencyKeyAndReplay",
 "testBookingUpdate", "testBookingCancel",
]
summary = []
original = []
recovered = []
for path in sorted((ROOT / "target/surefire-reports").glob("TEST-*.xml")):
 suite = ET.parse(path).getroot()
 summary.append({"suite": suite.get("name"), **{k: int(suite.get(k, "0")) for k in ("tests", "failures", "errors", "skipped")}})
 for case in suite.findall("testcase"):
  cls, name = case.get("classname"), case.get("name")
  record = {"class": cls, "test": name, "passed": not any(case.find(k) is not None for k in ("failure", "error", "skipped"))}
  if cls in BASELINE_CLASSES:
   original.append(record)
  elif cls == "quanlykhachsan.api.CoreEndpointsIntegrationTest" and name in RECOVERED_METHODS:
   recovered.append(record)
if len(original) != 34 or len(recovered) != 13:
 raise SystemExit(f"Baseline not reconciled: original={len(original)}, recovered={len(recovered)}")
if not summary or any(x[k] for x in summary for k in ("failures", "errors", "skipped")):
 raise SystemExit("Only a complete passing run without skips can be saved as acceptance evidence")
source = ROOT / "tests/quanlykhachsan/api/CoreEndpointsIntegrationTest.java"
record = {
 "required_baseline_count": 47, "original_count": 34, "recovered_count": 13,
 "baseline_count": 47, "baseline_cases": original + recovered,
 "incoming_commit": "3844597", "incoming_source": "src/test/java/quanlykhachsan/api/CoreEndpointsIntegrationTest.java",
 "executable_source": str(source.relative_to(ROOT)).replace(chr(92), "/"),
 "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
 "additional_cases": "Two folio-payment cases from the same recovered class also execute; they are supplemental to the designated 47-case baseline.",
 "scope": "Recovered HTTP cases verify synthetic Phase 1 mock contracts. Production Phase 2 persistence, authorization, concurrency, and accounting are independently exercised by the remaining integration tests.",
}
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "test-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
(OUT / "baseline-reconciliation.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"automated_tests": sum(x["tests"] for x in summary), "baseline_tests": len(original) + len(recovered)}))

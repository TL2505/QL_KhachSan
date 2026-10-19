# Phase 4 verification — 2026-10-20

**Status: implementation available for review; release acceptance pending.**
The outstanding deployment and operational blockers below mean Phase 4 must not
be marked fully complete. They are also recorded in the
[unreleased changelog](../CHANGELOG.md#unreleased).

| Gate | Result | Evidence and limits |
|---|---|---|
| GATE-01 | PASS with explicit skips | `mvn test`: 187 tests, 0 failures, 0 errors, 38 skips requiring explicit disposable integration configuration. Skips are not benchmark results. |
| GATE-02 | PASS on reproducible empty-schema equivalent | MariaDB 11.4.13 automatically applied schema-only base, prerequisites and all ten manifest migrations. Same manifest/transformation as initializer; generated schema removed afterward. Docker entrypoint itself could not run here. |
| GATE-03 | PASS | Source scan and frontend tests exclude fabricated IDs, simulation flags, external QR calls and simulated financial persistence. PAID comparisons only render server values. |
| GATE-04 | BLOCKED | Deployed localhost and configured VM HTTP endpoints timed out; explicit VM DB connection also timed out. Ephemeral local HTTP/proxy tests pass but do not certify deployed environments. |

Final commands and actual outputs:

```powershell
mvn test
# Tests run: 187, Failures: 0, Errors: 0, Skipped: 38
# BUILD SUCCESS

$env:DB_HOST='127.0.0.1'
mvn '-Dtest=Phase4BootstrapTest' '-Dhotel.phase4.verifyDatabase=true' test
# Phase 4 database engine: 11.4.13-MariaDB
# All ten manifest migrations applied.
# Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS

cd web-ui
npm test
# tests 51; pass 51; fail 0; skipped 0
```

The isolated test covers concurrent/HTTP order replay, authoritative prices,
IDOR, quote-only behavior, rollback, voucher eligibility, cash/bank replay,
partial/full settlement, unpaid checkout, cancellation/expiry, reconciliation
discrepancy, persisted revenue metrics, approval/audit and identity preservation.
The final extended run also passed verified-birthday/retention rules and
idempotent overdue no-show processing without changing another stay's room state.

Measured MariaDB creation → desktop listener delivery: **1101.983 ms**
through actual DAO/JSON/polling/EDT code; HTTP excluded. This is a controlled
measurement, not a production percentile or 100% delivery guarantee.

Other commands and intermediate results:

| Check | Actual result |
|---|---|
| Initial sandbox `mvn test` | Maven cache lock access denied; retried with approved access. |
| Initial full `mvn test` | 198 tests, 16 failures, 0 errors, 36 skips. Baseline's five-failure claim was stale. |
| Focused Maven tests for API client, CSP/security, concierge and CORS | Exposed CSP mismatch; corrected and included in passing full suite. |
| Intermediate Maven/DB runs | Found compatibility method/fixture arity/import errors, obsolete vault startup assertion, wrong `ai_interactions` assumption (actual `ai_chat_logs`), cleanup ordering, and management-role denial. Corrected; no failures/timeouts suppressed. |
| Explicit VM bootstrap | Communications link/connect timeout; no test schema created on unreachable VM. |
| Initial Node runs | Windows file URLs, sandbox loopback denial and a transient JS syntax error. Corrected; ran with local HTTP access. Catalog tests now use explicit catalog fixtures. |
| `node --check` API/app/concierge/bookings modules | Passed after correction. |
| `mvn package` | BUILD SUCCESS; 187 tests, 0 failures, 0 errors, 38 skips; application JAR created. |
| Git Bash `-n database/bootstrap/00-init.sh` | Passed with read-only execution outside sandbox; initial sandbox attempt could not allocate the MSYS shared object. |
| Added-line secret review / served-copy check | No suspicious added-line secret candidates; modified served module copies match; initializer has LF line endings. |
| Bundled Python `-m pytest ai-service/tests -q -p no:cacheprovider` | Blocked: `No module named pytest`. No Python/model accuracy result claimed. |
| `git diff --check` | Transient whitespace corrected; final review records clean result. |
| Executable discovery | No Docker/MariaDB CLI on PATH. Real SQL checks used JDBC/local MariaDB. |

Targeted scan (no matches; `rg` exit 1 means no matches):

```powershell
rg -n 'isSimulated|api\.qrserver\.com|Math\.floor\(100000|bookingId\s*=.*Math\.random|paymentStatus\s*[:=]\s*["\x27]PAID' web-ui --glob '*.js' --glob '!*.test.mjs'
```

`Invoke-WebRequest -TimeoutSec 4` read-only deployment probes:

```text
http://localhost:3000/portal.html                    timeout
http://localhost:3000/api/v1/health                 timeout
http://localhost:8081/api/v1/health                 timeout
http://192.168.56.102:3000/portal.html               timeout
http://192.168.56.102:3000/api/v1/health              timeout
http://192.168.56.102:8081/api/v1/health              timeout
```

Remaining blockers / concrete next actions:

- Start/reach reviewed localhost and Linux VM deployments; repeat portal,
  authenticated API, webhook and DB checks. GATE-04 requires actual operation.
- Supply Docker to verify images/entrypoint/Compose. The SQL sequence already
  passed on fresh schemas.
- Supply approved OCR/face models, reviewed data and reception camera trials
  before claiming >98.5% accuracy or <45-second check-in. No card device in scope.
- Install reviewed Python test dependencies in an isolated runtime; rerun AI
  tests. Java crypto/guardrail/TLS tests are separate evidence.
- Supply an actual invoice issuer/signing/verification mechanism before claiming
  regulatory compliance. Current PDFs are unsigned records.
- Run supervised guest pilot and explicitly configured load benchmark. No guest
  satisfaction, CCU, OCR accuracy or production latency metric was fabricated.

See [implementation notes and risks](PHASE_4_REPORT.md), including initial
legacy-fixture risk and existing-data migration limits. Raw local run logs are
under `target/phase4-*`; they are generated evidence, not source fixtures.

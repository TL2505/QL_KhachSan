# Phase 2 remaining acceptance tasks

The code and automated coverage are implemented. The latest saved clean regression contains 125 passing test invocations with no failures, errors, or skips. This does not replace native visual, audio, or timing evidence.

Record actual results in `docs/verification/phase2/manual-acceptance.json`. Leave an unexecuted gate pending; do not infer a pass from source inspection or reuse an API latency measurement as a desktop/UI timing measurement.

| Task | Required execution |
| --- | --- |
| C5.1 | On the booking page, measure rendering below 500 ms; verify widths 375, 800, 1920 and no console errors. |
| C5.3 | Ctrl+W opens the native walk-in wizard; complete a synthetic walk-in in the isolated test environment, verify atomic allocation and room-grid update below 200 ms; measure EDT dispatch below 16 ms. |
| C6.1 | Inspect the native QR image, countdown, and server polling; measure EDT dispatch below 16 ms. |
| C6.2 | Deliver an isolated test settlement callback; observe the overlay, hear the chime, and verify one notification despite duplicate delivery; measure EDT dispatch below 16 ms. |
| C7.1 | Filter dirty rooms, mark a synthetic room clean, verify audit actor/notes and update below 100 ms; measure EDT dispatch below 16 ms. |
| C7.2 | Load actual fixture catalog prices, post consumption, verify exact folio charge and stock decrement; measure EDT dispatch below 16 ms. |
| C7.3 | Use a fixture reservation in the maintenance window; verify rejection without PIN and approved override with audit; measure EDT dispatch below 16 ms. |
| C8.1 | Export a finalized synthetic invoice through Swing; verify accurate details in the default PDF reader and reader launch below 500 ms; measure EDT dispatch below 16 ms. |
| C8.3 | Open the closed-date DBR dashboard, verify KPI values and CSV, update below 300 ms; measure EDT dispatch below 16 ms. |
| D8.4 | All above gates, automated suite, Docker build/health check, and baseline inventory reconciliation must be supported by evidence. |

Use a disposable `*_test` MariaDB 11 schema and clearly marked synthetic fixtures. Real payment, hotel identity, PIN, tax, or OTP values must not be invented. Production configuration is separately documented in `phase2-production-configuration.md`.

For each task, supply measured values, the named boolean checks from the manifest, verifier/date, and evidence paths within the project (for example, logs or screenshots in `docs/verification/phase2/`). Setting `status` alone does not pass the validator.

Run the read-only gate:

```powershell
python ./scripts/validate-phase2-acceptance.py
```

Exit 0 means all recorded gates pass. Exit 1 reports pending task IDs and reasons. The tool does not register approvals, issue invoices, install Docker, or alter the database.

QA handoff recorded on 2026-10-15: the user has no host access currently and requested that the remaining checks be left for others to execute later. Use a dedicated Windows QA workstation with an interactive desktop for Swing; use a separate Linux CI runner/VM with Docker Engine and Compose for container acceptance. Windows with Docker Desktop/WSL 2 is an alternative if both must share one workstation. No native Swing or Docker result has been marked passed here.

C5.1 passed at 375, 800, and 1920 px: render times 80.5, 80.6, and 82.3 ms, respectively, with no console errors. Saved evidence: browser-render-timing.json and browser-render-desktop.png. The 47-case baseline is reconciled in baseline-reconciliation.json: 34 original invocations plus 13 recovered incoming core endpoint cases; two further recovered folio cases also execute.

Remaining IDs: **C5.3, C6.1, C6.2, C7.1, C7.2, C7.3, C8.1, C8.3, D8.4**. D8.4's automated suite and baseline inventory now pass; final sign-off awaits the eight native checks and Docker build/health evidence.

For Windows QA, configure isolated synthetic values, start the test database with scripts/start-phase2-testdb.ps1 (or provide another disposable MariaDB 11 schema), run scripts/test-phase2.ps1, then scripts/collect-phase2-test-evidence.py. Launch the Swing application with the same test configuration and API base, follow the table above, and record actual measurements and evidence in the manifest. Stop the optional local database with scripts/stop-phase2-testdb.ps1 after testing.

For Docker QA, use a fresh isolated Compose project and database volume with explicitly labeled synthetic environment settings. The checked-in .env.example intentionally contains no production defaults. Execute docker compose config --quiet, docker compose build, and docker compose up -d --wait. Save build/startup logs, docker compose ps, and the response from GET http://localhost:8081/api/v1/health; require HTTP 200 with status UP and both services healthy. Check /booking/ loads, protected room inventory requires authentication, and required migrations/fonts are present. Record verifier/date and evidence in the manifest before setting Docker checks true. The imported hotel.sql is an initialization script with DROP statements: use it only with the fresh QA volume, never as an upgrade script against existing hotel data.

Run the read-only validator again after evidence is supplied. It must continue to exit 1 while any acceptance evidence is missing.

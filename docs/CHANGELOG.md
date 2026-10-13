# CHANGELOG — Hotel PMS Documentation

## Rules
- Every change to `/docs/` MUST be logged here before commit.
- Format: `## [YYYY-MM-DD] — @author — file(s) — reason — what changed`
- If a constraint rule is added/modified → note which edge case or incident triggered it.
- If an API endpoint is added/removed → reference the phase (P1/P2/P3).
- Retention: permanent (do not delete old entries).

## [2026-10-09] — @toan — All 17 files — Initial documentation set
- Created `/docs/ai-context/` (6 files): constraints, data-access, escalation-rules, edge-cases, monitoring, incident-response
- Created `/docs/system-prompts/` (2 files): chatbot-guest, staff-assistant
- Created `/docs/dev-spec/` (8 files): architecture, api-contracts, test-scenarios, prompt-loading, implementation-plan, tech-stack, integration, deployment
- AI Behavior Contract v1.0 (11 hard rules)
- Defined Phase 1 scope (5 core endpoints)
- Deployment: 6-container Docker architecture

## [2026-10-11] — @devops — docs/dev-spec/*, docs/system-prompts/* — Platform Architecture & Tech-Stack Realignment
- Embedded Platform Design Philosophy: "Platform = WHERE THE USER SITS" (Swing Staff Phase 1-3 vs React Web Guest/Admin Phase 3+).
- Realigned documentation to plain HttpServer, Maven (pom.xml), and container health configurations.

## [2026-10-12] — @devops — docs/dev-spec/ui-*.md, docs/vi/dev-spec/ui-*.md — UI/UX Design System Specification
- Created 4 UI specification files: `ui-tokens.md`, `ui-desktop-staff.md`, `ui-web-guest.md`, `ui-web-admin.md` and complete Vietnamese translations.

## [2026-10-12] — @toan — docs/*, database/hotel.sql, docker-compose.yml — Architectural Refinements & Risk Mitigation
- Unified MUI 5.x across Web UIs; deferred Admin Web to Phase 4 to focus Dev C 100% on Guest Web.
- Reallocated AI Gateway responsibilities: Dev D on FastAPI/pgvector skeleton, Dev A on Java API client, Dev B on Night Audit & guardrails.
- Replaced Swing JEditorPane with Native Components (JPanel + BoxLayout + FlatRoundBorder + JTextArea).
- Enforced physical PII database boundary: configured MariaDB user `ai_reader` with read-only SELECT permissions on rooms and promotions.
- Standardized HikariCP Connection Pool and Fixed Thread Pool on plain Java HttpServer.

## [2026-10-12] — @toan — docs/dev-spec/tasks-phase1.md, pom.xml — Phase 1 WBS Specification & Dependencies
- Standardized Phase 1 WBS execution plan across 4 weeks and 4 developers.
- Added TASK-A1.4 for JWT auth / login controller; added HikariCP, SLF4J, and jbcrypt to pom.xml.
- Corrected room domain status codes (`vacant_clean`, `reserved`) in optimistic locking queries.
- Adjusted HikariCP leak detection threshold to 5000ms and timeout to 30s.
- Formatted clean Cross-Developer Dependency Matrix and Go/No-Go Acceptance Checklist.
## [2026-10-13] — @devops — root, SVN_Repo_KhachSan — Clean SVN Folders & Artifacts
- Removed legacy `SVN_Repo_KhachSan/` mirror directory from git tracking and disk.
- Removed `DongBoSVN.bat` synchronization script.
- Cleaned unused SVN placeholder folders (`trunk/`, `branches/`, `tags/`).

## [2026-10-13] — @codex — Dev C C3 frontend/backend files — C3 workflow review fixes
- Added Phase 1 folio payment and POS batch-sync routes with idempotency handling; connected the desktop clients to the configured API and authenticated session.
- Wired HTTP 503 responses to the Night Audit warning banner, disabled registered mutation controls during the freeze, and auto-syncs queued POS charges after unfreeze.
- Routed F2 to check-in and F3 to check-out for the selected room; displayed master and guest folio balances in checkout.
- Standardized room-state handling to the documented six status values and prevented bookings unless a room is `vacant_clean`.
- Added a request-generation guard to prevent stale room refresh results from replacing newer results; aligned the quick-search bar with theme tokens.

## [2026-10-13] — @codex — B1/C1 backend, frontend, tests, and build files — B1/C1 implementation and verification status
- Implemented the B1 migration/versioning and idempotency paths, connected booking pricing to the VAT/service-charge calculator, and added unit and opt-in MariaDB integration coverage.
- Implemented the C1 shared asynchronous API client, global F1–F10 shortcut dispatcher, and shared UI theme tokens; added API/EDT tests and Maven test-runner configuration.
- `mvn -o test` passed the available unit/API tests (26 passed); the three MariaDB integration checks were skipped because no MariaDB 11 test database was configured. B1.1/B1.2 database verification remains outstanding.

## [2026-10-13] — @codex — UpdateDbUtility.java, MariaDbMigrationIntegrationTest.java, database/hotel.sql — MariaDB 11 integration verification
- Verified the full suite on an isolated MariaDB 11.8.6 instance: 26 tests passed, 0 failures, 0 skipped. Coverage includes repeatable legacy-schema migration, the complete `database/hotel.sql` import, concurrent idempotent requests, and persisted failure replay.
- Scoped migration metadata lookups to the active database, taught the schema integration test to honor SQL `DELIMITER` directives, and corrected booking seed 63 from invoice status `paid` to valid booking status `checked_out`.

## [2026-10-14] — @codex — Dev B/C room allocation and desktop room-map files — Phase 1 remaining-task audit and partial implementation
- Added a MariaDB 11 opt-in race test for two room reservations; it requires `HOTEL_RUN_DB_INTEGRATION=true` and a disposable schema whose name ends in `_test`, then checks one success, one conflict, and one version increment.
- Added the fixed 80×60 room tile, 5-column detached map, six canonical status colors, in-memory room/guest/booking filtering, dense 24px booking status renderer, safe detached-window bounds persistence, and an EDT dispatch duration reporter.
- Connected the compact map to `RoomForm`, quick filtering, booking table rendering, and `MainUI` startup. These additions advance B2.1, C2.2, C2.3, C3.3, C4.1, and C4.2; their full WBS definitions of done remain partially unverified.
- Changed classes and focused tests compiled with JDK 17; focused UI tests passed (3 passed). The MariaDB race test was aborted by its environment assumption because no test schema was configured. Maven could not start: the user-level repository lock was unwritable, and the workspace-local retry could not resolve dependencies because Maven Central DNS was unavailable.

## [2026-10-14] — @codex — Dev B/C Phase 1 service completion and integration review
- Added atomic folio payment persistence, master/guest routing and split handling, scheduled 50-record no-show processing, seven-step persisted night-audit checkpoints with resume support, DBR aggregation, credit/refund math, and manager-authorized OOO-room downgrade handling.
- Connected API load failures to the Swing room-map error state, retained the 5-column room map and detached-monitor persistence, and documented the repeatable two-hour front-desk simulation procedure.
- Corrected new booking folios to begin with zero outstanding balance so nightly ledger postings do not duplicate the booking estimate; standardized audit freeze responses on `Retry-After: 300`.
- JDK 17 compiled all production and test Java sources. Focused JUnit run: 6 passed, 0 failed, 2 opt-in MariaDB tests aborted because no disposable MariaDB test schema was configured. `mvn -o test` remains unavailable due an unwritable user-level Maven repository lock. The two-hour simulation and live MariaDB transaction/recovery scenarios still require execution in their target environments.


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

## [2026-10-15] — @codex — Phase 2 backend, desktop/web UI, migrations, tests, scripts, and documentation — Phase 2 implementation and acceptance status
- Added the guest portal, OTP, public booking, server-authoritative pricing and vouchers, concurrent room allocation, VietQR, HMAC-verified webhooks, idempotent folio settlement, and overpayment handling.
- Added housekeeping and room-state workflows, minibar posting, OOO/OOS maintenance, PDF invoices with explicitly required tax configuration, DBR reporting, and corresponding Swing/web interfaces; included Phase 2 migrations, test doubles, and regression coverage.
- Added the changed-file inventory, required production configuration, implementation/acceptance report, and verification evidence. Synthetic values are limited to test fixtures; no production legal, banking, or OTP values were fabricated.
- The Phase 2 report records 109 automated tests passing with 0 failures and 0 skips on Java 17 and isolated MariaDB 11.4.2. Overall acceptance remains partial pending manual Swing/render-timing checks, Docker verification, and approved production configuration.

## [2026-10-15] — @codex — scripts/validate-phase2-acceptance.py, tests/acceptance/, docs/phase2-manual-acceptance.md, docs/verification/phase2/manual-acceptance.json — Phase 2 Go/No-Go evidence gate
- Added a read-only acceptance validator that reports pending task IDs and rejects missing measurements, skipped/failed automated tests, missing verifier/evidence, and unverified Docker/baseline reconciliation.
- Added four isolated unit checks; retained previously verified browser width/console checks without inventing render or native timing measurements. Overall Phase 2 remains partially accepted.
- Git fetch from origin/PHASE_1 was not executed: automatic approval review was unavailable because its usage limit was reached. No incoming merge/conflict resolution is claimed.

## [2026-10-15] — @codex — Incoming Phase 1 integration, baseline recovery, configuration, and acceptance handoff
- Fetched origin/PHASE_1 at 3844597 and refreshed PHASE_2. Resolved conflicts while preserving Phase 2 transactional and authorization behavior; retained incoming test source and recovered core endpoint cases in isolated test fixtures.
- Reconciled the designated 47-case baseline (34 original + 13 recovered); clean merged regression passed 125 tests with 0 failures, errors, or skips, including a production server registration check.
- Added deep database health and incoming response hardening; corrected Compose schema/configuration forwarding and removed default production credentials.
- C5.1 passed at 375/800/1920 px with rendering below 83 ms and no console errors. Saved evidence and a strict read-only acceptance validator.
- User has no QA host access currently. Documented later Windows Swing and Docker-host execution; C5.3, C6.1, C6.2, C7.1, C7.2, C7.3, C8.1, C8.3 and final D8.4 sign-off remain open. No native or Docker pass is claimed.

## [2026-10-15] — @codex — docs/dev-spec/web-ui-unification-review.md — Existing web inventory and unification proposal
- Inventoried 23 existing web screen/workflow surfaces across Customer, Staff, Admin and shared authentication/profile; recorded keep/fix/replace dispositions and verified source-level integration bugs.
- Proposed shared documented navy/Inter/light tokens, role-specific layouts/components, phased implementation and browser end-to-end acceptance; identified contradictory/future-phase documentation without silently expanding feature or framework scope.
- Recorded versioned-route and customer-ownership backend blockers for prior alignment. No application/backend changes or new browser E2E pass are claimed; style guide and implementation order await agreement.

## [2026-10-15] — @codex — web-ui/public/app.js, docs/dev-spec/web-ui-unification-review.md, web-ui tests — Role shell and session recovery
- Wired explicit presentation roles into the portal shell; Admin, Customer, and Front Desk retain separate menus, while Housekeeping, Maintenance, and F&B receive a restricted informational shell without Front Desk actions. Unknown roles fail closed.
- Connected expired-session notifications to shell logout and login feedback. Updated the review record for the agreed vanilla/navy/Inter direction and persisted light plus legacy dark modes; versioned-route and ownership backend changes remain unapproved.
- Focused Node suite: 15 passed, 0 failed, 0 skipped. Browser verified login light/dark toggle and persisted preference on reload; role end-to-end flows and target viewport matrix remain outstanding.
- `git fetch origin` could not read `.git/FETCH_HEAD` due environment permission denial; upstream movement was not verified. No merge, push, or backend changes were made.

## [2026-10-15] — @codex — web-ui/public/js/staff/room-map.js, web-ui/public/portal.html, shared-theme.css — Canonical room map and API setup copy
- Reworked the room map into a seven-state, text-labeled status overview using the shared canonical room-status helper, safe DOM text, responsive legend styles, and request-generation guards. Removed legacy click actions that posted client-calculated payments or reset room status to `available`; operational tasks remain in their separate modules.
- Corrected the portal API configuration placeholder and ngrok guidance to use `/api/v1`.
- Browser theme checks passed for light/dark toggle and preference persistence. Full role workflow/viewport acceptance has not been run because authenticated test access is unavailable here; backend checkout/ownership dependencies remain unresolved.
- Reworked Staff checkout to read booking folios from existing APIs, show server balances, settle with UUIDv4 idempotency keys, create configured VietQR and poll server payment status, then require a separate explicit checkout action. No client amount field, external QR account or payment-rule change remains in this screen.
- Added authenticated PDF blob retrieval for Admin and Staff invoices; session 401 handling is shared. Dashboard metrics now show loading/unavailable states rather than false zeros, with retry and theme-token chart styling.
- Re-ran focused suite after integration: 16 passed, 0 failed, 0 skipped. JavaScript syntax and `git diff --check` passed. Authenticated financial workflows still require isolated fixture browser acceptance.
- Elevated `git fetch origin` succeeded after the initial metadata permission failure. `origin/PHASE_1` and local `PHASE_1` remain at 3844597; `PHASE_2` remains 7 commits ahead of `origin/PHASE_2` with 0 incoming commits. No merge or push was performed.
- Generic `STAFF` sessions now fail closed unless the backend supplies a distinct supported department role; label normalization accepts `Front Desk` and `FRONT_DESK` without merging departmental menus.
- Replaced unsupported Staff loyalty discount promises with a shared status notice. Customer dashboard/bookings, account booking discovery, and personal loyalty views now show explicit blockers instead of sending customer-ID-dependent requests or calculating account booking prices locally. The distinct OTP Guest flow and unrelated Customer promotions/chat remain separate.

## [2026-10-15] — @codex — web-ui/public/css/theme-overrides.css — Light-mode readability and mobile floating controls
- Improved light-mode text contrast for muted labels and gold-gradient text, and restored a pale gold logo treatment over the unscrolled dark header.
- Set mobile hotline and chat controls to consistent 48px touch targets with 20px icons.
- Removed the experimental hero and footer gradients after review; no test run recorded for these CSS-only updates.

## [2026-10-15] — @codex — web-ui theme surfaces, docs/WEB_UI_ISSUES.md, docs/CHANGELOG.md, docs/vi/CHANGELOG.md — Global theme and UI issue tracking
- Added one shared Auto/Light/Dark theme control across the hotel landing pages and portal, with browser-preference tracking, persistent cross-page selection, accessible control labels, and matching light palettes; preserved customer and management layouts.
- Added theme behavior tests and recorded browser theme-color updates. Focused Node tests passed (6/6); JavaScript syntax and `git diff --check` passed.
- Created GitHub issues for outstanding UI/integration findings: #36 unsupported social icons, #37 lightbox close control, #38 room identity/pricing, #39 incomplete English localization, #40 missing party/room selectors, #41 admin statistics endpoint, #42 customer dashboard/loyalty loading, and #43 offers loading state.
- Documented the issue references in `docs/WEB_UI_ISSUES.md`. No issues already marked resolved were re-opened; `.phase2-runtime/` remains excluded from version control.

## [2026-10-16] — @codex — docker-compose.yml, web-ui portal/API serving and tests — Local portal integration
- Mounted the portal entry point and its assets in the web container, and served portal/public assets safely from the development server.
- Routed browser API requests through the same origin and added an explicit auth-session clearing helper for portal logout.
- Added regression coverage for portal assets, same-origin API routing, and session clearing.

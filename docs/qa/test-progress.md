# QA test progress

Updated: 2026-10-26

## Project and environment

- Branch: `codex/admin-room-types-avatar-media` (new branch prepared for the consolidated push).
- The checkout had existing user modifications before QA began. Those were preserved; QA changes touched regression fixtures, the MISA adapter, the migration manifest, the API contract, and QA documentation.
- Application: Java 17 REST backend and Swing desktop client, MariaDB persistence, and a vanilla JavaScript web UI served by `web-ui/server.js`; AI service and MISA meInvoice are separate integrations.
- Available runtimes: JDK 17.0.20.1, Maven 3.10.0, Node.js 24.20.0, npm 11.19.0. Docker is not installed.
- The user explicitly authorized synthetic QA writes to the local MariaDB selected by `.env`: `hotel_runtime_20261022`, MariaDB 11.4.13 on port 3306. The backend is started with `start-backend.bat` on port 8081; the web UI is the user-started server on port 3000. Payment is configured to the mock provider for QA, and SMTP delivery is disabled.

## Feature inventory

- **User roles:** admin, staff/front desk, customer/guest, housekeeping, maintenance, and food-and-beverage staff.
- **Guest and customer:** login/registration, Google OTP, profile and identity scanning, room discovery, availability, booking, promotions/vouchers, booking history, payment state, notifications, support, and loyalty.
- **Front desk and hotel operations:** reservation and walk-in flows, room assignment, check-in/out, folios, payment collection/webhooks, service orders, invoices, and revenue reports.
- **Housekeeping and maintenance:** room-state transitions, cleaning authorization, minibar inventory/posting, maintenance/out-of-order controls.
- **Administration:** users/roles, room catalog, services, membership, AI configuration, and reporting.
- **AI and integrations:** prompt guardrails, concierge chat/escalation, cloud provider settings, OTP/email, payment providers, and MISA e-invoice issue/sync/cancel/download.
- **Platforms:** browser pages for guest/staff/admin plus the Java Swing desktop client.

## Verification completed

| Area | Result | Evidence |
| --- | --- | --- |
| Java compile and Maven tests | **291 run; 0 failures; 0 errors; 42 skipped** | Latest full Maven suite used JDK 17, isolated `.run/qa-full` output and project-local Maven cache. The skips are environment-gated database/integration/performance cases. |
| Web UI tests | **153 passed; 0 failed; 0 skipped** | Latest `npm test` after popup-contrast regression coverage; local loopback access was enabled for local HTTP fixtures. |
| Changed browser JavaScript syntax | **6 files passed** | `node --check` on admin/staff invoices, customer bookings/discovery, concierge chat, and concierge room tools. |
| Static frontend smoke | **Passed** | Local server returned HTTP 200 for `/`; HTML contained the login and booking modals. API/backend was not running. |
| Live booking, mock QR payment, history, cancellation/refund and expiry | **Passed** | Browser on port 3000 plus the authorized local MariaDB. A paid mock booking was confirmed and then cancelled/refunded through the mock flow; a second unpaid QR booking expired; a third paid mock QR booking remains confirmed. No real payment was made. |
| Web booking lifecycle and MariaDB local-time handling | **Passed in rollback-only transaction** | On synthetic booking #3, simulated 07:05 check-in, 90% checkout reminder, 05:01 auto checkout, `vacant_dirty`, cleaning task and lifecycle notifications. All simulation writes were rolled back; booking remains `confirmed` and room `reserved`. Found and fixed JDBC `Timestamp` timezone conversion in the engine. |
| Post-fix Maven compile | **Passed** | Fresh isolated Maven build compiled 361 application sources and 126 test sources, including the MariaDB lifecycle regression test. |
| Booking wall-time JDBC mapping | **Partially verified** | Availability, reservation, QR expiry, cancellation, check-in/out, checkout adjustment, room readiness and housekeeping now bind/read local `DATETIME` values with `LocalDateTime`. The MariaDB lifecycle integration test exercised local-time booking and automatic check-in/out successfully; direct database/UI checks for each staff and customer endpoint remain pending. |
| Disposable MariaDB payment integration | **22 passed; 0 failed** | `scripts/test-payments.ps1` created an owned random `*_test` schema, applied migrations and ran the payment/notification integration cases plus webhook/signature/mail tests. The schema was removed afterward; no real gateway or email was called and the runtime schema was not used. |
| Auth and transport regression tests | **25 passed; 0 failed** | `portal-auth-fix.test.mjs` (9), `transport.test.mjs` (14), and `staff-bookings-auth.test.mjs` (2), including expiry redirection, legacy-token cleanup, and safe late responses. |
| Customer booking UI parity with landing page | **Passed** | Port 3000 live browser: compact search controls, expandable filters, availability lookup, room card, and booking summary/confirmation were visually checked. The booking was not submitted during this layout check. |
| Front-desk booking UI parity and automatic customer suggestions | **Passed with live inventory limit** | The stay/guest search controls, availability URL builder, and room-card renderer are shared with customer discovery. Seven focused web tests passed. On port 3000, entering a synthetic unmatched name showed no suggestion as required; live availability returned zero eligible clean rooms, so no booking or payment was submitted. |
| Front-desk room-card layout | **Passed in live browser** | Port-3000 staff availability search returned QA-801 in “Cần dọn phòng”. The fallback image, room facts, amenity chips, price, disabled action and reason now appear as distinct sections. No booking was created. |
| Staff counter collection and check-in timing | **Collection posted and verified; pre-check-in UI passed** | On the live staff UI, synthetic booking #5 was collected through the cash option for 850,500 VND after user authorization. The booking row showed “Đã thanh toán tại quầy”; transaction history showed one `cash / cash` folio-5 entry with 850,500 applied and zero excess (`APPLIED`). No real money or external gateway was used. `payment_paid / #5` is queued with zero email attempts, so Gmail delivery remains unverified while SMTP is disabled. Booking #3's pre-check-in button remains disabled until its scheduled time. |
| Invoice PDF regression | **Passed** | Synthetic statement contains internal-only labels, reconciled total, and embedded Vietnamese font; it does not claim to be an issued tax invoice. |
| Migration manifest regression | **Passed** | Manifest includes every SQL migration exactly once, including `v4_5_electronic_invoices.sql`. |
| MISA adapter contract | **6 passed; 0 failed** | Synthetic loopback HTTP fixtures cover POS/HSM route and payload shapes, token reuse/separation, status headers, view-link allowlisting, PDF validation and cancellation. The POS marker assertion fails against the old payload and passes with the fix. |
| Phase 2 acceptance gate | **4 validator unit tests passed; sign-off remains incomplete** | The read-only validator returned `phase2_complete: false`; it lists missing manual evidence for staff room-grid/check-in, QR/payment, housekeeping, minibar, maintenance override, invoicing, dashboard exports, and Docker. |
| Current e-invoice legal references | **Checked against official sources** | The operations guide now cites both Decree 254/2026/NĐ-CP and Circular 91/2026/TT-BTC, effective 01/07/2026. Seller-specific applicability remains for accounting/tax confirmation. |

Latest full-suite evidence is 291 Java tests (0 failures/errors, 42 skipped) and 140 web tests (0 failures). The Java suite was freshly compiled into `.run/qa-full` after the wall-time JDBC changes. Separately, 22 payment/notification tests passed against an owned disposable MariaDB schema, which the script removed. The 42 full-suite skips are other guarded database/integration/performance cases. The web suite remains current because no web source changed afterward.

## Workflow coverage checklist

- [x] Authentication and browser login/registration behavior through automated fixtures.
- [x] Guest/customer booking, voucher, membership, profile, payment-state, support, and notification UI behavior through Node tests.
- [x] Front-desk walk-in pricing/payment/check-in constraints through unit and UI tests.
- [x] Front-desk shared room-card display through the live browser, including preservation of the disabled action for a room needing cleaning.
- [x] Room allocation, housekeeping authorization/state rules, maintenance, minibar, folio, and webhook logic through Java unit/controller tests where fixtures allowed.
- [x] AI prompt guardrails, concierge authorization, provider settings, and session behavior through automated tests.
- [x] Internal invoice PDF semantics and migration-manifest consistency.
- [x] MISA adapter request/response behavior through synthetic local HTTP fixtures.
- [x] Customer browser booking and mock QR payment journeys through cancellation/refund and QR expiry against the running backend and synthetic MariaDB data; automatic check-in/out logic was simulated transactionally because the real booking time had not arrived.
- [x] Local MariaDB 11.4 migrations applied by backend startup; 22 payment, notification, webhook and signature cases passed on a disposable `*_test` schema.
- [ ] Remaining guarded MariaDB integration/migration/concurrency/performance cases outside the payment script; no test ran against the runtime database.
- [ ] MISA sandbox issue/sync/adjust/cancel/PDF operations; no MISA test credentials were available and no provider calls were made.
- [ ] Native Swing staff journey and full desktop UI inspection.
- [ ] Docker bootstrap and long-duration performance/soak run.

### Latest manual browser checks (2026-10-23)

- Opened the user-started UI at `http://127.0.0.1:3000/portal.html?view=users` against the local backend and logged in as the seeded demo administrator.
- Confirmed the permissions page loaded five users and live roles. Search and role filters worked; the existing-user role dialog opened and changed its selection locally, then was cancelled without saving.
- Updated and visually inspected the add-account dialog so its labels, spacing, controls, buttons, dark/gold palette, and responsive grid match the permissions page's dialogs. The live role list populated with Nhân viên selected by default.
- The user created a synthetic staff account through the admin form; the success dialog now presents the created name, username, optional contact details, role, and active status without showing the password. Signup errors also display under each relevant field and focus the first invalid input. Live invalid/valid/duplicate-account cases were checked.

### Browser-native dialog fix (2026-10-23)

- **Finding:** The customer booking-history “Hủy phòng” button showed a browser-native confirmation (`confirm()`), as shown in the user's screenshot. The same pattern appeared in customer, admin, and staff interface modules for confirmations, alerts, and text prompts.
- **Fix:** Replaced application `alert()` / `confirm()` / `prompt()` calls with the shared Aurelia modal helpers. The in-page dialog supports closing with Escape or by clicking the backdrop; missing modal markup is recreated in the page rather than falling back to a browser popup. Updated cache versions for the portal and customer views.
- **Checks:** `node --check` passed for 22 touched JavaScript files; `node --test tests/customer-booking-voucher.test.mjs tests/payment-state.test.mjs` passed 4/4; `git diff --check` passed. A source scan found no direct native dialog calls in application JavaScript; remaining matches are test-fixture strings and comments.
- **Live browser status:** After reauthenticating as the seeded demo guest, clicked “Hủy phòng” for synthetic booking #133 on port 3000. The in-page confirmation appeared; closed it with “Giữ đặt phòng” and reloaded the history to confirm #133 remained pending. The first visual pass exposed cramped action buttons; after the CSS fix, the buttons displayed separately and the destructive action was red. The final modal was also closed with “Giữ đặt phòng”; no cancellation was submitted.
- **Follow-up:** The browser-native modal was verified; later QA continued booking and payment flows through the live local backend and MariaDB as documented below.

### Customer booking and QR payment flow (2026-10-23)

- Reused the user-started customer portal at `http://127.0.0.1:3000/portal.html?view=bookings`; the backend health endpoint reported `UP` and `database=CONNECTED` after the `start-backend.bat` restart.
- The customer discovery search now follows the landing-page pattern: check-in, check-out, guest count, promo code, and a gold availability button; optional rental/time/type/price filters are expandable. Search returned one available room and the confirmation dialog showed the correct dates, guest count, one-night quote and QR/counter payment choices. This layout check did not submit another booking.
- Earlier live journey coverage: mock QR booking #1 was paid then cancelled with a mock refund; booking #2's QR deadline was advanced only in test data and the expiry worker cancelled it/released its room; booking #3 was paid via the mock UI and remains confirmed in the database. History shows these three states after reload.
- A rollback-only MariaDB simulation of booking #3 exercised automatic check-in at 07:05, a deduplicated checkout reminder after the 90% point, and automatic check-out at 05:01. It verified `checked_in`/`occupied_clean`, then `checked_out`/`vacant_dirty`, one pending cleaning task, and lifecycle notification records; rollback restored `confirmed`/`reserved` and left no simulated cleaning task.
- The first simulation exposed that `Timestamp.valueOf(LocalDateTime)` bound against MariaDB `DATETIME` shifted a 07:05 value to 00:05. The engine now uses `setObject(LocalDateTime)` for its wall-time comparisons and `check_in_actual`. A future MariaDB regression test was added and compiled; its disposable-schema guard was not overridden for this local runtime database.
- SMTP remains disabled: the booking/reminder/checkout emails are queued in `notification_outbox`, but no email was sent. The payment/refund provider used in this journey was mock-only; no real funds moved.

### Staff counter-payment and check-in timing (2026-10-23)

- The live staff page initially showed the old generic “Xác nhận” action. After reloading the frontend, it showed “Chưa có phương thức thu” for booking #4 because the running backend had not loaded the new `paymentChoice` field.
- `stop-backend.ps1` verified and stopped the recorded workspace backend PID 35712. The first sandboxed `start-backend.bat` attempt could not open Maven's protected user-profile lock. Rerunning the same project startup script with the required access succeeded: key preflight passed, database target was `127.0.0.1:3306/hotel_runtime_20261022`, MariaDB 11.4.13 connected, and backend health became ready on port 8081. No database was recreated.
- On `http://localhost:3000/portal.html?view=bookings`, synthetic booking #4 now shows “Chờ thanh toán tại quầy” and “Thu tiền tại quầy”. Opening the modal loaded its Folio master balance of 1,701,000 VND and offered “Tiền mặt” and “Thẻ ngân hàng (POS)”. I closed the modal without pressing “Xác nhận đã thu”; no folio payment was submitted and #4 remained pending.
- At 06:32 on 2026-10-23, confirmed booking #3 was scheduled for check-in at 14:00. Its “Nhận phòng” button was disabled and the row explained the available time. Cancelled bookings had no actions. The after-checkout UI branch is implemented but had no naturally ended confirmed booking for live verification.
- The booking DAO now exposes `customer_type`. After restarting through `start-backend.bat`, the live staff UI labeled bookings #3 and #4 “Đặt qua Web · hệ thống tự check-in/check-out”. Confirmed Web booking #3 showed the explicit automatic-processing note and no manual “Nhận phòng” button; pending counter booking #4 kept the “Thu tiền tại quầy” action. Horizontal scrolling confirmed the action column and notes together. Screenshot: `docs/qa/screenshots/staff-web-auto-lifecycle-note.png`.
- `node --check` passed for both staff booking modules; `git diff --check` passed for the touched backend, UI, and QA files. No automated test suite or cashier payment submission was run in this pass. The staff UI did not include a live Walk-in booking, so that label/action branch was not manually exercised.

## Confirmed findings and fixes

- `QA-2026-001`: Updated stale invoice PDF tests to the current internal-payment-statement contract. **Verified.**
- `QA-2026-002`: Added the electronic-invoice migration to the shared bootstrap manifest. **Verified by manifest regression; database application remains unverified.**
- `QA-2026-003`: Added MISA's required POS machine marker, corrected the POS request envelope, and avoided mutating the caller's payload. **Verified by six adapter tests using synthetic HTTP fixtures.**
- `QA-2026-005`: Added field-specific signup errors and removed the temporary fake identity insert; verified invalid fields and duplicate-account feedback in the browser. **Verified.**
- `QA-2026-006`: Added the Web booking lifecycle migration to the runtime migration sequence after booking history returned HTTP 500 on missing lifecycle schema. **Verified on local MariaDB; history now returns HTTP 200.**
- `QA-2026-007`: Unified customer discovery search controls and room-card actions with the landing page. **Verified in live browser and targeted UI tests.**
- `QA-2026-008`: Corrected Java/MariaDB wall-time binding in automatic booking lifecycle queries and check-in timestamp writes. **Verified with MariaDB rollback-only lifecycle simulation; permanent integration test compiled.**
- `QA-2026-009`: Added staff counter/POS collection through the folio API and exposed the booking payment choice. **Verified end to end in the live UI and transaction history using an authorized synthetic cash collection on booking #5.**
- `QA-2026-010`: Disabled check-in before the scheduled time and removed the action after checkout. **Pre-check-in UI verified; after-checkout UI branch not live-tested.**
- `QA-2026-011`: Exposed the booking channel and labeled Web bookings as system-managed; suppresses manual check-in/check-out for those stays while retaining Web counter collection. **Live UI verified on bookings #3/#4.**
- `QA-2026-029`: Corrected low-contrast form controls in admin popups under the light theme. **Room-type create/edit validation and account-role dialog verified live on port 3000; no form was saved.**

## Remaining risks and next actions

1. Run remaining guarded MariaDB concurrency/migration/performance cases on a disposable schema; do not point them at `hotel_runtime_20261022`.
2. Automatic lifecycle was verified under a simulated clock and rolled back. Observe a naturally due booking/reminder on a future run if a wall-clock journey is required; no current booking was left checked in or checked out for testing.
3. Continue remaining staff workflows through the browser, then run the desktop UI when a suitable GUI environment is available.
4. The staff counter-collection write path is verified for a synthetic cash collection on booking #5; POS and partial/excess staff collections remain untested in the live UI.
5. With MISA test credentials and an approved test seller account, exercise issue/sync/adjust/cancel/PDF operations in MISA's test environment before considering production setup.
6. Run Docker bootstrap and performance tests on a host with Docker and MariaDB 11.
7. QA-2026-012 overdue-notification fixture still needs a disposable MariaDB schema ending in `_test`; the current runtime database is deliberately not used by that integration test.

## Commands run

- `mvn test` initially could not open a lock in the protected user-profile Maven cache. Retried successfully with the existing project-local `target/maven-repository` cache and `target/qa-baseline` output.
- Final full-suite command: `$env:MAVEN_OPTS='-Dmaven.repo.local=target/maven-repository'; mvn -B '-Dhotel.build.directory=.run/qa-full' test`; result: 291 run, 0 failures/errors, 42 skipped. Build compiled 361 application and 126 test sources.
- `mvn -Dtest=MisaMeInvoiceProviderTest test` passed all six tests using only local synthetic HTTP responses.
- The POS marker regression was mutation-checked: temporarily omitting the marker made the test fail at its explicit contract assertion; restoring it returned all six adapter tests to passing.
- `python.exe -m unittest tests.acceptance.test_phase2_gate -v` passed four validator unit tests. The read-only `scripts/validate-phase2-acceptance.py` run returned `phase2_complete: false` and the outstanding manual/Docker gates; no acceptance status was fabricated.
- `test-dual-env.sh` was not run: its second target is the separately addressed VM `192.168.56.102`, and the script sends health and unauthenticated POST probes to that host.
- Maven test run used local ephemeral sockets and `target/qa-temp`; it did not enable database integration flags.
- `npm test` in `web-ui` passed all 140 tests when local test fixtures were allowed to bind/connect.
- `node --check` on six modified browser JavaScript files passed.

### Staff overdue notification center and overtime (2026-10-23)

- Added the staff notification migration/API, per-user read state, overdue event from the Web lifecycle worker, and stable-reference late fee reconciliation. The portal tray now loads for authorized staff roles and refreshes while open/visible.
- Backend compile passed across 361 application and 126 test sources. `CounterPaymentLifecycleTest`: 3 passed. The guarded `PaymentsNotificationsIntegrationTest` did not execute because it requires `HOTEL_RUN_DB_INTEGRATION=true` and a disposable database name ending in `_test`; the local runtime schema was not used for destructive integration fixtures.
- `node --check` passed for eight source/public portal and booking modules. Focused Node UI tests passed 4/4; `git diff --check` passed.
- Restarted with `start-backend.bat`; MariaDB 11.4.13 health/preflight passed and the staff notification API loaded through the live port-3000 portal. The bell showed “THÔNG BÁO NHÂN VIÊN” and the correct empty state. No overdue fixture, folio payment or email was created/sent, so a populated overdue notification still needs a disposable-fixture UI run.

### Customer directory details (2026-10-24)

- **Finding:** The customer table showed dashes for name/identity because its API map keys were snake_case while the staff/admin views consume camelCase. Email/address were missing from the list query; customer creation also ignored address.
- **Fix:** Standardized list response fields, selected email/address and identity-vault presence, persisted submitted addresses, applied staff/admin authorization to the PII list endpoint, and escaped database values in both source and served customer modules.
- **Verification:** `start-backend.bat` passed key/schema/vault preflight and restarted the backend against local MariaDB. Maven `test-compile` compiled 361 main and 126 test sources; no test suite was run. `node --check` passed on all four edited customer modules and `git diff --check` passed. The live authenticated port-3000 table now shows customer names, three existing email values, phone values, and “Đã lưu bảo mật” for a vaulted identity. Address cells remain empty because the existing rows have no address data. No customer records were created, deleted, or edited. Search-by-email and the admin page were not separately exercised.
- **Next action:** If requested, manually exercise email/address search and admin customer details with synthetic records; current production-like records should not be edited to fill missing addresses.

### Staff payment history readability (2026-10-24)

- **Finding:** The eight-column payment history compressed date/time, transaction ID, amount and status at the live desktop viewport. The filter controls also stacked despite sufficient horizontal space.
- **Fix:** Added a horizontally scrollable, minimum-width transaction table with legible column sizing and no-wrap rules for key values. Filter controls use a responsive row layout. Source and served assets are synchronized with cache-busted portal/module URLs.
- **Verification:** Reloaded the authenticated port-3000 staff page. Dates and amounts stayed on one line, the paid badges remained intact, transaction IDs were readable, and filters aligned horizontally. Folio 5 showed the authorized 850,500 VND cash entry; no extra transaction was submitted.
- **Status:** QA-2026-015 verified. Continue the remaining POS and partial/excess live collection paths separately.

### Identify guests from payment transactions (2026-10-24)

- **Finding:** Staff saw only Folio ID and transaction ID, so they needed another lookup to identify a guest's payment.
- **Fix:** Transaction history now projects booking code, customer name and room number; the UI displays those alongside Folio ID. Phone and identity numbers remain absent from this list.
- **Verification:** Rebuilt/restarted with `start-backend.bat`; MariaDB 11.4 connection, schema and vault-key preflight passed. The live authenticated port-3000 staff history showed the existing folio-5 transaction as “Khách QA đặt phòng 06h-18h”, `BKG-WEB-2026-5`, room `QA-801`. Filtering on Folio `5` returned exactly that row. Folios 1, 3 and 4 also resolved to the expected existing QA customer and booking-code rows. No extra transaction was submitted.
- **Status:** QA-2026-016 verified. POS and partial/excess live collection paths remain open and require a separate approved synthetic payment action.

### Payment history table consistency (2026-10-24)

- **Finding:** Payment and notification history used basic previous/next buttons instead of the shared portal pagination and table card.
- **Fix:** Both history endpoints now return current-page `items` plus an exact `total`; the UI uses the shared pagination control, matching page-size selector, range summary and page indicator. The menu item now appears below “Hóa đơn” as “Thanh toán & thông báo” with a wallet icon.
- **Verification:** Restarted with `start-backend.bat`; MariaDB 11.4.13, schema and vault preflight passed. The live staff page showed transactions `1–4 trên 4` and notifications `1–10 trên 17`; Next returned `11–17 trên 17` on page `2 / 2`, with correct disabled states. No database records, payments or emails were changed. JavaScript syntax and `git diff --check` passed.
- **Status:** QA-2026-017 verified. Continue the remaining POS and partial/excess live collection paths separately.

### Housekeeping staff interface preview (2026-10-24)

- **Scope:** Added a standalone housekeeping work-queue preview at `web-ui/public/housekeeping-preview.html` for the later staff-area integration. It uses synthetic room/task data only and makes no backend, booking, customer, database, payment, or email changes.
- **Manual browser verification:** On port 3000, accepted the already handed-over room 305 and saw it change to “Đang dọn”; confirmed cleaning and saw it in “Đã hoàn tất”. Room 110, which is not handed over until 13:30, stayed visible for awareness with the action disabled and an explanation. The completed list showed rooms 305 and 605. Earlier preview checks also exercised room search and the all/completed views.
- **Build checks:** `npm run build:housekeeping-preview`, `node --check` on source and served JavaScript, and `git diff --check` passed.
- **Regression suite:** The initial sandboxed `npm test` run had 10 failures. Two front-desk checks were stale for the new disabled room cards/empty-state guidance, and the notification test fixture did not implement the browser APIs used by the staff notification tray. Updated these regression checks and added a deterministic hotel-time clock for the check-in assertion. The first sandboxed full run still blocked local-server fixtures (`EACCES`); rerunning with localhost access passed the login/server integration cases. Final `npm test`: **136 passed, 0 failed**.
- **Backend regression:** mvn -o -Dmaven.repo.local=target/maven-repository -Dhotel.build.directory=target/qa-baseline test: **291 run, 0 failures/errors, 42 guarded tests skipped**. The skips include MariaDB destructive/integration fixtures that require an explicit disposable test schema.
- **Status:** Housekeeping preview workflow and full web-ui suite verified. Production integration remains intentionally deferred. Bundle this work with the related staff changes for the single GitHub push, as requested.

### Disposable MariaDB payment integration attempt (2026-10-24)

- **Safety setup:** The payment test script uses a random `hotel_payments_<token>_test` schema, marks ownership, disables outbound email, and drops only that owned schema after the run. No runtime-schema changes, payments, or emails were made.
- **Initial attempt:** The sandbox could not connect to `127.0.0.1:3306`; no disposable schema was created during that run.
- **Follow-up:** MariaDB service was confirmed listening on 3306 with local access. The script then ran against random owned schemas. It exposed and fixed a duplicate customer email in the test fixture, updated stale assumptions about the paged API and QR choice, and moved the outside-window transaction from minute 60 to minute 61 to avoid a microsecond-boundary ambiguity.
- **Final result:** All 22 payment/notification, webhook, signature and mail tests passed; the random schema was dropped by the script. No external payment or email was sent. The 15 database integration cases are included in this count.

### Staff session expiry recovery (2026-10-24)

- **Finding:** The staff booking tab displayed the raw 401 response `Missing or malformed Authorization header (Bearer <token>)` after an invalid or missing token.
- **Fix:** The shared API transport now clears unchanged/legacy-only stale credentials and dispatches the existing session-expired event; it suppresses technical 401/403 server messages. The booking list no longer renders raw exception messages or writes into a view removed by the sign-in transition.
- **Verification:** `node --test tests/transport.test.mjs`: **14 passed**, including authenticated PDF expiry. Two staff-booking tests cover safe auth errors and late responses after view removal. Final full `npm test`: **140 passed, 0 failed** with loopback access. A sandbox-only run failed in HTTP fixtures with `EACCES` and was not treated as a product regression.
- **Portal UI check:** A portal integration test dispatched the expired-session event and confirmed the protected content clears, the login form appears, the status message is localized, and the stale token is removed. The user-owned browser tabs were left untouched, so live visual confirmation remains open.

### Admin dashboard and dialogs (2026-10-26)

- **Manual UI passed:** On port 3000, the authenticated admin account list loaded. Opened the add-account, role-change and delete-confirmation dialogs; all were centered/readable, with no stray backdrop text. Cancelled each dialog; account count and roles remained unchanged. No account was created, role changed, or account deleted.
- **Manual UI defect reproduced:** Opening the admin dashboard shows a localized load error, four KPI values remain blank, and the revenue chart stays empty. Clicking refresh reproduces the error. Browser console: `ApiError: Endpoint không tồn tại` from `/api/v1/reports/admin-overview`.
- **Backend evidence:** PID 15368 owns 8081 and its classpath is this workspace’s `.run/backend-build/classes`. Runtime `ReportController.class` timestamp 04:34 predates the source controller timestamp 04:48. The source contains the route, but the currently running process was started before that change.
- **Next:** Restart using the guarded project stop script and `scripts/start-backend.ps1 -Background`; verify health, authenticated dashboard values, chart, and console. Then rerun targeted Java/web regressions.

### Admin dashboard follow-up and room image upload persistence (2026-10-26)

- **Dashboard:** Restarted using `start-backend.bat` in the prior QA step and revisited the authenticated dashboard on port 3000. The report, chart and operational summary now load: 5,953,500 ₫ collected, 0% occupancy, 0 pending bookings, 28.6% refund/collection ratio, and one room needing cleaning. No new dashboard console error was recorded. QA-2026-020 is verified.
- **Room image UI:** The add/edit room dialogs use one shared JPEG/PNG upload helper (5 MB maximum), show a preview before submission, and embed the shared room-type price controls in the room dialog.
- **Persistence defect found and fixed:** The first end-to-end save uploaded the image successfully but the room API rejected the resulting managed `/api/v1/media/{uuid}` path. `RoomController` now accepts only that strict UUID route or an absolute HTTP/HTTPS URL. A retry reuses the uploaded URL instead of creating another media asset.
- **Manual persistence verification:** Restarted the backend with `start-backend.bat`; saved the uploaded sample image to QA-801 through the room edit dialog. Reopened the dialog and verified the stored image loaded from `/api/v1/media/9b0e9b49-bfa4-4ce5-8e1b-3bdcd61737e8` with `naturalWidth=1200` and the “Ảnh hiện tại của phòng” status. QA-801 remained at 1,500,000 đ/night and “Cần vệ sinh”.
- **Automated checks:** `RoomImageUrlValidationTest`: 3 passed (managed UUID path and HTTP/HTTPS accepted; other relative paths and protocols rejected). `node --test tests/media-upload.test.mjs`: 2/2 passed; JavaScript syntax checks and `git diff --check` passed. Full `npm test --prefix web-ui` ran 142 tests: 135 passed and 7 failed. Five `login-modal.test.mjs` integration scenarios received the generic connection error or consequently failed the login transition assertion; two `server.test.mjs` cases were blocked by sandbox `connect EACCES` on ephemeral loopback ports.
- **Open cleanup:** The first successful local upload, which preceded the rejected room update, left an unattached media asset (`44baad75-eec8-4eba-a4aa-42c2991bb26a`). Do not delete its file or DB row until MariaDB confirms it is unreferenced by both rooms and room-type images. R2 is now configured and live-verified in the follow-up below; retain the old local file until that reference check is complete.

### Cloudflare R2 room-image integration and live QA (2026-10-26)

- Added a backend Cloudflare R2 S3-compatible adapter using SigV4 for upload, read/proxy, and cleanup. Local disk remains the default; R2 activates only with `HOTEL_MEDIA_PROVIDER=r2`. A blank public base keeps the bucket private and serves via the backend; a configured HTTPS public base returns a direct image URL. No storage credentials are sent to the browser.
- **Manual end-to-end passed:** The operator configured R2 variables in ignored `.env`. Started the local backend with `start-backend.bat`; MariaDB 11.4.13, schema, and vault checks all passed. On port 3000, uploaded the sample `standard.jpg` from the room-edit popup, saved QA-801, reopened the popup, and verified “Ảnh hiện tại của phòng” plus successful image load at 1200 × 900 from the configured R2 public base. Nightly price remained 1,500,000 đ and housekeeping status remained “Cần vệ sinh”.
- **Room-type follow-up:** Uploaded the same sample from the “Hạng phòng” edit popup to QA Standard and reopened it; the saved R2 URL returned the image at 1200 × 900, and “Ảnh phòng mẫu QA Standard” remained as its alt text. No other room-type fields changed.
- **Automated checks:** `R2ObjectStorageTest`: 4 passed; `RoomImageUrlValidationTest`: 3 passed; `node --test web-ui/tests/media-upload.test.mjs`: 2 passed. JavaScript syntax checks and `git diff --check` passed.
- **Still open:** Verify whether the first local upload is unreferenced in `rooms` and `room_type_images` before removing its orphan file/row. R2 live upload and read are verified; the R2 bucket now contains the QA-801 sample image.

### Shared account avatars and dedicated room-type management (2026-10-26)

- **Scope:** Added the same avatar upload/preview/remove flow to Admin, Staff, and Customer account settings. Added Admin room-type create/edit/delete and moved the shared stay-rate editor out of physical-room dialogs into the room-type page. Existing R2-backed media routes are reused.
- **Focused web tests:** `node --test tests/portal.test.mjs tests/room-types.test.mjs tests/account-settings.test.mjs tests/media-upload.test.mjs`: **29 passed, 0 failed**. This covers all three roles, upload/removal, room-type create/edit/delete behavior, disabled deletion for in-use types, and source/public synchronization.
- **Full web suite:** `npm test`: **147 total, 140 passed, 7 failed**. Five `login-modal.test.mjs` scenarios could not reach their local HTTP fixture and consequently showed the generic connection state; two `server.test.mjs` cases failed to connect to ephemeral `127.0.0.1` ports with `EACCES`. Treat these as environment-blocked integration cases; the focused feature tests pass.
- **Backend:** `mvn.cmd -q -DskipTests test-compile` passed. `mvn.cmd -q '-Dtest=RoomTypeControllerTest,AccountSecurityTest' test` passed, covering room-type authorization/input rejection and the avatar field in account profile responses.
- **Database:** The new `users.avatar_url` migration and schema changes compiled and have migration inventory coverage. Live MariaDB migration verification was not completed: the configured app database user could not drop a temporary integration schema, so the integration test aborted during cleanup. No production/runtime schema was intentionally modified in this session; do not retry cleanup without first identifying only test-owned schemas and confirming DB permissions.
- **Browser:** Manual visual review was stopped by the user's physical Escape action. No browser interaction was performed after that stop. The UI changes are therefore automated-test verified but not live-browser verified in this session.
- **Other checks:** `git diff --check` passed. `.env` remains ignored and was not staged; `.env.example` contains only blank R2 placeholders.
- **Status:** Feature tests verified. Full-suite loopback integration and live browser/DB verification remain blocked.

### Room-type icon and responsive rate-card follow-up (2026-10-26)

- **Finding:** The “Hạng phòng” nav referenced an unsupported Lucide icon name, leaving no icon. The shared rate editor used a fixed two-column breakpoint; a single room type therefore occupied only half the available content width in the supplied screenshot.
- **Fix:** Changed the nav icon to `layers`; rate cards now use `repeat(auto-fit, minmax(min(100%, 420px), 1fr))`, with a one-column rule for narrow screens. Root/public JavaScript and CSS copies and asset cache versions were updated together.
- **Verification:** `node --test tests/room-types.test.mjs tests/portal.test.mjs`: **15 passed, 0 failed**. The regression test checks the Lucide icon key, auto-fit grid rule, small-screen single-column rule, and rendered rate-editor container. Source/public synchronization passed.
- **Live UI:** Reloaded the already authenticated admin tab on port 3000. Confirmed the “Hạng phòng” sidebar now shows the layers icon and the single rate card spans the available content width. Narrow-screen one-column behavior is covered by the CSS breakpoint and regression test; a separate narrow-device screenshot was not captured.
- **Status:** Desktop visual and automated-test verified

### Admin room form validation and modal review (2026-10-26)

- Reproduced QA-2026-025 in the authenticated port-3000 admin page: an empty room form produced the browser's English “Please fill out this field” bubble.
- Added a shared inline-validation helper and integrated it into physical-room and room-type forms. Required fields now show Vietnamese inline errors; the first invalid field receives focus, errors clear as inputs are corrected, and invalid forms do not call the API.
- **Focused regressions:** `node --test tests/inline-validation.test.mjs tests/admin-room-validation.test.mjs tests/room-images.test.mjs tests/room-types.test.mjs tests/portal.test.mjs`: **22 passed, 0 failed**.
- **Manual UI:** Submitted an empty add-room modal in both light and dark themes. Verified three field-level errors in Vietnamese, first-field focus, and error clearing after correcting the room number. Cancelled without saving; no database data changed. Restored light theme.
- **Full suite:** `npm test` passed **153/153** when run with loopback access for its local HTTP fixtures. The initial sandbox-only run returned `connect EACCES`; it was environmental and cleared when the test command was allowed local loopback access.

### Live room-type CRUD and backend rebuild (2026-10-26)

- On the authenticated port-3000 room-type page, a valid create attempt returned “Phương thức không được hỗ trợ” and left the form open without creating a row.
- Compared runtime and source timestamps: `.run/backend-build/classes/.../RoomController.class` was built at 06:07 while `RoomController.java` was updated at 06:27. The live process was stale.
- Stopped only the workspace-owned process via `stop-backend.bat`, rebuilt/restarted with `start-backend.bat`, and passed database, schema and vault startup checks. Reloaded the page and confirmed it remained authenticated.
- Manually created a hidden `QA TEMP CRUD 2026-10-26` room type (capacity 1, 100,000 VND, test description/amenities), edited its name/capacity/description/amenities, and deleted it through the in-page confirmation. The UI returned to one original type; the original room association and nightly price remained unchanged. The test type is absent afterward.
- This resolves the live CRUD verification pending under QA-2026-022 and documents the stale runtime as QA-2026-026. The earlier 405 attempt made no database write.

### Room-type icon clarity and responsive price fields (2026-10-26)

- Rechecked the user's screenshot against the authenticated port-3000 page. The sidebar already rendered `layers`; the screenshot's numbered annotation sat over the icon, making it look absent. Replaced it with the more recognizable `door-open` symbol.
- Replaced the stay-rate input row's viewport-based `md:grid-cols-3` with auto-fit columns sized to the card itself (190px minimum). Narrow cards can now flow to two or one columns while wide cards keep all three side by side.
- Updated root/public copies and bumped the portal, stylesheet, and module cache versions.
- Focused regression run `node --test tests/room-types.test.mjs tests/portal.test.mjs`: **15 passed, 0 failed**, including source/public synchronization.
- Reloaded the authenticated page on port 3000 and confirmed the door icon and full-width rate card with all three prices visible. At 720px the fields reflow to two columns and at 390px to one column. Restored the original 1493 × 1224 viewport after testing.

### Shared avatar picker UI review (2026-10-26)

- Replaced the browser's raw English file input control with an accessible “Chọn ảnh” button and localized filename state in the shared Admin/Staff/Customer profile module.
- The file picker continues to validate JPEG/PNG and 5 MB limits, shows a local preview and enables “Lưu ảnh”. “Gỡ ảnh” stays hidden when no saved avatar exists.
- Rebuilt `account-settings-ui.css` from the project's Tailwind/DaisyUI input stylesheet.
- Focused regression command `node --test tests/account-settings.test.mjs tests/room-types.test.mjs tests/portal.test.mjs`: **28 passed, 0 failed**, including cross-role and source/public-copy assertions.
- Live Admin check on port 3000 selected the repository's `standard.jpg`; filename and preview rendered. Reloaded without saving, confirmed the avatar was not persisted; no R2 request or database mutation occurred.

### AI provider model discovery diagnostics (2026-10-29)

- Reproduced from the user's screenshot: model discovery showed a generic 5xx message. A second read-only Admin tab also failed to load saved AI metadata, confirming the error can occur before a provider request.
- `forModels` previously decrypted the old saved configuration even when the admin supplied a complete new key. A complete key/workspace draft now avoids that dependency; saving a complete replacement writes a new encrypted file only after the explicit Save action. Blank-key use still requires reading the saved config.
- Classified backend provider connection I/O failures separately from invalid/oversized provider responses. The frontend surfaces only allowlisted AI diagnostics on model discovery and connection-test routes; generic 5xx details remain hidden elsewhere. Initial config-load failures now explain the draft recovery steps.
- Targeted web regressions: `node --test web-ui/tests/ai-settings.test.mjs web-ui/tests/transport.test.mjs` — **26 passed, 0 failed**.
- Backend regression: `mvn -q -Dtest=AiModelDiscoveryTest test` — **passed**, including simulated corrupted saved-config recovery; only synthetic keys and mocked provider responses were used.
- Local runtime smoke: port 3000 page and versioned assets returned HTTP 200; backend `/api/v1/health` on port 8081 returned HTTP 200 with MariaDB connected. The `start-backend.bat` attempt correctly refused to start a duplicate backend because port 8081 was already occupied.
- No third-party request was sent using the user's key. The exact cause of the local saved-config read failure and provider key/reachability still need confirmation. The UI's new-key recovery path can be checked by retrying after a hard reload and saving only if the model list succeeds.

### AI settings recovery after model selection (2026-10-29)

- Found a UI gating bug: Save required successfully loaded old metadata even after the administrator entered a replacement key and selected a model. This blocked the recovery path already supported by the backend.
- Removed the saved-metadata gate. Save now requires a selected model plus either a new key or the existing key for the same provider. Connection testing stays disabled until the replacement saves successfully; the page now explains this order.
- Updated initial-load error copy so it no longer tells administrators to reload before saving a replacement draft.
- Focused regressions `node --test tests/ai-settings.test.mjs tests/transport.test.mjs`: **26 passed, 0 failed**. Model discovery/save used mocked provider responses and synthetic credentials.
- Did not send a request with the user's API key. Live UI and real provider connectivity remain unverified.

### AI connection-test timeout and status (2026-10-29)

- Reproduced the reported behavior from screenshots: the button remained in a generic “Đang xử lý…” state until the provider timeout at 45 seconds.
- Added a 20-second timeout only for connection tests. Normal guest-generation and model-list requests retain their 45-second timeout.
- The loading status now says that the provider connection is being checked and gives the 20-second maximum; the backend returns a matching actionable timeout message.
- Node AI settings/API regressions: **26 passed, 0 failed**. Two targeted Maven tests passed for the distinct timeout values and message. All provider responses and credentials in these tests are synthetic.
- Did not repeat a real provider request. Live connectivity and visual verification remain pending because the browser automation could not attach to the live page.
- The requested `start-backend.bat` rebuild completed after redirecting Maven's home/repository into the workspace, but startup stopped safely at database preflight: MariaDB localhost:3306 was unavailable. No startup migration ran and no backend process was started.

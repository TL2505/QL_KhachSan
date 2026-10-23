# QA bug report

Session date: 2026-10-23. Environment: Windows 11, JDK 17.0.20.1, Maven 3.10.0, Node.js 24.20.0, MariaDB 11.4.13 on local port 3306, backend 8081, customer UI 3000. The user authorized synthetic changes to the local MariaDB. Payment and refund testing used the mock gateway; no real payment provider or SMTP delivery was used.

## QA-2026-001 — Invoice PDF tests used the retired tax-invoice contract

- **Type:** Confirmed test/build defect
- **Discovered:** 2026-10-23
- **Module / role:** Internal invoice PDF; staff and accounting workflow
- **Severity / priority:** P2 / P2
- **Environment and test data:** Application 1.0-SNAPSHOT, Maven/JDK 17; synthetic hotel, customer, and ledger values only
- **Precondition:** The PDF is an internal payment statement, with provider-issued tax invoices handled separately by MISA meInvoice.
- **Reproduction:** Run the Maven test phase before this fix. `VatInvoicePdfGeneratorTest` and `InvoicePdfControllerTest` call the prior four-field `Hotel` constructor, while the current record has three fields. The generator test also expects the old legal-invoice title and verification URL.
- **Expected:** Tests compile and assert the internal-statement title, synthetic-data label, non-invoice warning, reconciled totals, and embedded Vietnamese font.
- **Actual:** Test compilation failed at both constructor calls; the test expectation also described the retired PDF behavior.
- **Evidence / root cause:** `VatInvoicePdfGenerator.Hotel` now contains legal name, address, and tax code only. The PDF explicitly identifies itself as an internal statement. The tests were not updated with that contract change.
- **Affected files:** `tests/quanlykhachsan/invoice/VatInvoicePdfGeneratorTest.java`; `tests/quanlykhachsan/invoice/InvoicePdfControllerTest.java`.
- **Proposed fix:** Update the fixtures and assertions to the current internal-statement contract while preserving the PDF font/amount checks.
- **Fix implemented:** Updated synthetic fixtures to the three-field record, asserted the current internal-statement wording and absence of a verification URL, and wrote the generated test PDF under `target/qa-artifacts/`.
- **Regression test:** `VatInvoicePdfGeneratorTest.embeddedVietnameseFontAndAuthoritativeTotals`.
- **Verification:** Verified. Maven test phase passed; the invoice test compiled and ran. The database-backed controller test was skipped by its disposable-database guard.
- **Status:** Verified

## QA-2026-002 — Electronic-invoice migration missing from bootstrap manifest

- **Type:** Confirmed database-bootstrap defect
- **Discovered:** 2026-10-23
- **Module / role:** Database bootstrap and electronic invoices; administrator/staff
- **Severity / priority:** P2 / P2
- **Environment and test data:** Application 1.0-SNAPSHOT; static migration inventory; no database was changed
- **Precondition:** `database/migrations/v4_5_electronic_invoices.sql` exists and is applied by the application updater, but the fresh bootstrap manifest drives Docker and local demo setup.
- **Reproduction:** Run `Phase4BootstrapTest.manifestIncludesEveryMigrationExactlyOnce`. Before the fix, the migration-directory inventory contained `v4_5_electronic_invoices.sql` but `database/bootstrap/migrations.txt` did not.
- **Expected:** The bootstrap manifest lists each SQL migration exactly once so fresh bootstrap and demo setup apply the same migration set.
- **Actual:** The manifest omitted the e-invoice tables/events migration.
- **Evidence / root cause:** `database/bootstrap/00-init.sh`, `scripts/FreshDatabaseSetup.java`, and `scripts/DemoDatabaseSetup.java` consume `database/bootstrap/migrations.txt`. The new migration had been added to `UpdateDbUtility` but not to that shared manifest.
- **Affected files:** `database/bootstrap/migrations.txt`; migration file already present at `database/migrations/v4_5_electronic_invoices.sql`.
- **Proposed fix:** Add the missing migration to the shared manifest exactly once.
- **Fix implemented:** Added `v4_5_electronic_invoices.sql` to the manifest after the existing migrations.
- **Regression test:** `Phase4BootstrapTest.manifestIncludesEveryMigrationExactlyOnce`.
- **Verification:** Verified. The Maven suite passed after the manifest update. Applying the migration against MariaDB remains unverified because no local database service was available.
- **Status:** Verified

## QA-2026-003 — POS e-invoice payload omitted MISA's required machine marker

- **Type:** Confirmed provider-contract defect
- **Discovered:** 2026-10-23
- **Module / role:** MISA meInvoice POS issuance; front desk/accounting
- **Severity / priority:** P2 / P2
- **Environment and test data:** Application 1.0-SNAPSHOT; local HTTP fixture with synthetic credentials and invoice data; no MISA account was contacted
- **Precondition:** `MISA_EINVOICE_MODE=POS` and a staff member requests electronic invoice issuance.
- **Reproduction:** Call `MisaMeInvoiceProvider.publish` in POS mode and inspect the posted `OrgInvoiceData`.
- **Expected:** POS request uses MISA's documented code route and has `IsInvoiceCalculatingMachine: true` inside `OrgInvoiceData`.
- **Actual:** The adapter used the documented route but omitted the required marker. It also added a top-level `RefID` not shown in the POS request contract and merged reference fields into the caller's invoice object.
- **Evidence / root cause:** [MISA's POS issuance contract](https://doc.meinvoice.vn/api/Document/InvoicePublishCalculatingMachine.html) requires the marker on `OrgInvoiceData`; the prior adapter built the shared HSM envelope without a POS-specific marker.
- **Affected files:** `src/quanlykhachsan/backend/invoice/MisaMeInvoiceProvider.java`.
- **Proposed fix:** Build the documented POS envelope, set its required marker, and avoid mutating caller-owned invoice data.
- **Fix implemented:** POS payloads now carry the required marker and nested reference ID. Provider-specific fields are applied to a copy of the invoice data, preserving the caller's object.
- **Regression test:** `MisaMeInvoiceProviderTest.posPublicationUsesDocumentedRouteAndInvoiceMarker`; additional fixtures cover HSM code/no-code routes, token reuse and separation, view-link allowlisting, PDF validation and cancellation.
- **Verification:** Verified. The regression test failed with the old missing-marker behavior, then all six provider adapter tests passed after restoring the fix. All calls used synthetic local HTTP responses.
- **Status:** Verified

## QA-2026-004 — Booking cancellation used the browser-native confirmation popup

- **Type:** Confirmed UI defect
- **Discovered:** 2026-10-23
- **Module / role:** Customer booking history; customer
- **Severity / priority:** P3 / P2
- **Environment and test data:** User-provided browser screenshot from `http://127.0.0.1:3000/portal.html?view=bookings`; synthetic booking #133 remained pending and was not cancelled during this check.
- **Precondition:** An unpaid booking has an available “Hủy phòng” action.
- **Reproduction:** Open booking history and click “Hủy phòng”.
- **Expected:** Confirmation appears inside Aurelia's portal UI and can be dismissed without changing the booking.
- **Actual:** The browser blocks the page with a native `confirm()` dialog.
- **Evidence / root cause:** The attached screenshot shows the browser-generated confirmation. `web-ui/js/customer/bookings.js` called global `confirm()` directly; other customer, staff, and admin modules also used browser-native `alert()` and `prompt()` calls.
- **Affected files:** Shared portal dialog helpers; customer booking/concierge, admin, and staff UI modules; portal asset version references.
- **Proposed fix:** Route confirmation, feedback, and text input through the portal's own modal system, with a safe in-page fallback when the modal root is missing.
- **Fix implemented:** The shared helpers now create the in-page modal if needed, support Escape/backdrop dismissal, and provide custom confirm, alert, and prompt dialogs. Application call sites now use those helpers; the cancellation dialog labels the destructive action explicitly.
- **Regression tests:** Syntax check of all edited frontend modules; targeted customer booking voucher and payment-state tests.
- **Verification result:** **Verified:** JavaScript syntax checks and 4 targeted tests passed. On the live user-started portal at `http://127.0.0.1:3000/portal.html?view=bookings`, signed in as the seeded demo guest, clicking “Hủy phòng” for #133 opened the in-page modal with separate “Giữ đặt phòng” and “Hủy đặt phòng” buttons. After adjusting the action layout, both buttons rendered with clear spacing and the destructive action was visually distinct. The modal was dismissed with “Giữ đặt phòng”; booking #133 remained pending. Source scan found no direct browser-native alert/confirm/prompt calls in application code.
- **Status:** Verified

## QA-2026-005 — Registration errors did not identify the invalid fields

- **Type:** Confirmed form-validation defect
- **Discovered:** 2026-10-23
- **Module / role:** Account registration; guest/customer
- **Severity / priority:** P2 / P2
- **Environment and test data:** User-started browser on port 3000; invalid, valid and duplicate synthetic account values only.
- **Reproduction:** Submit registration with a missing/invalid name, email or username, phone, password, or mismatched confirmation; then try a duplicate username.
- **Expected:** The affected control is marked invalid, the specific error appears next to that field, and focus moves to the first invalid control. A duplicate username error should identify the username field.
- **Actual:** Validation feedback was generic and did not consistently point to each field, making corrections hard to locate.
- **Root cause:** Registration validation returned form-level messages instead of accessible field-level state.
- **Affected files:** `web-ui/js/app.js`, `web-ui/public/js/app.js`, and `web-ui/tests/portal-auth-fix.test.mjs`.
- **Fix implemented:** Added per-field messages, red invalid styling, `aria-invalid`/described-by state, first-error focus and duplicate-account feedback. Removed the temporary fake identity insert from the account-creation transaction.
- **Verification:** Verified in the live form for invalid fields, a successful synthetic registration and duplicate username; `portal-auth-fix.test.mjs` passed 9/9 tests.
- **Status:** Verified

## QA-2026-006 — Booking history returned HTTP 500 on a fresh lifecycle schema

- **Type:** Confirmed migration-sequencing defect
- **Discovered:** 2026-10-23
- **Module / role:** Customer booking history and Web lifecycle; customer
- **Severity / priority:** P1 / P1
- **Environment and test data:** User-authorized local MariaDB 11.4.13; synthetic Web bookings only.
- **Reproduction:** Start the backend against a database where `v4_7_web_booking_lifecycle.sql` has not been applied, then request the customer's booking history.
- **Expected:** Runtime startup applies the migration before booking-history queries use its lifecycle fields and tables.
- **Actual:** The history endpoint returned HTTP 500 because the runtime migration path skipped the new lifecycle migration.
- **Root cause:** `UpdateDbUtility` did not include `v4_7_web_booking_lifecycle.sql` in its runtime migration sequence.
- **Affected files:** `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`, `database/migrations/v4_7_web_booking_lifecycle.sql`.
- **Fix implemented:** Added the lifecycle migration to the runtime sequence. Startup applied it idempotently on the authorized local database.
- **Verification:** Booking history returned HTTP 200 in the live portal, showed three synthetic booking states and remained correct after reload.
- **Status:** Verified

## QA-2026-007 — Customer discovery booking controls differed from the landing page

- **Type:** Confirmed UI consistency defect
- **Discovered:** 2026-10-23
- **Module / role:** Customer room discovery and booking; guest/customer
- **Severity / priority:** P3 / P2
- **Environment and test data:** Live browser on `http://127.0.0.1:3000/portal.html?view=discovery`.
- **Reproduction:** Compare the customer portal's room search and booking cards with the landing page's corresponding controls.
- **Expected:** Both entry points use the same search hierarchy, room-card emphasis and booking actions.
- **Actual:** The customer portal presented a different search/filter layout and action styling.
- **Fix implemented:** Matched the landing-page search controls, moved secondary filters into an expandable section, and aligned gold accents on room cards and booking actions.
- **Verification:** Live availability search returned one room; the booking dialog displayed the selected dates, guest count and quote. No additional booking was submitted. Targeted customer booking UI tests passed 3/3.
- **Status:** Verified

## QA-2026-008 — Automatic Web lifecycle shifted MariaDB `DATETIME` values by seven hours

- **Type:** Confirmed date/time binding defect
- **Discovered:** 2026-10-23
- **Module / role:** Automatic Web check-in, checkout reminders and checkout; hotel operations/customer
- **Severity / priority:** P1 / P1
- **Environment and test data:** JDK 17, MariaDB 11.4.13 with `Asia/Bangkok` JVM time and `+07:00` DB session; synthetic booking #3.
- **Reproduction:** Bind `Timestamp.valueOf(LocalDateTime.of(2026,10,23,7,5))` to a MariaDB `DATETIME` comparison. MariaDB cast it as `2026-10-23 00:05`; the booking due at 07:00 was not selected. Binding the same value with `setObject(LocalDateTime)` preserved `07:05` and selected the booking.
- **Expected:** Wall-clock dates stored in `DATETIME` remain unchanged through lifecycle comparisons and `check_in_actual` writes.
- **Actual:** Automatic check-in did not run at the expected simulated wall time; the timestamp write also stored the shifted value.
- **Root cause:** JDBC `Timestamp` binding converted a Vietnam wall-time value through the DB session timezone.
- **Affected files:** `src/quanlykhachsan/backend/booking/WebBookingLifecycleEngine.java`; regression coverage in `tests/quanlykhachsan/backend/payment/PaymentsNotificationsIntegrationTest.java`.
- **Fix implemented:** Switched lifecycle wall-time parameters and `check_in_actual` to `PreparedStatement.setObject(LocalDateTime)`.
- **Verification:** After the fix, a rollback-only MariaDB simulation checked check-in at 07:05, exact stored wall time, the 90% checkout reminder, auto-checkout at 05:01, `vacant_dirty`, a pending cleaning task and both lifecycle notifications. The test transaction was rolled back and booking #3 returned to `confirmed`/`reserved`. The gated disposable-schema regression test was compiled but not run against the runtime database.
- **Status:** Verified

## QA-2026-009 — Staff could not collect counter payment from a Web booking

- **Type:** Confirmed staff workflow defect
- **Discovered:** 2026-10-23
- **Module / role:** Staff booking list and front-desk payment collection; staff
- **Severity / priority:** P1 / P1
- **Environment and test data:** User-started browser on `http://localhost:3000`, backend `http://localhost:8081`, local MariaDB `hotel_runtime_20261022`; synthetic Web booking #4, 1,701,000 VND.
- **Precondition:** A customer has a pending Web booking with `payment_choice='counter'` and an open folio.
- **Reproduction:** Open the staff booking list. The old UI offered a direct “Xác nhận” action; using it was rejected because the reservation had no verified bank payment or authorized folio collection. There was no cash/POS collection action.
- **Expected:** Staff can review the live folio balance, choose cash or POS, and record collection. The backend automatically confirms the booking only after all balances are settled.
- **Actual:** The booking could not be completed through the staff UI. The first reload also lacked the payment-choice field because the running backend had not loaded the Java DTO change.
- **Root cause:** The staff view lacked the existing folio-payment action and exposed a direct confirmation action that violates the payment invariant. The booking list DTO also did not expose `paymentChoice` until the backend was rebuilt.
- **Affected files:** `src/quanlykhachsan/backend/booking/Booking.java`, `src/quanlykhachsan/backend/booking/BookingDAOImpl.java`, both staff booking modules under `web-ui/js` and `web-ui/public/js`, and portal module cache references.
- **Fix implemented:** Exposed the stored payment choice in the booking model, replaced direct confirmation with a counter-collection action for counter bookings, and added a custom modal that loads current folio balances and offers cash/POS. Collection calls use `checkout:false` and an idempotency key; the API owns final confirmation.
- **Verification:** UI verified after restarting with `start-backend.bat` against MariaDB: booking #4 displays “Chờ thanh toán tại quầy”; the modal showed folio balance 1,701,000 VND and both payment methods. The modal was closed without submitting a collection, so no transaction was posted and booking #4 remained pending. The final payment action is deliberately unverified until an authorized cashier confirms receipt of funds.
- **Status:** Fixed-Unverified

## QA-2026-010 — Staff check-in was enabled before the scheduled check-in time

- **Type:** Confirmed staff UI timing defect
- **Discovered:** 2026-10-23
- **Module / role:** Staff booking list; front desk
- **Severity / priority:** P2 / P2
- **Environment and test data:** Live staff UI on port 3000; synthetic confirmed booking #3, scheduled check-in 14:00 and current local time 06:32 on 2026-10-23.
- **Expected:** Disable “Nhận phòng” before the scheduled check-in time and explain when it becomes available. Once the checkout time has passed, the check-in action should be absent.
- **Actual:** The UI offered an enabled “Nhận phòng” button from the start of the check-in date, several hours before the scheduled time.
- **Root cause:** The UI compared calendar dates instead of full reservation timestamps.
- **Affected files:** Both staff booking modules under `web-ui/js` and `web-ui/public/js`, plus portal module cache references.
- **Fix implemented:** The action now compares the current time with the full check-in/check-out timestamps. Before check-in it is disabled with the available start time; at/after check-out it is replaced by an ended-stay note with no action button. Cancelled bookings continue to show no actions.
- **Verification:** Live UI verified for booking #3: at 06:32, its 14:00 check-in button was disabled with the text “Chưa đến giờ nhận phòng. Có thể nhận từ 14:00 23/10/2026.” Cancelled bookings showed no actions. No confirmed booking whose checkout time had already passed was available for a live UI check, so that branch remains unverified.
- **Status:** Fixed-Unverified

## QA-2026-011 — Staff could not distinguish automatic Web reservations from manual front-desk stays

- **Type:** Confirmed staff UI clarity defect
- **Discovered:** 2026-10-23
- **Module / role:** Staff booking list; front desk
- **Severity / priority:** P2 / P2
- **Environment and test data:** Live staff UI on `http://localhost:3000/portal.html?view=bookings`, backend on port 8081, local MariaDB `hotel_runtime_20261022`; synthetic bookings #3 and #4.
- **Expected:** Staff can identify Web bookings whose confirmed stay is handled automatically, and are not offered manual check-in/check-out actions for those bookings. A Web booking that still needs counter collection must continue to show the collection action.
- **Actual:** The staff list did not show the booking channel, so automatic lifecycle bookings were indistinguishable from stays that require reception actions.
- **Root cause:** The booking response did not expose `customer_type` to the staff UI and the view had no channel/lifecycle annotation.
- **Affected files:** `src/quanlykhachsan/backend/booking/Booking.java`, `src/quanlykhachsan/backend/booking/BookingDAOImpl.java`, both staff booking modules and portal admin styles under `web-ui`.
- **Fix implemented:** Exposed `customerType` from the booking DAO. Web bookings now show “Đặt qua Web · hệ thống tự check-in/check-out”; confirmed Web stays show an explicit no-manual-check-in note instead of the reception button, and checked-in Web stays show the automatic checkout note instead of “Trả phòng”. Walk-in bookings are labeled as staff-operated. Web counter bookings retain “Thu tiền tại quầy”.
- **Verification:** Restarted via `start-backend.bat`; live MariaDB-backed UI showed Web labels on bookings #3/#4, no “Nhận phòng” action on confirmed Web booking #3, and the cash-collection action on pending counter booking #4. No payment was posted. Screenshot: `docs/qa/screenshots/staff-web-auto-lifecycle-note.png`.
- **Status:** Verified

## QA-2026-012 — Overdue Web counter bookings did not alert the staff notification center

- **Type:** Confirmed staff workflow gap
- **Discovered:** 2026-10-23
- **Module / role:** Automatic Web checkout and staff notification center; front desk/admin
- **Severity / priority:** P1 / P1
- **Environment and test data:** Local MariaDB 11.4.13, backend on port 8081, staff portal on port 3000; no new overdue booking or payment was created during this check.
- **Precondition:** A Web booking uses counter payment and still has an outstanding folio balance 30 minutes after scheduled checkout.
- **Expected:** Keep the stay in-house, add overdue charges once, mark it overdue and show one notification in the staff center with booking/room/guest/contact/balance details and a checkout action.
- **Actual:** The overdue marker was visible to booking views, but the lifecycle worker had no durable staff-center event or staff notification API.
- **Root cause:** Only customer lifecycle notifications had a persisted event/read model; no staff broadcast event with per-user read state existed.
- **Affected files:** `WebBookingLifecycleEngine`, `OverstayChargeService`, checkout adjustment logic, staff notification API and migration, portal notification tray, staff booking/checkout views.
- **Fix implemented:** Added stable-reference overtime/penalty reconciliation, a 30-minute overdue transition, an idempotent staff alert event and per-user read receipts. Staff roles can read the alert; the portal tray polls for new notifications and links to checkout. The checkout flow reconciles charges against any fees already posted by the lifecycle worker.
- **Verification:** Backend compiled successfully; 3 focused Java lifecycle tests and 4 focused Node tests passed; syntax checks passed for 8 source/public JavaScript modules; `git diff --check` passed. Restarted the backend with `start-backend.bat`, which connected to MariaDB 11.4 and applied the migration. On the live staff portal, the bell opened the “THÔNG BÁO NHÂN VIÊN” center and its empty state loaded without API error. The runtime database was not changed to create an overdue booking, so the real event display remains to be verified against an overdue fixture; the MariaDB integration test requires a disposable schema ending in `_test`.
- **Status:** Implemented; overdue fixture verification pending

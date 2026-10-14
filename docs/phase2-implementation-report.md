# Phase 2 implementation and acceptance report

Verified on 2026-10-15 using Java 17 and an isolated MariaDB 11.4.2 database, `hotel_phase2_test`, on `127.0.0.1:33317`.

**Status: the Phase 2 application implementation and automated regression are delivered. Overall Phase 2 acceptance is partially complete; formal sign-off remains pending the manual checks below.** No production configuration values were invented. No production database was used for verification.

## Delivered behavior

- The native server serves the vanilla booking portal with traversal protection and CORS preflight handling. Guests can search availability, obtain server pricing, verify OTP, reserve atomically, obtain VietQR, and receive confirmation only after server payment polling reports settlement.
- Web and walk-in booking share optimistic allocation. Booking, initial room/service/VAT debits, voucher redemption, and the durable idempotent response commit together. Walk-ins collect identity information and transition to `occupied_clean`.
- OTP codes expire after five minutes, are single-use, and have retry/resend controls. The approved development delivery sink exposes codes only through injected test fixtures, never public API responses or logs.
- Webhooks verify HMAC against the raw bounded body before parsing or database access. Bank transaction replay, applied credit, excess-credit liability, and delivery audit commit together. Overpayments preserve a non-negative folio balance without making a refund promise.
- Housekeeping, maintenance, F&B, and Front Desk privileges are separated. Cleaning transitions log the actor and supplied measured duration/inspection notes. OOO/OOS reservations require the configured manager PIN. Minibar stock and guest-folio charges update atomically.
- Dynamic pricing uses decimal arithmetic and an occupancy matrix per stay. Public previews have a bounded 250 ms cache; booking and voucher financial writes always recalculate directly from PMS inside their transactions.
- Voucher previews require room category and dates, or an authorized booking ID. Client totals never control price. Discounts are capped and applied to room charges before service charge and VAT. Redemption locks the promotion and checks the per-customer limit in the booking transaction; unique booking/folio constraints prevent stacking.
- Paid checkout finalizes the accounting invoice from posted ledger amounts. iText 7 produces Vietnamese PDFs with an embedded DejaVu Sans font, figures, words, and an approved verification URL. PDF access requires financial staff or verified ownership and a settled finalized record.
- Night Audit saves reconciled DBR snapshots. The financial endpoint serves only saved closed-date snapshots; the Swing dashboard shows occupancy/revenue/ADR/RevPAR and exports CSV. Excess-credit liabilities remain separate from receivables reconciliation.
- Desktop requests run through SwingWorker. Added walk-in, VietQR, animated settlement confirmation/chime, housekeeping, minibar, maintenance, invoice viewer, and DBR interfaces. Existing legacy portal assets remain available through `portal.html`.

## Verification evidence

The reproducible command is `mvn clean test -Dtest=Phase2RegressionSuite`. The provided PowerShell wrapper sets isolated synthetic test configuration, requires a `*_test` database, restores its process environment afterward, and rejects skipped acceptance tests.

| Gate | Result |
| --- | --- |
| Clean regression | **109 executed; 0 failures; 0 errors; 0 skipped**, 29.404 s |
| Existing baseline inventory | 34 executed invocations across the 10 test classes checked into the starting Git revision |
| Scenario 9 | 20 competing Web/walk-in requests: exactly 1 booking and 19 conflicts; version increments once; replay does not repost charges |
| Scenario 7 | 50 same-bank-transaction HTTP callbacks: 1 settlement and 49 duplicate confirmations |
| Scenario 8 | Front Desk cleaning forbidden; authorized dirty-to-clean succeeds; illegal occupied-to-vacant-clean rejected |
| Overpayment | Partial, exact, and 150% payment preserve accounting and non-negative balance |
| Minibar concurrency | 20 distinct rooms: exact charges and inventory decrements; pool returns to zero active/waiting connections |
| Pricing load | 10,000 HTTP requests, 50 clients, production-equivalent 20-thread server/filter chain: **P95 6.447 ms; P99 9.968 ms** |
| Pool telemetry | 0 active and 0 waiting connections after load |
| BIGINT parity | Folio ID 3,000,000,000 survives settlement and durable replay |
| Vouchers | Cap and tax order; rollback preserves quota; concurrent one-use quota has one winner; changing client total does not change preview |
| Invoice HTTP | Missing auth and unauthorized departments blocked; owner permitted; other guest forbidden; missing hotel config returns unavailable |
| PDF | Required Vietnamese strings survive PDFBox extraction; fonts embedded; exact totals; rendered page inspected without clipped table content |
| DBR | Exact formulas; zero reconciliation variance; injected 0.01 discrepancy rejected; financial-role and closed-date HTTP guards verified |
| Browser | Mock OTP → voucher → booking → local QR → simulated callback → confirmed receipt; no console warnings/errors; no horizontal overflow at 375 and 1920 px |
| Existing EDT protection | Delayed network response leaves EDT responsive; callbacks run on EDT; synchronous network facade refuses EDT use |

The pricing benchmark runs in a fresh JVM so earlier PDF/font work and short-lived HTTP clients cannot contaminate its resource lifetime. It measures repeated one-night preview requests, including HTTP client/server time, with the preview cache enabled. It is not a claim about all cold-cache dates or deployment hardware.

Evidence is saved in `docs/verification/phase2/`: `clean-regression.log`, `test-summary.json`, `pricing-load.txt`, `synthetic-vat-invoice.pdf`, its rendered PNG, and `browser-confirmation-mobile.png`. All invoice and browser verification data is synthetic.

## Approved decisions and compatibility

- Unique processed webhook row per bank transaction plus separate duplicate-delivery audit, as approved by the user.
- CRC uses the explicitly approved polynomial `0x1021`, initial `0xFFFF`, no reflection. The `123456789` check value is `29B1`; the WBS's “Kermit” label does not describe those parameters.
- Hotel legal identity, verification URL, bank beneficiary, and VAT remain required production configuration. Synthetic values are confined to test fixtures.
- Voucher validation additionally requires authoritative pricing context, as approved. `code` and `voucher_code` are accepted; conflicting aliases are rejected. Anonymous previews are provisional; actual guest eligibility/quota is checked at redemption.
- Required UUIDv4 idempotency applies to booking, minibar, maintenance, and settlement writes. The maintenance request also needs a real future `estimated_finish` window for reservation conflict checks. PIN can be supplied in the payload or `X-Manager-Pin` header.
- Tests use the repository's configured `tests/` source root rather than adding JUnit code to production `src/`. The WBS's missing `StaffForm.java` is integrated through the actual `MainUI.java`.
- Week 8 migration adds explicit nullable voucher policy fields and redemption records, plus customer company/tax fields. A promotion with missing policy configuration fails closed; migration does not invent marketing policy.
- Folio IDs remain BIGINT through booking, payments, routing, POS, Night Audit, and desktop facades. New payment references use booking-based codes short enough for VietQR.
- VietQR requires whole-dong payable amounts and references up to 25 characters. Fractional balances or unsupported legacy references return an error for Front Desk review; they are never silently rounded or rewritten.
- DBR reconciliation uses cumulative debits/credits and closing receivables so opening balances are accounted for. Daily revenue and separate excess-credit liability are included in the saved snapshot.

## WBS coverage and pending acceptance

| Tasks | Implementation / verification |
| --- | --- |
| A5.1–A5.4; B5.1–B5.3 | Implemented; static/auth/migration/allocation/CRM/booking tests pass |
| C5.1–C5.2 | Implemented; browser flow and responsive widths verified; full-page 500 ms render gate remains unmeasured |
| C5.3 | Implemented; backend atomic flow verified; native shortcut/grid timing pending |
| D5.1–D5.3 | Mock contracts and concurrency/security gates pass |
| A6.1–A6.3; B6.1–B6.3 | Implemented; QR/HMAC/ownership/payment/migration tests pass |
| C6.1–C6.2 | Implemented; native QR timing, animation, and audible chime verification pending |
| C6.3 | Implemented; browser QR and settlement polling verified |
| D6.1–D6.3 | Mock dispatcher, duplicate webhook, and overpayment tests pass |
| A7.1–A7.3; B7.1–B7.3 | Implemented; migration/state/RBAC/minibar/maintenance tests pass |
| C7.1–C7.3 | Implemented; native visual/timing checks pending |
| D7.1–D7.3 | Mocks, Scenario 8, and 20-room HTTP stress tests pass |
| A8.1–A8.4; B8.1–B8.4 | Implemented; authoritative pricing/vouchers/PDF/DBR tests pass |
| C8.1 | Implemented; system PDF reader launch/timing pending |
| C8.2 | Implemented; browser voucher discount and occupancy price display verified |
| C8.3 | Implemented; native KPI visual/300 ms interaction gate pending |
| D8.1–D8.3 | Mock endpoints, 50-client load, and PDF Unicode gates pass |
| D8.4 | Clean automated regression passes; overall Go/No-Go remains pending manual evidence |

The remaining corrective steps are:

1. On the installed Swing desktop, exercise Ctrl+W, QR settlement/chime, dirty-room cleaning, minibar posting, maintenance PIN, VAT reader launch, and DBR/CSV. Measure the specified EDT/visual timings; background-worker tests alone do not prove every 16 ms UI target.
2. Measure full-page web rendering under the specified 500 ms target. The browser automation surface exposes DOM/layout verification but does not expose the Navigation Timing API used for this measurement.
3. Build/run the Maven-based Docker image and health check on a host with Docker. Docker is not installed/on PATH in this environment, so this is unverified rather than marked passed.
4. Supply the approved production configuration and explicit voucher policies before live use. Connect an approved real OTP delivery provider if production SMS/email delivery is desired; this request authorized the development/test mock only.
5. The PDF is a printable representation of finalized accounting data. Tax-provider issuance/signing and the approved verification destination must be provided and validated before claiming live electronic-invoice legal issuance. No tax registration, digital signature, or legal sign-off is fabricated.

The WBS's asserted “47 Phase 1 baseline tests” does not match the starting repository inventory (34 executed invocations across 10 checked-in test classes). The suite selects every available checked-in test. Reconcile this count with the WBS or supply any missing historical test files; 13 tests are not fabricated to claim a 47-test baseline.

## Reproduction

For the optional task-created portable test runtime (retained in ignored `.phase2-runtime/`):

```powershell
./scripts/start-phase2-testdb.ps1
./scripts/test-phase2.ps1 -Offline
./scripts/stop-phase2-testdb.ps1
```

Alternatively configure a disposable MariaDB 11 schema from `database/hotel.sql` using a distinct `*_test` name and set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD`; run `./scripts/test-phase2.ps1`. The migration tests additionally create/drop their own temporary schemas, so the test account needs schema-creation privileges. Do not use this runner with production data.

The development mock is `quanlykhachsan.stubs.MockApiServer` in the test source set and binds only localhost port 8089. It is never included in production source/classes. Its fixed OTP and SUMMER50 coupon are synthetic fixtures, not production credentials/policies.

See `phase2-production-configuration.md` for required settings and `phase2-changed-files.md` for the complete changed-file inventory. Temporary test processes are stopped after verification; the portable runtime is ignored and is not part of the source deliverable.

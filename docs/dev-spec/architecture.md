# SYSTEM ARCHITECTURE & DATA FLOW — 5★ HOTEL PMS

> **Platform layer:** Desktop (Swing, Staff, Phase 1-3) + Web (React, Guest/Admin, Phase 3+) → both consume `/api/v1/*` from same Java backend. Principle: Platform = where user sits. See `tech-stack.md` § PLATFORM DESIGN PHILOSOPHY.


## 1. 12-ROW DATA FLOW MATRIX
| Source | Target | Trigger | Action |
| :--- | :--- | :--- | :--- |
| `BOOK-02` (Booking) | `ROOM-01` (Inventory) | Confirm Booking | Deduct available room count for dates |
| `ROOM-01` (Inventory) | `BILL-02` (Folio) | Night Audit | Post daily room charge to master folio |
| `SERV-01` (Service) | `BILL-02` (Folio) | Order placed | Post incidental charge to guest folio |
| `BILL-02` (Folio) | `ROOM-02` (Assignment) | Payment confirmed | Room released |
| `CRM-01` (Guest) | `BOOK-02` (Booking) | New Booking | Link booking to existing guest profile |
| `PROMO-01` (Promo) | `BOOK-02` (Booking) | Apply Code | Recalculate total before confirming |
| `CRM-02` (Loyalty) | `BOOK-02` (Booking) | VIP Login | Auto-apply member discount rate |
| `CARE-02` (Feedback) | `CRM-01` (Guest) | Checkout | Update guest sentiment score |
| `BILL-02` (Folio) | `ROOM-02` (Assignment) | Full Payment | Trigger check-out / release room |
| `ROOM-02` (Assignment) | `BOOK-02` (Booking) | Room status OOO | Overbooking risk / booking block |
| `SYS-01` (Auth) | `ALL MODULES` | API Request | Validate JWT and RBAC permissions |
| `NIGHT AUDIT` | `BILL-02`/`ROOM-02` | 00:00 Cron | Roll business date, post charges, reset status |

## 2. NIGHT AUDIT WORKFLOW
**Schedule:** Daily at 00:00 System Time.
**Lock Rule:** The PMS enters a "Frozen" state from 00:00 to 00:05. Manual API writes (e.g., staff check-in, new bookings) return `503 Service Unavailable` with `Retry-After: 300`. System-automated charges (room posting, tax, POS recon) continue to run internally.
1. **Lock System:** Prevent concurrent manual writes (Freeze API).
2. **Verify Arrivals/Departures:** Flag No-Shows and Overstays.
3. **No-Show Penalties:** Post no-show fees to guaranteed bookings.
4. **Post Room Charges:** Apply daily rate to active folios.
5. **Calculate Taxes:** Post VAT and City Tax.
6. **Post Recurring Services:** Apply daily parking/breakfast fees.
7. **Reconcile POS:** Sync F&B store-and-forward transactions.
8. **Statistics & Reports:** Update ADR, Occupancy, RevPAR, and generate DBR (Daily Business Report).
9. **Roll Date:** Advance business date from Day N to Day N+1.
10. **Unlock System:** Resume API services.

## 3. EDGE CASES IN DATA FLOW
- **POS Offline:** POS must queue charges locally during the 00:00 Lock.
- **Race Condition:** Two bookings for the last room handled via Optimistic Locking on `ROOM-01`.
- **Idempotency:** Payment webhooks to `BILL-01` require an `Idempotency-Key` to prevent double billing.

## 4. NIGHT AUDIT DATABASE ISOLATION & "ZERO CONNECTION POOL" ARCHITECTURE
- **Strict Isolation Commitment:** During the 00:00 – 00:05 Night Audit Lockout, the HikariCP database connection pool is isolated and dedicated to heavy end-of-day batch audit procedures (folio postings, date roll, report aggregation).
- **Zero Pool Consumption on Intercepted Requests:** All mutating requests (`POST`, `PUT`, `DELETE`, `PATCH`) are intercepted by `SystemStatusFilter` at the filter boundary, returning `HTTP 503 Service Unavailable` with `Retry-After: 300` in $< 1\text{ms}$ with zero database connection consumption.
- **Authentication Exemption & In-Memory Cache Verification:**
  - Route `/api/v1/auth/login` is exempt from blanket 503 filtering to support night-shift operations (read-only audit monitoring).
  - To eliminate pool contention with Night Audit transactions, `AuthController` resolves credentials via an in-memory cache (`CREDENTIAL_CACHE`) with **zero HikariCP pool allocation**.
  - Uncached user credentials during the freeze window are safely rejected with `503 ERR_503_SYSTEM_FROZEN` without touching the database pool, strictly preserving pool isolation.

## 5. REAL-WORLD DOMAIN GOVERNANCE & LEDGER INVARIANTS (ADR-06 & CAS DECOUPLING)
- **Decoupled Room Availability vs Physical Status:**
  - `rooms.status` represents today's current physical state of the room (`vacant_clean`, `vacant_dirty`, `occupied_clean`, `occupied_dirty`, `out_of_order`, `reserved`).
  - Same-day bookings require `status = 'vacant_clean'` and transition room to `reserved`.
  - Future bookings only require `status != 'out_of_order'` (room not broken/out of order) with no schedule conflicts in `bookings`. The room's physical status for today is preserved (`vacant_dirty`, `occupied_clean`, etc.), and `version` increments atomically for optimistic concurrency control.
- **ADR-06 Ledger Immutability & Zero Phantom Debt:**
  - `folios.balance` strictly matches ledger transactions ($\sum \text{CHARGE} - \sum \text{PAYMENT}$).
  - When a reservation is cancelled before stay commencement, associated folios transition to `status = 'cancelled'` / `'void'` and balance is zeroed (`0.00`), eliminating phantom debt and unblocking folio closure (`closeFolio()`).
  - Cancelling a future booking on an occupied room preserves today's physical room status (`occupied_clean` / `vacant_dirty`) via `CASE WHEN status = 'reserved' THEN 'vacant_clean' ELSE status END`.
- **Synchronous Business Date Rollover:**
  - `SystemStateHolder.rollNextBusinessDate()` writes directly and synchronously to the `system_settings` table to prevent date rollback upon unexpected server/container restart.

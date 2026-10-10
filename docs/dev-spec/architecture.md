# SYSTEM ARCHITECTURE & DATA FLOW — 5★ HOTEL PMS

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

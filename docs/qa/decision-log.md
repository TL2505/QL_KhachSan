# QA decision log

## DL-2026-001 — Keep the internal payment statement separate from MISA tax invoices

- **Date:** 2026-10-23
- **Area:** Invoice PDF and electronic-invoice workflow
- **Evidence:** `VatInvoicePdfGenerator` labels its PDF an internal payment statement and removes the simulated verification URL. `docs/electronic-invoices.md` describes the separate provider-issued MISA document and explicitly distinguishes the two records.
- **Decision:** Align regression tests and API documentation with this existing separation. Do not label the internal PDF as a legally issued e-invoice or add a fabricated verification link.
- **Business rule changed:** No. This QA session made no change to tax rates, invoice amounts, or provider issuance behavior.
- **Approval needed:** None for test/document alignment. Production issuance still requires the seller's registered MISA mode and accounting configuration documented in the operations guide.

## DL-2026-002 — Cite the current e-invoice implementation circular

- **Date:** 2026-10-23
- **Area:** Electronic-invoice operations guidance
- **Evidence:** The Government's legal portal lists Decree 254/2026/NĐ-CP and Circular 91/2026/TT-BTC with an effective date of 01/07/2026. The circular's stated scope is guidance under the Tax Administration Law and Decree 254 for electronic invoices and documents.
- **Decision:** Cite both instruments together in the technical handoff. Keep seller-specific applicability, tax rate, registered invoice mode, invoice form and signing requirements as accounting/provider confirmations.
- **Assumptions:** The guide explains integration configuration and does not determine whether a hotel seller qualifies for a particular invoice route or exception.
- **Approval needed:** No approval to update the public legal references. Accounting/tax-agent confirmation is still required before production issuance.

## DL-2026-003 — Separate Web QR expiry from booking and front-desk workflows

- **Date:** 2026-10-23
- **Area:** Reservation lifecycle, QR payment and cancellation
- **Evidence:** The customer needs to reopen an active QR transaction from booking history after leaving the payment screen. Walk-in reservations are staff-operated, while Web bookings can choose counter payment or QR.
- **Decision:** Start a Web QR session at booking creation and keep that same provider transaction recoverable from booking history for one hour. After the QR deadline, retain the room for a two-minute webhook reconciliation grace. Accept a verified callback only when the provider transaction time is within the QR deadline; then automatically confirm the fully paid Web reservation. If no payment arrives, automatically cancel the unpaid Web QR reservation, post a balancing folio adjustment, close its folio, expire its QR link and release its room. Preserve the original records for audit.
- **Counter and walk-in behavior:** Web counter bookings do not use QR expiry. Walk-in bookings remain staff-operated and are not automatically expired by the Web QR job.
- **Cancellation:** An unpaid future reservation with no invoice can be cancelled by reversing its outstanding folio balance through an audited adjustment, closing the folio, cancelling its active payment link and releasing the room. Reservations with any recorded payment or invoice require front-desk reconciliation and refund handling.
- **Late and partial payment:** A callback outside the provider timestamp window or after cancellation is recorded as a late/reconciliation result and does not confirm the booking. A partial collection is retained for staff reconciliation instead of silently releasing the booking.
- **Approval needed:** None for the local business workflow. A production refund policy and provider-specific cancellation/refund behavior remain a separate business decision.

## DL-2026-004 — Gate staff check-in by the scheduled stay timestamps

- **Date:** 2026-10-23
- **Area:** Staff booking list and front-desk check-in
- **Evidence:** The live staff list showed “Nhận phòng” enabled at 06:32 for a reservation scheduled to start at 14:00 the same day. The user asked to hide or disable the action before the scheduled time with an explanation, and to hide it after the stay has ended.
- **Decision:** Enable check-in only from the scheduled check-in timestamp until the scheduled check-out timestamp. Before the window, keep the button disabled and show the start time. At or after check-out, show the ended-stay message without a check-in button. Cancelled bookings have no booking actions.
- **Approval needed:** None; this is the explicit UI rule requested for this workflow.

## DL-2026-005 — Show the booking channel and reserve lifecycle actions for Web stays to the system

- **Date:** 2026-10-23
- **Area:** Staff booking list and automatic Web booking lifecycle
- **Evidence:** `WebBookingLifecycleEngine` selects `customer_type='Web'` for automatic stay processing. The staff list previously omitted the booking channel and showed the same reception actions for Web and front-desk stays.
- **Decision:** Carry the persisted booking channel through the booking DTO. Clearly label Web bookings as system-managed and suppress manual check-in/check-out actions for Web stays. Keep staff collection available for Web bookings that chose counter payment; payment receipt remains a staff action. Label Walk-in bookings as staff-operated.
- **Approval needed:** None; this follows the user-requested channel-specific workflow.

## DL-2026-006 — Alert staff when an unpaid Web counter stay becomes overdue

- **Date:** 2026-10-23
- **Area:** Web counter payment, late checkout, staff notification center
- **Evidence:** The guest can keep occupying a Web reservation after its scheduled checkout while an unpaid counter balance remains. Staff need the booking, room, guest contact and outstanding balance to collect payment and complete checkout.
- **Decision:** Keep the reservation checked in through the first 30 minutes after checkout. If the counter balance remains, mark it overdue, post idempotent hourly overstay charges and create one durable staff notification linked to the checkout screen. Charge each started hour after the free period and add one hourly penalty when six hours have elapsed. Staff read state is per account. QR-paid Web reservations continue through automatic checkout; front-desk stays remain staff-operated.
- **Approval needed:** None; the user requested the overdue staff alert and confirmed the grace/fee rules.

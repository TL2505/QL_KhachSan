# 3RD PARTY INTEGRATIONS & APIs

## 1. OTA (Online Travel Agencies: Booking.com, Agoda)
- **Protocol:** REST JSON (Modern) / HTNG (Legacy fallback).
- **Sync Frequency:** Real-time push via Webhooks; 15-minute fallback polling.
- **Rate Parity Check:** Automated check before pushing updates to prevent underselling.
- **Error Handling:** Dead Letter Queue (DLQ) for failed syncs; retry with exponential backoff.

## 2. PAYMENT GATEWAY (Stripe / Local VN Gateway)
- **Integration Method:** API (Server-to-Server) for tokenization + Hosted Redirect for 3DS verification.
- **Idempotency:** Required. `Idempotency-Key` header enforced on all `POST /payments`.
- **Webhook:** Asynchronous payment confirmation (`payment_intent.succeeded`).
- **Error Handling:** If webhook fails, run a reconciliation cron job every hour.

## 3. POS (Point of Sale - F&B)
- **Integration:** Direct REST API to `BILL-02` (Post Charge).
- **Offline Mode:** POS must support Store & Forward. If PMS is offline (e.g., Night Audit Lock), POS queues charges locally and pushes when a 200 OK heartbeat is received.
- **Reconciliation:** End-of-shift Z-read syncs total revenue against PMS posted folios.

## 4. SMS / EMAIL (CARE-03)
- **Provider:** Twilio (SMS) / SendGrid (Email).
- **Template Management:** Stored in PMS; AI triggers template ID, not raw text.
- **Rate Limiting:** Max 3 SMS per guest per day to prevent spam.
- **Auth & Format:** Bearer Token, JSON payload. Retries max 3 times on 5xx errors.

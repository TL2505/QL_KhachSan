# DATA ACCESS CONTROL & RBAC

## 1. GUEST ROLE (External via Chatbot/App)
- **READ:** Own profile, Own bookings, Own folio, Public room availability, Service menu, Active promotions.
- **WRITE:** Write own reviews, Create service request drafts, Create booking drafts (requires check-out flow to complete).
- **NEVER ACCESS:** Other guests' PII, Staff P&L, HR, Internal rates, Audit logs.
- **ENFORCEMENT:** Middleware rejects queries if `guest_id != authenticated_token_id`.

## 2. STAFF ROLE SCOPE
- **Front Desk:** Access Bookings, Folios, Profiles, Room Status (Read/Write). Read-only for Promotions. Cannot access F&B P&L.
- **Housekeeping:** Access Room Status, Task List, Maintenance (Read/Write). Cannot access Folios or Room Rates.
- **F&B / POS:** Access Guest Name & Room Number for charging. Cannot access full CRM profiles.
- **Marketing:** Access Promotions (Read/Write), Reviews (Read all).
- **IT / DevOps:** Access AI Gateway logs, system health, Night Audit progress, Webhook configs (Read/Write). Cannot access Guest PII or Folios.
- **Compliance / QA:** Access Audit Logs (Read), AI Interaction Logs (Read), Violation Reports (Read). Cannot edit any operational data.
- **Admin/GM:** Full access (Includes overrides, Audit Logs, Night Audit).

## 3. NIGHT AUDIT ACCESS
- **Trigger:** Automatic (Cron at 00:00) or manual by Night Auditor.
- **Abort:** Admin/GM only (Requires 2FA).
- **View:** Night Auditor, Admin, GM (Read-only dashboard).

## 4. DATA FRESHNESS & CACHE TTL
- **Room Availability & Status:** REAL-TIME ONLY (TTL = 0s) to prevent overbooking.
- **Room Rates & Promotions:** Max TTL = 5 minutes.
- **Guest Profiles:** TTL = 1 hour (Clear cache on check-in/out).

## 5. AUDIT LOG ACCESS
- Every AI interaction is logged (Timestamp, User ID, AI Action, Reference Data).
- Access restricted to Admin & Compliance roles.
- Retention period: 6 months (GDPR/PDPA compliant).

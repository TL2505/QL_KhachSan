# DATA ACCESS CONTROL & RBAC

## 1. GUEST ROLE (External via Chatbot/App)
- **READ:** Own profile, own bookings, own folio, public room availability, service menus, active promotions.
- **WRITE:** Own reviews, service request drafts, booking drafts (checkout flow required).
- **NEVER ACCESS:** Other guests' PII, Staff P&L, HR, internal rates, audit logs.
- **ENFORCEMENT:** Middleware rejects queries where `guest_id != authenticated_token_id`.

## 2. STAFF ROLE-BASED SCOPE
- **Front Desk:** Access Reservations, Folios, Profiles, Room Status (R/W). Read-only for Promotions. Cannot access F&B P&L.
- **Housekeeping:** Access Room Status, Task Sheets, Maintenance (R/W). Cannot access Folios or Rates.
- **F&B / POS:** Access Guest Name & Room Number for charge posting. No access to full CRM profile.
- **Marketing:** Access Promotions (R/W), Reviews (Read all).
- **IT / DevOps:** Access AI Gateway logs, system health, Night Audit status, Webhook configs (R/W). Cannot access Guest PII or Folios.
- **Compliance / QA:** Access Audit Logs (Read), AI Interaction Logs (Read), Violation Reports (Read). Cannot modify any operational data.
- **Admin/GM:** Full access (Override, Audit Logs, Night Audit).

## 3. NIGHT AUDIT ACCESS
- **Trigger:** Automated (Cron at 00:00) or manual by Night Auditor.
- **Abort:** Admin/GM only (2FA required).
- **View:** Night Auditor, Admin, GM (Read-only dashboard).

## 4. DATA FRESHNESS & CACHE TTL
- **Room Availability & Status:** REAL-TIME ONLY (TTL = 0s) to prevent overbooking.
- **Rates & Promos:** Max TTL = 5 minutes.
- **Guest Profile:** TTL = 1 hour (Cache invalidated on check-in/out).

## 5. AUDIT LOG ACCESS
- Every AI interaction is logged (Timestamp, User ID, AI Action, Referenced Data).
- Access restricted to Admin & Compliance roles.
- Retention: 6 months (GDPR/PDPA compliant).

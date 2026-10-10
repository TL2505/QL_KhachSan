# FINAL DEPLOYMENT PLAN — 5★ HOTEL PMS
*Consolidated from: Gap Analysis + 17-file spec + Current system audit*

## 0. VERDICT
- **Production ready?** NO.
- **Internal demo ready?** YES — after 2 quick fixes (JWT + error format, ~2h).
- **Full 5★ ready?** Phase 4 (Week 12-16).

## 1. PHASE 0: QUICK FIXES (Day 1-2, ~4h total)
*Make current system safe enough to demo internally.*

| # | Fix | Effort | File affected |
|---|---|---|---|
| 1 | Add JWT to all endpoints (currently 0 auth) | 2h | docker-compose, backend |
| 2 | Standard error format + HTTP codes | 1h | backend |
| 3 | Remove DB port exposure (3307 → internal only) | 10min | docker-compose.yml |
| 4 | Move password to .env | 10min | docker-compose.yml |
| 5 | Add UFW (allow 22, 80 only) | 10min | OS |

**After Phase 0:** System is safe for internal demo. NOT for real guests.

## 2. PHASE 1: CORE PMS (Week 1-4)
*Build the foundation. 3 containers.*

### Containers:
| Container | Port | Exposed? |
|---|---|---|
| Nginx (TLS) | 443/80 | Yes |
| Java Backend (Spring Boot 17) | 8081 | Internal only |
| MariaDB 11 | 3306 | Internal only |

### Scope (10 endpoints):
- POST /api/v1/auth/login (JWT, 1h expiry)
- GET /api/v1/rooms/availability?start=&end=&type=
- GET /api/v1/rooms (paginated)
- PUT /api/v1/rooms/{id}/status (RBAC transitions)
- POST /api/v1/customers
- POST /api/v1/bookings (Idempotency-Key)
- GET /api/v1/bookings?date=&type=
- POST /api/v1/bookings/{id}/cancel
- POST /api/v1/folios/{id}/pay (Idempotency-Key)
- GET /api/v1/health

### Room Status (6 states + RBAC):
| Role | Allowed Transitions |
|---|---|
| HOUSEKEEPING | VD→VC, OD→OC, any→OOO |
| FRONT_DESK | VC→RESERVED, RESERVED→OC, OC→VD |
| ADMIN | any→any |

### Night Audit (CRITICAL — build in Phase 1):
- 7 steps: Lock → Verify → Post Room → Post Tax → Reconcile POS → Roll Date → Unlock
- Lock = 503 + Retry-After: 300
- @Scheduled 00:00 (Spring)
- Tax: VAT 10% + City Tax (per local regulation)
- No-Show: auto-post 1-night penalty

### Security:
- [ ] JWT + RBAC middleware
- [ ] DB internal only
- [ ] .env (not in git)
- [ ] UFW (22, 80, 443)
- [ ] SSH key only + fail2ban
- [ ] HTTPS (self-signed dev / Let's Encrypt prod)

### Go/No-Go → Phase 2:
- [ ] All 10 endpoints pass Postman test
- [ ] Night Audit runs successfully 3 consecutive nights
- [ ] 0 PII accessible without JWT
- [ ] Room status transitions enforced (test: HK tries to set RESERVED → 403)

## 3. PHASE 2: AI + OPERATIONS (Week 5-8)
*Add AI Gateway + advanced folio. 5 containers.*

### New Containers:
| Container | Port | Exposed? |
|---|---|---|
| pgvector (PostgreSQL 16) | 5432 | Internal only |
| AI Gateway (Python 3.11, FastAPI) | 8082 | Internal only |

### New Endpoints (15):
- POST /api/v1/auth/guest (OTP verify → scoped token)
- POST /api/v1/bookings/{id}/check-in (optional roomId, deposit)
- POST /api/v1/bookings/{id}/check-out (balance check, POSTPAID exception)
- POST /api/v1/services/request
- POST /api/v1/bookings/group (master folio + billing routing)
- PUT /api/v1/folios/{id}/routing
- POST /api/v1/folios/{id}/split
- GET /api/v1/folios/{id}/interim
- POST /api/v1/ai/chat (guardrails applied)
- POST /api/v1/ai/escalate (warm transfer + SLA)
- GET /api/v1/ai/interactions?date=&page=&size= (ADMIN only)
- POST /api/v1/system/night-audit/execute (ADMIN/AUDITOR only)
- POST /api/v1/system/night-audit/abort (ADMIN only, 2FA)
- GET /api/v1/reports/dbr
- GET /api/v1/reports/occupancy

### AI Gateway Architecture:
`Request → Input Guardrail → RAG (pgvector) → LLM (gpt-4o-mini) → Output Guardrail → Escalation Check → Audit Log → Response`

### Guardrails (code-level, NOT just prompt):
- Price validation: response must match PMS rate (regex + API cross-check)
- PII redaction: block any guest_id ≠ authenticated_id
- Keyword blocklist: refund, discount, free, upgrade
- Confidence < 80% → auto-fallback
- Prompt injection: pattern match → ignore + log

### Go/No-Go → Phase 3:
- [ ] AI violation rate < 0.1% over 7 days
- [ ] Escalation SLA > 95% (2min ack)
- [ ] 0 PII leak in 1000 interactions
- [ ] All 6 test scenarios pass (group, overbooking, POS offline, injection, race, bypass)
- [ ] Night Audit + POS offline scenario passes

## 4. PHASE 3: SCALE & INTEGRATION (Week 9-12)
*OTA, payment, POS, reporting. 6+ containers.*

### New Endpoints (15+):
- POST /api/v1/promotions (Marketing/Admin)
- GET /api/v1/promotions/validate
- GET /api/v1/loyalty/{customerId}
- POST /api/v1/loyalty/{customerId}/adjust
- POST /api/v1/feedback (guest review)
- POST /api/v1/notifications/send (SMS/Email)
- POST /api/v1/staff
- PUT /api/v1/staff/{id}/roles
- POST /api/v1/webhooks/subscribe (HMAC-SHA256)
- POST /api/v1/webhooks/receive (OTA/Payment inbound)
- GET /api/v1/reports/revenue-by-channel
- GET /api/v1/reports/revpar

### Integrations:
| System | Protocol | Sync |
|---|---|---|
| Booking.com / Agoda | XML (HTNG) + Webhook | Push every 5min + event-driven |
| Payment (Stripe/local) | REST API + Webhook | Real-time |
| POS | Direct API + Store & Forward | Real-time + Night Audit recon |
| SMS/Email | Provider API (Twilio/local) | Event-driven |

### Webhook Events (8):
reservation_created, reservation_cancelled, check_in_completed,
check_out_completed, room_status_changed, payment_captured,
night_audit_completed, no_show_processed

### Go/No-Go → Production:
- [ ] OTA sync: 0 overbooking in 7 days
- [ ] Payment: 0 double-charge in 100 transactions
- [ ] AI CSAT ≥ 4/5 over 2 weeks
- [ ] Uptime > 99.5%
- [ ] Backup: 30 days retained, restore tested

## 5. OPERATIONS (ongoing)

### Daily:
- Night Audit auto-runs 00:00
- Backup: cron 02:00 (mysqldump → /backups/, 30-day retention)
- Monitoring: Grafana dashboard (violation rate, SLA, AHT, uptime)

### Weekly:
- AI interaction log review (100 random samples)
- Violation report → update constraints.md if needed
- CHANGELOG.md review

### Monthly:
- AI API key rotation
- Security scan (OWASP ZAP)
- Load test (50 concurrent + 10 staff)

### Incident Response:
- Tier 1 (PII leak / wrong price): KILL SWITCH → IT + GM immediately
- Tier 2 (wrong policy / AI offline): Degrade to static FAQ
- Tier 3 (high fallback rate): Alert IT, monitor

## 6. TEAM & RACI

| Task | You (Business) | Dev | AI (tool) |
|---|---|---|---|
| Constraints & rules | **R/A** | C | A |
| API build | C | **R** | A |
| AI Gateway build | C (spec) | **R** | A |
| Guardrails (code) | C (spec) | **R** | A |
| Test scenarios | **R** (logic) | R (code) | A |
| Pilot monitoring | **R** | C | — |
| Go/No-Go decision | **A** | C | — |

## 7. TIMELINE SUMMARY

| Phase | Duration | Deliverable |
|---|---|---|
| Phase 0 (Quick Fix) | 2 days | Safe internal demo |
| Phase 1 (Core PMS) | 4 weeks | 10 endpoints + Night Audit |
| Phase 2 (AI + Ops) | 4 weeks | AI Gateway + 15 endpoints |
| Phase 3 (Scale) | 4 weeks | OTA + Payment + Reporting |
| **TOTAL** | **~12 weeks + 2 days** | **Production ready** |

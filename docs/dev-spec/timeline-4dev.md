# PROJECT TIMELINE — 4 DEV TEAM (5★ HOTEL PMS + AI)

## Team Roles
| Dev | Role | Focus |
|---|---|---|
| Dev A | Backend API | Endpoints, JWT, RBAC, Payment |
| Dev B | Business Logic | Night Audit, Folio, Group Booking, AI Gateway |
| Dev C | Frontend | React/Vue, Staff Dashboard, Guest Chat UI |
| Dev D | DevOps + QA | Docker, CI/CD, Testing, Security, Monitoring |

## Phase 0: Quick Fixes (Day 1-2)
| Dev | Task |
|---|---|
| Dev A | JWT on all endpoints + Standard error format + HTTP codes |
| Dev B | DB schema design + Docker skeleton (docker-compose.yml) |
| Dev C | React/Vue project setup + Nginx config + TLS (self-signed) |
| Dev D | .env + .gitignore + UFW (22,80,443) + SSH key only + fail2ban |

**Milestone (Day 3):** Phase 0 complete → Internal demo safe.

## Phase 1: Core PMS (Week 1-4)
| | Week 1 | Week 2 | Week 3 | Week 4 |
|---|---|---|---|---|
| Dev A | Auth, Room, Availability, Customer, Booking endpoints | Cancel, Folio, Payment, Health, List endpoints | Integration test with Dev B | Go/No-Go test |
| Dev B | Night Audit 7 steps + Lock Rule | Night Audit edge cases (POS offline, midnight C/O, No-Show) + Tax | Group booking + Folio routing/split/interim | Integration test |
| Dev C | Login UI + Room list + Booking form (mock data) | Customer + Folio + Payment UI | Connect real API (remove mock) | E2E test |
| Dev D | 6 test scenarios (code) + Unit test framework | Integration test framework | Load test (50 concurrent) + Security scan (OWASP ZAP) | Go/No-Go checklist |

**Milestone (End Week 4):** Go/No-Go Phase 1.
**Criteria:**
- [ ] All 10 endpoints pass Postman
- [ ] Night Audit runs 3 consecutive nights
- [ ] 0 PII accessible without JWT
- [ ] Room status RBAC enforced (HK tries RESERVED → 403)
- [ ] 6 test scenarios pass

## Phase 2: AI + Operations (Week 5-7)
| | Week 5 | Week 6 | Week 7 |
|---|---|---|---|
| Dev A | Guest auth (OTP), Check-in/out, Services, Group endpoints | Folio advanced (routing, split, interim), Night Audit manual trigger | Integration test |
| Dev B | AI Gateway: main.py + llm_client.py | AI Gateway: rag.py + guardrails.py + escalation.py | AI Gateway: audit log + monitoring hooks |
| Dev C | Guest Chat UI + Staff Dashboard | Escalation alert UI + AI interaction viewer | E2E test (AI scenarios) |
| Dev D | pgvector setup + RAG pipeline (index edge-cases, escalation, SOPs) | 4 AI test scenarios (injection, bypass, concurrent, offline) | Monitoring dashboard (Grafana) + Alert rules |

**Milestone (End Week 7):** Go/No-Go Phase 2.
**Criteria:**
- [ ] AI violation rate < 0.1% over 7 days
- [ ] Escalation SLA > 95% (2min ack)
- [ ] 0 PII leak in 1000 interactions
- [ ] All 6 test scenarios pass (including AI)
- [ ] Night Audit + POS offline scenario passes

## Phase 3: Scale & Integration (Week 8-9)
| | Week 8 | Week 9 |
|---|---|---|
| Dev A | Promotions, Loyalty, Feedback, Notifications, Staff mgmt endpoints | Webhooks (subscribe + receive), Reports (DBR, occupancy, revenue-by-channel) |
| Dev B | OTA integration (Booking.com/Agoda XML/HTNG) | Payment gateway (Stripe/local) + POS integration |
| Dev C | Admin dashboard (reports, staff, promos) | Webhook config UI + Notification templates |
| Dev D | CI/CD pipeline (GitHub Actions) + Environment separation (dev/staging/prod) | Production hardening + Backup cron + Restore test |

**Milestone (End Week 9):** Go/No-Go Production.
**Criteria:**
- [ ] OTA sync: 0 overbooking in 7 days (staging)
- [ ] Payment: 0 double-charge in 100 transactions
- [ ] Uptime > 99.5% (staging)
- [ ] Backup: 30 days retained, restore tested

## GO-LIVE (Week 10)
- Deploy to production (6 containers)
- Limited scope: Guest chatbot READ-ONLY (availability, FAQ, services)
- Staff uses manual PMS (no AI write)
- Monitoring active (Grafana + Slack alerts)

## Phase 4: Pilot & Scale (Week 11-15)
| Week | Unlock | Risk | Go/No-Go |
|---|---|---|---|
| 11 | Guest chatbot live (20-50 real guests) | Low | Violation < 0.1% for 7 days |
| 12 | Staff Copilot (Front Desk) | Medium | Staff CSAT ≥ 4/5 |
| 13 | Booking draft (Write-preview) | High | 0 booking error in 14 days |
| 14 | Service request + Loyalty points | Medium | 0 PII leak |
| 15 | Revenue analytics (Admin) | Low | Internal only |

## Ongoing (Week 16+)
| Frequency | Task | Owner |
|---|---|---|
| Daily | Night Audit auto-run + Backup (02:00) | System |
| Weekly | AI log review (100 samples) + Violation report | You + Dev D |
| Weekly | CHANGELOG.md review | All devs |
| Monthly | AI API key rotation + Security scan (OWASP ZAP) | Dev D |
| Quarterly | Load test (50 concurrent + 10 staff) + DR drill | Dev D + You |

## Summary
| Milestone | Week |
|---|---|
| Phase 0 (Safe demo) | Day 3 |
| Phase 1 (Core PMS) | Week 4 |
| Phase 2 (AI + Ops) | Week 7 |
| Phase 3 (Integration) | Week 9 |
| GO-LIVE (Limited) | Week 10 |
| Full 5★ AI (all use cases) | Week 15 |

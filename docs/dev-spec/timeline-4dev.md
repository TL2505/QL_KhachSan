# PROJECT TIMELINE — 4 DEV TEAM (5★ HOTEL PMS + AI)

## Team Roles & Rebalanced Ownership
| Dev | Role | Phase 2 Focus | Phase 3 Focus |
|---|---|---|---|
| **Dev A** | Backend API & AI Integration | Java HTTP Client to AI Gateway (`/api/v1/ai/*`) | Webhooks & Loyalty/Promotions subsystems |
| **Dev B** | Core Business Logic | 100% Night Audit 7-step, Folio Split/Routing, Locks | `guardrails.py` rules & RAG context injection |
| **Dev C** | Frontend Engineer | Swing Chat Native UI (`JPanel` + `FlatRoundBorder`) | 100% Guest Web Mobile-First (MUI 5) |
| **Dev D** | DevOps & AI Infrastructure | FastAPI skeleton, pgvector Docker, SOP embeddings | CI/CD GitHub Actions, Stress test (50 connections) |

### Dev C (Frontend) — Platform: Desktop (Swing) Phase 1-2, Web (React) Phase 3

| Phase | Task | Platform |
|---|---|---|
| Phase 1 (W1-4) | Integrate `/api/v1/*` into existing Swing UI. Remove mock data. Connect real API. **NO new UI framework. NO React.** | Swing |
| Phase 2 (W5-7) | AI Chat Native UI in Swing (`JPanel` + `BoxLayout` + `FlatRoundBorder`). Staff Dashboard panels (Swing). Escalation alert (Swing popup). **NO React.** | Swing |
| Phase 3 (W8-9) | **React 18 web (MUI 5):** 100% focus on Guest booking page + AI chat + review form. Admin manages via Swing `AdminForm`. | React |
| Phase 4+ | React 18 Admin Portal. Optional: Swing → React migration for staff. PWA for mobile guests. | React + PWA |



## Phase 0: Quick Fixes (Day 1-2)
| Dev | Task |
|---|---|
| Dev A | JWT on all endpoints + Standard error format + HTTP codes |
| Dev B | DB schema design + Docker skeleton (docker-compose.yml) |
| Dev C | Nginx config + TLS (self-signed). NO React/Vue setup. |
| Dev D | .env + .gitignore + UFW (22,80,443) + SSH key only + fail2ban |

**Milestone (Day 3):** Phase 0 complete → Internal demo safe.

## Phase 1: Core PMS (Week 1-4)
| | Week 1 | Week 2 | Week 3 | Week 4 |
|---|---|---|---|---|
| Dev A | Auth, Room, Availability, Customer, Booking endpoints | Cancel, Folio, Payment, Health, List endpoints | Integration test with Dev B | Go/No-Go test |
| Dev B | Night Audit 7 steps + Lock Rule | Night Audit edge cases (POS offline, midnight C/O, No-Show) + Tax | Group booking + Folio routing/split/interim | Integration test |
| Dev C | API integration into existing Swing UI + connect real API (remove mock). NO new UI framework. | API integration into existing Swing UI + connect real API (remove mock). NO new UI framework. | Connect real API (remove mock) | E2E test |
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
| Dev A | Guest auth (OTP), Check-in/out, Services, Group endpoints | Java Client HTTP to AI Gateway (`/api/v1/ai/*`) | Integration test with AI Gateway & Core |
| Dev B | Night Audit edge cases & Folio Routing/Split | 100% Core Transaction Locks & Folio Audit | `guardrails.py` rules & RAG context injection |
| Dev C | AI Chat Native UI in Swing (`JPanel` + `FlatRoundBorder`) | Staff Dashboard panels (Swing) + Alert popup | E2E test (Swing AI scenarios) |
| Dev D | FastAPI skeleton + pgvector Docker setup | RAG indexing pipeline (SOPs, constraints) | 4 AI test scenarios + Monitoring dashboard |

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
| Dev A | Promotions, Loyalty, Feedback, Notifications, Staff mgmt endpoints | Webhooks (subscribe + receive), Reports (DBR, occupancy, revenue) |
| Dev B | OTA integration (Booking.com/Agoda XML/HTNG) | Payment gateway (Stripe/local) + POS integration |
| Dev C | React 18 (MUI 5): Guest booking flow + AI chat bubble | React 18: Guest review form + mobile polish. Admin on Swing. |
| Dev D | CI/CD pipeline (GitHub Actions) + Stress test (50 conn) | Production hardening + Backup cron + Restore test |


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

# IMPLEMENTATION & ROLLOUT PLAN

## 1. TECH STACK SUMMARY
| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend API** | Java 17 + Spring Boot | Core business logic, PMS functions, Night Audit |
| **AI Gateway** | Python 3.11 + FastAPI | Guardrails, RAG, LLM Orchestration |
| **Database** | MariaDB 11 | Transactional data, Bookings, Folios |
| **Vector DB** | PostgreSQL 16 + pgvector | Document embeddings for RAG |
| **Deployment** | Docker Compose | Container orchestration (6 containers) |

## 2. PHASE TIMELINE & ROADMAP
### Phase 1: Core PMS & APIs (Weeks 1-4)
- **Scope:** CRUD for Rooms, Bookings, Folios. Auth & RBAC.
- **Go/No-Go Criteria:** 
  1. 100% API endpoints pass unit tests.
  2. Authentication (JWT) successfully restricts roles.
  3. Concurrent booking race condition handled.

### Phase 2: AI Gateway & Guardrails (Weeks 5-8)
- **Scope:** FastAPI service, OpenAI integration, `constraints.md` enforcement.
- **Go/No-Go Criteria:** 
  1. Prompt injection test success rate > 99%.
  2. AI never quotes incorrect rates in automated tests.
  3. Latency < 2 seconds per AI response.

### Phase 3: AI Pilot & Integrations (Weeks 9-12)
- **Scope:** Connect AI Gateway to PMS APIs. Internal Staff Copilot testing.
- **Go/No-Go Criteria:**
  1. Staff can retrieve PMS data via natural language.
  2. Escalation workflow (Warm Transfer) routes correctly to Dashboard.
  3. Zero PII leaks during 2-week internal pilot.

### Phase 4: Scale & Guest Launch (Weeks 13+)
- **Scope:** Guest-facing Chatbot launch, Webhooks, OTA sync.
- **Scale Roadmap:** Guest Chatbot → Booking Drafts → Service Requests → Revenue Analytics.

## 3. RACI MATRIX
| Task | Business (You) | Dev Team | AI Tool/Gateway |
| :--- | :--- | :--- | :--- |
| Define Policies (constraints) | **A/R** | C | I |
| Build PMS APIs | C | **A/R** | I |
| Implement Guardrails | I | **A/R** | C |
| Monitor Violations | **A** | R | **C** |
*(R=Responsible, A=Accountable, C=Consulted, I=Informed)*

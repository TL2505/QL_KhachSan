# TECHNOLOGY STACK DEFINITION

| Category | Technology | Version | Justification |
| :--- | :--- | :--- | :--- |
| **LLM Provider** | OpenAI `gpt-4o-mini` | Latest | High speed, cost-effective for 5★ high-volume chat; Fallback: Claude 3 Haiku |
| **Vector Database** | `pgvector` (PostgreSQL) | 16 | ACID compliant, native vector search (1536 dim), simplifies deployment |
| **PMS Backend** | Java 17 (plain HttpServer) | 17 | Enterprise standard, lightweight native HTTP server (`com.sun.net.httpserver`), robust multithreading |
| **Build Tool** | Maven (pom.xml) + javac | 3.9+ | Standard dependency management and clean javac container build |
| **AI Gateway** | Python (FastAPI) | 3.11 | Native ML/AI libraries, async handling, ideal for LLM orchestration |
| **Frontend UI (Guest & Admin)** | React 18 + MUI 5.x | 18 | Unified single design system; reuses theme tokens, minimizes learning overhead |
| **DB Connection Pool** | HikariCP | 5.1.0 | High-performance JDBC connection pool for Java HttpServer, eliminates leaks |
| **AI Database Role** | MariaDB user `ai_reader` | - | Physical read-only boundary (SELECT rooms, promotions); strict PII denial |
| **Transactional DB** | MariaDB | 11 | High performance relational DB for folios, bookings, and financial logs |
| **Deployment** | Docker Compose | v2 | Simplifies 6-container microservices orchestration on a single Ubuntu VM |
| **OS** | Ubuntu LTS | 22.04 | Stable, secure production environment |
| **CI/CD** | GitHub Actions | - | Integrated with repo, automated testing and docker image builds |
| **Monitoring** | Prometheus + Grafana | - | Real-time dashboards for API latency and AI violation rates |

## PLATFORM ARCHITECTURE
| Platform | User | Tech | Phase | Notes |
|---|---|---|---|---|
| Desktop (Staff) | Front Desk, HK, F&B | Java Swing (existing) | Phase 1-2 (keep) | Calls /api/v1/* via HTTP. No UI changes. |
| Web (Guest) | Guest | React 18 + MUI 5.x | Phase 3 | Served by Nginx. OTP auth. |
| Web (Admin) | Admin/GM | React 18 + MUI 5.x | Phase 4+ (Swing in P3) | Reuses Guest theme/tokens. |
| Mobile (Future) | Guest | PWA / React Native | Phase 4+ | Reuses /api/v1/* endpoints. |

### PLATFORM BY PHASE
| Phase | Staff UI | Guest UI | Admin UI | Dev C Focus |
|---|---|---|---|---|
| 1-2 | Swing (real REST API integration) | None | None | Integrate `/api/v1/*` into existing Swing; NO React. |
| 3 | Swing Staff Form | React 18 (Booking, Chat, Review) | Swing AdminForm (existing) | 100% focus on Guest Web Mobile-First. |
| 4+ | Swing (or migrate React if needed) | React + PWA | React 18 (Admin Portal) | Build Web Admin and PWA. |


## PLATFORM DESIGN PHILOSOPHY

**Principle: Platform = WHERE THE USER SITS.**

| User | Sits at | Platform | Phase | Rationale |
|---|---|---|---|---|
| Front Desk / HK / F&B | 1 desk, 8h, multi-monitor | Desktop (Swing) | 1-3 (keep) | Speed, keyboard shortcuts, no state loss on refresh |
| Guest | Phone/tablet, 1-3 min | Web (React) + PWA | 3-4 | No install, responsive, multi-language |
| Admin / GM | Anywhere | Web responsive (React) | 3+ | Mobile-friendly reports |

### Consequences
- Phase 1-2: **NO React.** Dev C integrates API into existing Swing only.
- Phase 3: React for Guest + Admin. Swing remains for Staff.
- Phase 4+: Optional Swing → React (only if staff demand).
- AI Chat: Guest = React. Staff = Swing (JWebView). Two separate UIs, one API.
- **No 'unified frontend' until Phase 4+.**

### Shared Backend (platform-agnostic)
All platforms → same Java backend → same `/api/v1/*` endpoints.
Differentiation: Auth method + RBAC role + UI framework. Nothing else.



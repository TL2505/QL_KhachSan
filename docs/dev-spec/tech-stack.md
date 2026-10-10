# TECHNOLOGY STACK DEFINITION

| Category | Technology | Version | Justification |
| :--- | :--- | :--- | :--- |
| **LLM Provider** | OpenAI `gpt-4o-mini` | Latest | High speed, cost-effective for 5★ high-volume chat; Fallback: Claude 3 Haiku |
| **Vector Database** | `pgvector` (PostgreSQL) | 16 | ACID compliant, native vector search (1536 dim), simplifies deployment |
| **PMS Backend** | Java (Spring Boot) | 17 | Enterprise standard, robust multithreading for Night Audit, strongly typed |
| **Build Tool** | Gradle | 8.x | Faster builds, standard for modern Java/Spring Boot projects |
| **AI Gateway** | Python (FastAPI) | 3.11 | Native ML/AI libraries, async handling, ideal for LLM orchestration |
| **Frontend UI** | React + Vite | 18 | High performance, rapid rendering for Staff Dashboard |
| **Transactional DB** | MariaDB | 11 | High performance relational DB for folios, bookings, and financial logs |
| **Deployment** | Docker Compose | v2 | Simplifies 6-container microservices orchestration on a single Ubuntu VM |
| **OS** | Ubuntu LTS | 22.04 | Stable, secure production environment |
| **CI/CD** | GitHub Actions | - | Integrated with repo, automated testing and docker image builds |
| **Monitoring** | Prometheus + Grafana | - | Real-time dashboards for API latency and AI violation rates |

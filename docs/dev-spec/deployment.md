# DEPLOYMENT ARCHITECTURE & CHECKLIST

## 1. 6-CONTAINER ARCHITECTURE (Docker Compose)
Deployed on an Ubuntu 22.04 LTS VM (e.g., `192.168.56.102`).
1. **Nginx (Reverse Proxy):** Port `443/80` (TLS termination, routing).
2. **Java Backend (PMS API):** Port `8081` (Spring Boot 17). Internal network only.
3. **MariaDB (Transactional):** Port `3306`. Internal network only. No exposed ports.
4. **pgvector (Vector DB):** Port `5432`. Internal network only. Used for RAG embeddings.
5. **AI Gateway (FastAPI):** Port `8082`. Internal network only. Interacts with OpenAI.
6. **Adminer (DB Admin):** Port `127.0.0.1:8083` (Dev profile only).

## 2. PHASE DEPLOYMENT ROLLOUT
- **Phase 1 (Core):** Deploy Nginx, Java Backend, MariaDB. (3 containers)
- **Phase 2 (AI Pilot):** Add AI Gateway, pgvector. (+2 containers)
- **Phase 3 (Scale):** Add external Webhook listeners and scale Java nodes.

## 3. SECURITY CHECKLIST (9 Items)
- [x] All database ports (3306, 5432) are bound to Docker internal network ONLY.
- [x] `.env` is listed in `.gitignore` and NEVER committed.
- [x] TLS (HTTPS) enforced at Nginx layer.
- [x] JWT tokens have a strict 1-hour expiration.
- [x] AI API keys (OpenAI) are rotated monthly.
- [x] Rate limiting enabled on AI Gateway to prevent billing DDoS.
- [x] **SSH key only** access to VM (Password auth disabled).
- [x] **fail2ban** configured to prevent brute force attacks.
- [x] **UFW firewall** enabled, allowing only ports 22, 80, 443.

## 4. OPERATIONS (Backup & Health Check)
- **Backup:** Daily cron job running `mysqldump` at 02:00 AM, 30-day retention.
  ```bash
  0 2 * * * docker exec pms-mariadb sh -c 'mysqldump -u root -p${DB_PASSWORD} pms' > /backups/db_$(date +\%F).sql
  ```
- **Health Check:** Post-deploy validation script checking the `/api/v1/health` endpoint.
  ```bash
  curl -f https://pms.hotel.com/api/v1/health || exit 1
  ```

## 5. ENVIRONMENT VARIABLES (.env)
```bash
# WARNING: NEVER COMMIT THIS FILE
DB_PASSWORD=secure_password
VECTOR_DB_PASSWORD=pg_secure_password
OPENAI_API_KEY=sk-xxxx
LLM_ENDPOINT=https://api.openai.com/v1
JWT_SECRET=long_random_string_here
```

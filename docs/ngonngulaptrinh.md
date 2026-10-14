# TECHNOLOGY & LANGUAGES — 5★ HOTEL PMS

## Programming Languages

| Layer | Language | Version | Framework | Owner |
|---|---|---|---|---|
| Backend PMS (API, Night Audit, Business Logic) | Java | 17 (LTS) | Spring Boot 3.x | Dev A, Dev B |
| AI Gateway (RAG, Guardrails, Escalation) | Python | 3.11 | FastAPI + uvicorn | Dev B |
| Frontend (Guest Chat, Staff Dashboard, Admin) | TypeScript | 5.x | React 18 (or Vue 3) | Dev C |
| Database (Schema, Query, Migration) | SQL | — | MariaDB 11 + PostgreSQL 16 (pgvector) | Dev B, Dev D |
| Infrastructure (Containers, Network, Proxy) | YAML + Nginx Config | — | Docker Compose v2 | Dev D |
| CI/CD (Pipeline, Automation) | YAML + Bash | — | GitHub Actions | Dev D |
| Testing (Unit, Integration, E2E) | Java + Python + TypeScript | — | JUnit 5, pytest, Cypress | Dev D |

## Summary

| Category | Languages |
|---|---|
| Primary (3) | **Java**, **Python**, **TypeScript** |
| Secondary (2) | **SQL**, **Bash** |

## Why These Choices

| Choice | Justification |
|---|---|
| Java 17 + Spring Boot | Industry standard for enterprise PMS. Long-term support. Large talent pool. |
| Python 3.11 + FastAPI | Best-in-class for AI/ML integration. Async-native. FastAPI = auto OpenAPI docs. |
| TypeScript + React | Type safety for complex UI. Largest component ecosystem. |
| MariaDB 11 | Drop-in MySQL replacement. Free. Proven at scale. |
| PostgreSQL 16 + pgvector | Vector search for RAG in same DB engine. No extra infrastructure. |
| Docker Compose v2 | Reproducible deployments. 6-container orchestration. |
| GitHub Actions | Native CI/CD. Free for public repos. |

## Dev Responsibility Matrix

| Dev | Primary Language | Secondary |
|---|---|---|
| Dev A (Backend API) | Java | SQL |
| Dev B (Business Logic + AI) | Java + Python | SQL |
| Dev C (Frontend) | TypeScript | — |
| Dev D (DevOps + QA) | Bash + YAML | Java (test), Python (test), SQL |
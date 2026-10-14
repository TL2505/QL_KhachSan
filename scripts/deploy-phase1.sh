#!/usr/bin/env bash
# ==============================================================================
# 5★ Luxury Hotel PMS - TASK-D4.3: Automated One-Click Deployment Script
# Target Platform: Ubuntu 22.04 LTS / Debian Linux / Docker Engine 24+
# DoD Standard: Full cold-start deployment achieving HEALTHY state in < 90 seconds.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ANSI Color Codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

START_TIMESTAMP=$(date +%s%N 2>/dev/null || date +%s)

echo -e "${BLUE}==================================================================${NC}"
echo -e "${CYAN}${BOLD}★ 5★ LUXURY HOTEL PMS - ONE-CLICK PRODUCTION DEPLOYMENT ★${NC}"
echo -e "${BLUE}==================================================================${NC}"

cd "${PROJECT_ROOT}"

# ------------------------------------------------------------------------------
# STEP 1: PREREQUISITE & ENVIRONMENT VERIFICATION
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[STEP 1/5] Checking host environment and system dependencies...${NC}"

REQUIRED_TOOLS=("docker" "curl")
for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "${tool}" &> /dev/null; then
        echo -e "${RED}[ERROR] Required tool '${tool}' is not installed or not in PATH.${NC}"
        exit 1
    fi
done

# Check Docker Compose (v2 plugin or standalone)
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    echo -e "${RED}[ERROR] Neither 'docker compose' nor 'docker-compose' was found.${NC}"
    exit 1
fi

echo -e "${GREEN}✔ Host dependencies verified: Docker Engine & Compose available.${NC}"

# ------------------------------------------------------------------------------
# STEP 2: STORAGE DIRECTORIES & INITIALIZATION ARTIFACTS
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[STEP 2/5] Initializing runtime directories & permissions...${NC}"

mkdir -p "${PROJECT_ROOT}/target"
mkdir -p "${PROJECT_ROOT}/logs"
chmod +x "${SCRIPT_DIR}/healthcheck.sh"
if [ -f "${SCRIPT_DIR}/run-soak-test.sh" ]; then
    chmod +x "${SCRIPT_DIR}/run-soak-test.sh"
fi

echo -e "${GREEN}✔ Runtime directories prepared.${NC}"

# ------------------------------------------------------------------------------
# STEP 3: TEARDOWN OBSOLETE CONTAINERS (IDEMPOTENT CLEAN SLATE)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[STEP 3/5] Cleaning up existing containers and orphan networks...${NC}"
${DOCKER_COMPOSE} down --remove-orphans || true

# ------------------------------------------------------------------------------
# STEP 4: MULTI-STAGE BUILD & CONTAINER DAEMON STARTUP
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[STEP 4/5] Building and launching multi-container stack (MariaDB + Java 17 Backend)...${NC}"
${DOCKER_COMPOSE} up --build -d

# ------------------------------------------------------------------------------
# STEP 5: DEEP HEALTHCHECK POLLING LOOP (MAX 90 SECONDS TIMEOUT)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[STEP 5/5] Polling deep healthcheck endpoint until system is HEALTHY...${NC}"

MAX_WAIT_SECONDS=90
POLL_INTERVAL=2
ELAPSED=0
SYSTEM_HEALTHY=false

while [ ${ELAPSED} -lt ${MAX_WAIT_SECONDS} ]; do
    if "${SCRIPT_DIR}/healthcheck.sh" > /dev/null 2>&1; then
        SYSTEM_HEALTHY=true
        break
    fi
    sleep ${POLL_INTERVAL}
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
    echo -e "   ⏳ Waiting for system convergence... (${ELAPSED}s / ${MAX_WAIT_SECONDS}s)"
done

END_TIMESTAMP=$(date +%s%N 2>/dev/null || date +%s)

# Calculate duration in seconds with decimal
if command -v python3 &> /dev/null; then
    DURATION=$(python3 -c "print(f'{(${END_TIMESTAMP} - ${START_TIMESTAMP}) / 1e9:.1f}')" 2>/dev/null || echo "${ELAPSED}")
else
    DURATION="${ELAPSED}"
fi

echo -e "\n${BLUE}==================================================================${NC}"
if [ "${SYSTEM_HEALTHY}" = true ]; then
    echo -e "${GREEN}${BOLD}✔ [SUCCESS] 5★ PMS Stack deployed and reached HEALTHY state in ${DURATION}s!${NC}"
    echo -e "${GREEN}✔ DoD Criteria Met: System converged in < 90 seconds (Actual: ${DURATION}s).${NC}"
    echo -e "${CYAN}• Backend API Endpoint : http://localhost:8081/api/v1/health${NC}"
    echo -e "${CYAN}• Room Catalog API     : http://localhost:8081/api/v1/rooms${NC}"
    echo -e "${CYAN}• Booking Engine API   : http://localhost:8081/api/v1/bookings${NC}"
    echo -e "${BLUE}==================================================================${NC}"
    exit 0
else
    echo -e "${RED}${BOLD}❌ [FATAL] Deployment timed out after ${MAX_WAIT_SECONDS} seconds without reaching HEALTHY state.${NC}"
    echo -e "${YELLOW}=== DOCKER COMPOSE CONTAINER LOGS ===${NC}"
    ${DOCKER_COMPOSE} logs --tail=50
    echo -e "${BLUE}==================================================================${NC}"
    exit 1
fi

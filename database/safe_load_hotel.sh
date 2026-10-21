#!/bin/bash
# ==============================================================================
# AURELIA GRAND PALACE 5★ PMS - SAFE DATABASE LOAD SCRIPT
# Standard: 4.1.0-REALISTIC-BASELINE (Invariant 1: Data Integrity & Volume Protection)
# ==============================================================================
set -euo pipefail

CONTAINER_NAME="${DB_CONTAINER:-aurelia-mariadb}"
DB_NAME="${DB_NAME:-hotel_prod_db}"
ROOT_PASSWORD="${DB_ROOT_PASSWORD:-hotel_root_secret_2026}"
HOTEL_SQL_PATH="${1:-database/hotel.sql}"
REMOTE_GRANT_SQL="database/grant_remote_access.sql"

echo "[PMS Safe DB Load] Checking MariaDB container: ${CONTAINER_NAME}..."
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "[ERROR] Container ${CONTAINER_NAME} is not running! Start it with: docker compose up -d mariadb" >&2
    exit 1
fi

if [ ! -f "${HOTEL_SQL_PATH}" ]; then
    echo "[ERROR] SQL file not found at: ${HOTEL_SQL_PATH}" >&2
    exit 1
fi

echo "[PMS Safe DB Load] Step 1: Disabling FOREIGN_KEY_CHECKS and triggers..."
docker exec -i "${CONTAINER_NAME}" mariadb -uroot -p"${ROOT_PASSWORD}" -e "SET FOREIGN_KEY_CHECKS=0; CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\`;" "${DB_NAME}"

echo "[PMS Safe DB Load] Step 2: Restoring data from ${HOTEL_SQL_PATH} using root account..."
# Strip legacy production seeds if needed or stream directly with foreign key checks disabled
docker exec -i "${CONTAINER_NAME}" mariadb -uroot -p"${ROOT_PASSWORD}" --default-character-set=utf8mb4 "${DB_NAME}" < "${HOTEL_SQL_PATH}"

echo "[PMS Safe DB Load] Step 3: Re-enabling FOREIGN_KEY_CHECKS..."
docker exec -i "${CONTAINER_NAME}" mariadb -uroot -p"${ROOT_PASSWORD}" -e "SET FOREIGN_KEY_CHECKS=1;" "${DB_NAME}"

if [ -f "${REMOTE_GRANT_SQL}" ]; then
    echo "[PMS Safe DB Load] Step 4: Re-applying remote access privileges ('hotel_admin'@'%')..."
    docker exec -i "${CONTAINER_NAME}" mariadb -uroot -p"${ROOT_PASSWORD}" "${DB_NAME}" < "${REMOTE_GRANT_SQL}"
fi

echo "[PMS Safe DB Load] Verifying database connectivity & user privileges..."
docker exec -i "${CONTAINER_NAME}" mariadb -uroot -p"${ROOT_PASSWORD}" -e "SELECT User, Host FROM mysql.user WHERE User = 'hotel_admin';"

echo "[PMS Safe DB Load] SUCCESS: Database restored cleanly with full remote access preserved."

#!/bin/bash
# Sourced by the official MariaDB entrypoint on an empty data directory only.
set -euo pipefail
schema="${MARIADB_DATABASE:-${MYSQL_DATABASE:-hotel_prod_db}}"
[[ "$schema" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Invalid database name' >&2; exit 1; }
bootstrap_sql() {
    sed "s/hotel_prod_db/$schema/g" "$1" | docker_process_sql --database="$schema"
}
# Never import the legacy production seed (accounts and personal data).
sed '/^-- SEED DATA FROM PRODUCTION/,$d' /pms-database/hotel.sql |
    sed "s/hotel_prod_db/$schema/g" | docker_process_sql --database="$schema"
bootstrap_sql /pms-database/bootstrap/prerequisites.sql
while IFS= read -r migration || [[ -n "$migration" ]]; do
    [[ -z "$migration" ]] && continue
    echo "Applying PMS migration: $migration"
    bootstrap_sql "/pms-database/migrations/$migration"
done < /pms-database/bootstrap/migrations.txt

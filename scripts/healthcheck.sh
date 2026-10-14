#!/usr/bin/env bash
# Read-only verifier for the production database-aware health endpoint.
set -euo pipefail
TARGET_URL="${1:-http://localhost:8081/api/v1/health}"
TMP_BODY=$(mktemp)
trap 'rm -f "$TMP_BODY"' EXIT
HTTP_STATUS=$(curl -sS -o "$TMP_BODY" -w '%{http_code}' --max-time 3 "$TARGET_URL")
if [ "$HTTP_STATUS" != "200" ]; then
    echo "UNHEALTHY: HTTP $HTTP_STATUS"
    exit 1
fi
if command -v python3 >/dev/null 2>&1; then
    python3 - "$TMP_BODY" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as response:
    data = json.load(response)
if data.get("status") != "UP" or data.get("database") != "CONNECTED":
    raise SystemExit("UNHEALTHY: database probe did not pass")
for metric in ("active_connections", "awaiting_connections"):
    value = data.get(metric)
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise SystemExit("UNHEALTHY: missing or invalid pool telemetry")
print("HEALTHY: database probe passed; active=%s awaiting=%s" %
      (data["active_connections"], data["awaiting_connections"]))
PY
else
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$TMP_BODY"
    grep -Eq '"database"[[:space:]]*:[[:space:]]*"CONNECTED"' "$TMP_BODY"
    echo "HEALTHY: database probe passed (Python unavailable; telemetry not parsed)"
fi

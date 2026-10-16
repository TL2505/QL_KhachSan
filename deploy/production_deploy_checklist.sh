#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
command -v docker >/dev/null
docker compose -f docker-compose.prod.yml config --quiet
# Build, provision the approved external database volume, back up and run identity
# cutover in a maintenance window BEFORE this startup-only health gate.
started=$(date +%s)
docker compose -f docker-compose.prod.yml up -d --wait --wait-timeout 85
docker compose -f docker-compose.prod.yml exec -T backend sh -c '
  curl -fsS --max-time 3 http://127.0.0.1:8081/api/v1/health >/dev/null
  curl -fsS --max-time 3 --connect-timeout 2 --cert-type P12 \
    --cert /run/secrets/ai_client_identity --pass "$(cat /run/secrets/ai_tls_password)" \
    --cacert /run/secrets/ai_ca https://ai-service:8082/health >/dev/null
'
# Verify the public TLS route as well; use the approved CA via CURL_CA_BUNDLE if private.
: "${PMS_PUBLIC_BASE_URL:?Supply the approved HTTPS PMS origin}"
case "$PMS_PUBLIC_BASE_URL" in https://*) ;; *) echo 'HTTPS origin required' >&2; exit 1 ;; esac
curl -fsS --max-time 3 "${PMS_PUBLIC_BASE_URL%/}/api/v1/health" >/dev/null
elapsed=$(($(date +%s)-started))
if [ "$elapsed" -ge 90 ]; then
  echo "FAIL: startup health took ${elapsed}s; required <90s" >&2
  exit 1
fi
echo "Startup and mTLS health verified in ${elapsed}s. This does not grant production Go/No-Go."

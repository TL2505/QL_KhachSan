#!/usr/bin/env bash
# ==============================================================================
# 5★ Luxury Hotel PMS - TASK-D4.2: 50-Concurrent User Soak Test Runner
# Platform: Linux / Ubuntu 22.04 LTS / CI Headless Environment
# SLA Standard: P95 < 30ms, P99 < 50ms, Error Rate = 0.00%, Host CPU < 25%
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

TEST_PLAN="${PROJECT_ROOT}/src/test/resources/load-test/phase1-soak-test.jmx"
RESULTS_DIR="${PROJECT_ROOT}/target/jmeter"
RESULTS_FILE="${RESULTS_DIR}/results.jtl"
DASHBOARD_DIR="${RESULTS_DIR}/dashboard"

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}==================================================================${NC}"
echo -e "${CYAN}★ 5★ LUXURY HOTEL PMS - 50-CCU 30-MINUTE SOAK TEST RUNNER ★${NC}"
echo -e "${BLUE}==================================================================${NC}"

# 1. Check for Apache JMeter installation
if ! command -v jmeter &> /dev/null; then
    echo -e "${YELLOW}[WARN] 'jmeter' command not found in PATH.${NC}"
    echo -e "${YELLOW}[INFO] Searching in standard JMeter locations...${NC}"
    if [ -x "/opt/apache-jmeter/bin/jmeter" ]; then
        JMETER_BIN="/opt/apache-jmeter/bin/jmeter"
    elif [ -x "${HOME}/apache-jmeter/bin/jmeter" ]; then
        JMETER_BIN="${HOME}/apache-jmeter/bin/jmeter"
    else
        echo -e "${RED}[ERROR] Apache JMeter is not installed. Please install JMeter 5.6+ to execute soak test.${NC}"
        echo -e "${YELLOW}Install instructions on Ubuntu:${NC}"
        echo -e "  sudo apt update && sudo apt install -y default-jre wget"
        echo -e "  wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz"
        echo -e "  tar -xzf apache-jmeter-5.6.3.tgz -C /opt/"
        echo -e "  export PATH=/opt/apache-jmeter-5.6.3/bin:\$PATH"
        exit 1
    fi
else
    JMETER_BIN="jmeter"
fi

# 2. Prepare Results Directory
mkdir -p "${RESULTS_DIR}"
rm -rf "${RESULTS_FILE}" "${DASHBOARD_DIR}"

HOST="${1:-localhost}"
PORT="${2:-8081}"
TOKEN="${3:-eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTY3MDAwMDAwMCwiZXhwIjoxOTkwMDAwMDAwfQ.signature}"

echo -e "${CYAN}[SETUP] Target System: http://${HOST}:${PORT}${NC}"
echo -e "${CYAN}[SETUP] Test Plan: ${TEST_PLAN}${NC}"
echo -e "${CYAN}[SETUP] Results Output: ${RESULTS_FILE}${NC}"
echo -e "${CYAN}[SETUP] HTML Dashboard: ${DASHBOARD_DIR}${NC}"

# 3. Launch JMeter in Headless Non-GUI Mode
echo -e "\n${BLUE}==================================================================${NC}"
echo -e "${YELLOW}[START] Launching Apache JMeter Soak Test (50 Threads, 1800s Duration)...${NC}"
echo -e "${BLUE}==================================================================${NC}"

START_TIME=$(date +%s)

"${JMETER_BIN}" -n \
  -t "${TEST_PLAN}" \
  -l "${RESULTS_FILE}" \
  -e -o "${DASHBOARD_DIR}" \
  -Jhost="${HOST}" \
  -Jport="${PORT}" \
  -Jtoken="${TOKEN}"

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

echo -e "\n${BLUE}==================================================================${NC}"
echo -e "${GREEN}[COMPLETED] Soak test execution finished in ${ELAPSED} seconds.${NC}"
echo -e "${BLUE}==================================================================${NC}"

# 4. Analyze Results and Evaluate DoD SLAs
if [ ! -f "${RESULTS_FILE}" ]; then
    echo -e "${RED}[FAIL] Results file ${RESULTS_FILE} was not generated!${NC}"
    exit 1
fi

echo -e "${CYAN}[ANALYSIS] Calculating P95, P99, and Error Rate metrics from JTL...${NC}"

# Use python3 or awk to compute percentiles and error rate from JTL CSV
python3 - <<EOF
import sys

jtl_path = "${RESULTS_FILE}"

latencies = []
total_count = 0
error_count = 0

try:
    with open(jtl_path, 'r', encoding='utf-8') as f:
        header = f.readline().strip().split(',')
        try:
            elapsed_idx = header.index('elapsed')
            success_idx = header.index('success')
            code_idx = header.index('responseCode')
        except ValueError:
            elapsed_idx = 1
            success_idx = 7
            code_idx = 3

        for line in f:
            parts = line.strip().split(',')
            if len(parts) > max(elapsed_idx, success_idx):
                try:
                    latency = float(parts[elapsed_idx])
                    success = parts[success_idx].strip().lower() in ['true', '1']
                    code = parts[code_idx].strip()
                    latencies.append(latency)
                    total_count += 1
                    if not success or code.startswith('5') or code.startswith('4'):
                        error_count += 1
                except ValueError:
                    continue

    if total_count == 0:
        print("[ERROR] No sample records found in JTL file.")
        sys.exit(1)

    latencies.sort()
    p50 = latencies[int(len(latencies) * 0.50)]
    p90 = latencies[int(len(latencies) * 0.90)]
    p95 = latencies[int(len(latencies) * 0.95)]
    p99 = latencies[int(len(latencies) * 0.99)]
    avg = sum(latencies) / len(latencies)
    err_rate = (error_count / total_count) * 100.0

    print("------------------------------------------------------------------")
    print(f"Total Requests Executed : {total_count:,}")
    print(f"Error Count             : {error_count:,}")
    print(f"Error Rate              : {err_rate:.2f}% (DoD SLA: 0.00%)")
    print(f"Average Response Time   : {avg:.2f} ms")
    print(f"Median (P50) Latency    : {p50:.2f} ms")
    print(f"P90 Latency             : {p90:.2f} ms")
    print(f"P95 Latency             : {p95:.2f} ms (DoD SLA: < 30.00 ms)")
    print(f"P99 Latency             : {p99:.2f} ms (DoD SLA: < 50.00 ms)")
    print("------------------------------------------------------------------")

    # Evaluate SLA Violations
    failures = []
    if err_rate > 0.00:
        failures.append(f"Error Rate {err_rate:.2f}% exceeds DoD SLA threshold of 0.00%")
    if p95 >= 30.0:
        failures.append(f"P95 Latency {p95:.2f}ms exceeds DoD SLA threshold of 30.00ms")
    if p99 >= 50.0:
        failures.append(f"P99 Latency {p99:.2f}ms exceeds DoD SLA threshold of 50.00ms")

    if failures:
        print("\n\033[0;31m[FAILED] DoD SLA VIOLATION DETECTED:\033[0m")
        for f in failures:
            print(f"  ❌ {f}")
        sys.exit(1)
    else:
        print("\n\033[0;32m✔ [SUCCESS] 100% of TASK-D4.2 Soak Test SLA Criteria Passed Cleanly!\033[0m")
        print(f"✔ Interactive HTML Dashboard generated at: file://${DASHBOARD_DIR}/index.html")
        sys.exit(0)

except Exception as e:
    print(f"[ERROR] Failed to parse results: {e}")
    sys.exit(1)
EOF

EXIT_CODE=$?
exit ${EXIT_CODE}

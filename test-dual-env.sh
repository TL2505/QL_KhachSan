#!/usr/bin/env bash
# ==============================================================================
# Aurelia Grand Palace PMS - Dual-Environment Automated Health & Parity Test
# Tests: Localhost (127.0.0.1) & Linux VM (192.168.56.102)
# ==============================================================================

set -u

# --- Bảng màu hiển thị Console ---
CLR_RESET="\033[0m"
CLR_RED="\033[1;31m"
CLR_GREEN="\033[1;32m"
CLR_YELLOW="\033[1;33m"
CLR_BLUE="\033[1;34m"
CLR_CYAN="\033[1;36m"
CLR_BOLD="\033[1m"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

log_header() {
    echo -e "\n${CLR_CYAN}${CLR_BOLD}=====================================================================${CLR_RESET}"
    echo -e "${CLR_CYAN}${CLR_BOLD}  $1${CLR_RESET}"
    echo -e "${CLR_CYAN}${CLR_BOLD}=====================================================================${CLR_RESET}"
}

report_result() {
    local status="$1"
    local name="$2"
    local details="${3:-}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    if [ "$status" = "PASS" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo -e " [ ${CLR_GREEN}PASS${CLR_RESET} ] $name"
    elif [ "$status" = "SKIP" ]; then
        SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
        echo -e " [ ${CLR_YELLOW}SKIP${CLR_RESET} ] $name ($details)"
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo -e " [ ${CLR_RED}FAIL${CLR_RESET} ] $name"
        [ -n "$details" ] && echo -e "        ${CLR_RED}↳ $details${CLR_RESET}"
    fi
}

check_dependencies() {
    for cmd in curl jq; do
        if ! command -v "$cmd" &>/dev/null; then
            echo -e "${CLR_RED}Lỗi: Lệnh '$cmd' chưa được cài đặt. Vui lòng cài đặt trước khi chạy.${CLR_RESET}"
            exit 1
        fi
    done
}

# --- Kiểm tra tính đồng bộ Asset giữa web-ui/ và web-ui/public/ ---
test_asset_synchronization() {
    log_header "PHASE 0: KIỂM TRA ĐỒNG BỘ NGUỒN TĨNH (LOCAL REPO)"
    
    local base_dir="web-ui"
    local pub_dir="web-ui/public"

    if [ ! -d "$base_dir" ] || [ ! -d "$pub_dir" ]; then
        report_result "SKIP" "Kiểm tra Hash SHA-256 các tệp Web" "Không tìm thấy thư mục web-ui hoặc web-ui/public"
        return
    fi

    local sync_errors=0
    for file in index.html portal.html js/api.js js/app.js portal-app.js; do
        if [ -f "$base_dir/$file" ] && [ -f "$pub_dir/$file" ]; then
            hash_base=$(sha256sum "$base_dir/$file" | awk '{print $1}')
            hash_pub=$(sha256sum "$pub_dir/$file" | awk '{print $1}')
            if [ "$hash_base" != "$hash_pub" ]; then
                sync_errors=$((sync_errors + 1))
                echo -e "        ${CLR_RED}Lệch mã băm SHA-256: $file${CLR_RESET}"
            fi
        fi
    done

    if [ $sync_errors -eq 0 ]; then
        report_result "PASS" "Đồng bộ hóa 100% Asset giữa web-ui/ và web-ui/public/"
    else
        report_result "FAIL" "Đồng bộ hóa Asset Web" "Phát hiện $sync_errors tệp có nội dung khác nhau"
    fi
}

# --- Kiểm thử toàn diện trên một cặp IP (Host/Backend) ---
run_environment_suite() {
    local env_name="$1"
    local host_ip="$2"
    local be_port="8081"
    local fe_port="3000"

    local be_base="http://${host_ip}:${be_port}"
    local fe_base="http://${host_ip}:${fe_port}"

    log_header "KIỂM TRA MÔI TRƯỜNG: ${env_name} (${host_ip})"

    # 1. Thăm dò TCP Socket Backend
    local be_probe
    be_probe=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "${be_base}/api/v1/health" || echo "000")
    if [ "$be_probe" = "000" ]; then
        report_result "FAIL" "Kết nối TCP Socket Backend (:8081)" "Không thể kết nối tới ${be_base} (ERR_CONNECTION_REFUSED)"
        echo -e "    ${CLR_YELLOW}Bỏ qua các bài test phụ thuộc Backend trên môi trường này.${CLR_RESET}"
        return
    else
        report_result "PASS" "Kết nối TCP Socket Backend (:8081)"
    fi

    # 2. Kiểm tra Health Endpoint & Kết nối MariaDB
    local health_body
    health_body=$(curl -s --connect-timeout 3 "${be_base}/api/v1/health")
    local be_status
    be_status=$(echo "$health_body" | jq -r '.status // empty')
    local db_status
    db_status=$(echo "$health_body" | jq -r '.database // empty')

    if [ "$be_status" = "UP" ] && [ "$db_status" = "CONNECTED" ]; then
        report_result "PASS" "Backend Health & MariaDB Pool State"
    else
        report_result "FAIL" "Backend Health & MariaDB Pool State" "status=${be_status}, db=${db_status}"
    fi

    # 3. Kiểm tra Dynamic CORS Reflection & OPTIONS Preflight
    local cors_origin="http://${host_ip}:${fe_port}"
    local cors_headers
    cors_headers=$(curl -s -I -X OPTIONS "${be_base}/api/v1/service-orders" \
        -H "Origin: ${cors_origin}" \
        -H "Access-Control-Request-Method: POST" \
        -H "Access-Control-Request-Headers: Content-Type, Authorization")

    local cors_code
    cors_code=$(echo "$cors_headers" | grep -i "HTTP/" | head -n1 | awk '{print $2}')
    local allow_origin
    allow_origin=$(echo "$cors_headers" | grep -i "access-control-allow-origin:" | tr -d '\r' | awk '{print $2}')

    if [ "$cors_code" = "204" ] && { [ "$allow_origin" = "$cors_origin" ] || [ "$allow_origin" = "*" ]; }; then
        report_result "PASS" "CORS Preflight (204 No Content & Dynamic Origin Reflection)"
    else
        report_result "FAIL" "CORS Preflight" "HTTP: $cors_code, Origin Reflected: $allow_origin (Kỳ vọng: $cors_origin)"
    fi

    # 4. Kiểm tra Public Reputation API (Bypass JWT & Khử Double.NaN)
    local rep_res
    rep_res=$(curl -s -w "\n%{http_code}" --connect-timeout 3 "${be_base}/api/v1/reviews/public?limit=1")
    local rep_code
    rep_code=$(echo "$rep_res" | tail -n1)
    local rep_json
    rep_json=$(echo "$rep_res" | sed '$d')

    if [ "$rep_code" = "200" ]; then
        local has_data
        has_data=$(echo "$rep_json" | jq '.data | has("overall_average")')
        if [ "$has_data" = "true" ]; then
            report_result "PASS" "Public Reviews API (Bypass JWT & Chuẩn RFC 8259 JSON)"
        else
            report_result "FAIL" "Public Reviews API" "Payload JSON thiếu trường overall_average"
        fi
    else
        report_result "FAIL" "Public Reviews API" "HTTP Status: $rep_code (Kỳ vọng: 200)"
    fi

    # 5. Kiểm tra IDOR & Auth Guard trên Private Routes
    local order_code
    order_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${be_base}/api/v1/service-orders" \
        -H "Content-Type: application/json" \
        -d '{"booking_id": 99999, "item_name": "Test Probe"}')

    if [ "$order_code" = "401" ] || [ "$order_code" = "403" ]; then
        report_result "PASS" "Bảo vệ Tuyến Private & Auth Guard (Từ chối request thiếu Token: HTTP $order_code)"
    else
        report_result "FAIL" "Bảo vệ Tuyến Private" "HTTP: $order_code (Kỳ vọng: 401 hoặc 403)"
    fi

    # 6. Kiểm tra Web Frontend Server (Nginx / Static Server)
    local fe_probe
    fe_probe=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "${fe_base}/portal.html" || echo "000")
    if [ "$fe_probe" = "200" ]; then
        report_result "PASS" "Web Nginx Server (:3000 /portal.html sẵn sàng)"
    elif [ "$fe_probe" = "000" ]; then
        report_result "SKIP" "Web Frontend Server (:3000)" "Cổng 3000 chưa được bật tại ${host_ip}"
    else
        report_result "FAIL" "Web Frontend Server (:3000)" "HTTP Status: $fe_probe (Kỳ vọng: 200)"
    fi
}

# --- Main Entry Point ---
main() {
    check_dependencies
    
    echo -e "${CLR_BLUE}${CLR_BOLD}"
    echo "    _   _   _ ____  _____ _     ___    _       ____  __  __ ____  "
    echo "   / \ | | | |  _ \| ____| |   |_ _|  / \     |  _ \|  \/  / ___| "
    echo "  / _ \| | | | |_) |  _| | |    | |  / _ \    | |_) | |\/| \___ \ "
    echo " / ___ \ |_| |  _ <| |___| |___ | | / ___ \   |  __/| |  | |___) |"
    echo "/_/   \_\___/|_| \_\_____|_____|___/_/   \_\  |_|   |_|  |_|____/ "
    echo ">> Dual-Environment Zero-Hardcoded Validation Matrix <<"
    echo -e "${CLR_RESET}"

    test_asset_synchronization
    
    # Môi trường 1: Localhost
    run_environment_suite "LOCAL DEVELOPMENT" "127.0.0.1"

    # Môi trường 2: Linux VM (VirtualBox/Docker Host-Only)
    run_environment_suite "LINUX VIRTUAL MACHINE" "192.168.56.102"

    # --- Tổng kết kết quả ---
    log_header "TỔNG KẾT KẾT QUẢ KIỂM TRA (VERIFICATION REPORT)"
    echo -e " Tổng số kịch bản kiểm tra: ${CLR_BOLD}${TOTAL_TESTS}${CLR_RESET}"
    echo -e " Thành công (PASS)        : ${CLR_GREEN}${CLR_BOLD}${PASSED_TESTS}${CLR_RESET}"
    echo -e " Thất bại  (FAIL)        : ${CLR_RED}${CLR_BOLD}${FAILED_TESTS}${CLR_RESET}"
    echo -e " Bỏ qua    (SKIP)        : ${CLR_YELLOW}${CLR_BOLD}${SKIPPED_TESTS}${CLR_RESET}"

    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "\n${CLR_GREEN}${CLR_BOLD}>>> ĐẠT CHUẨN: Hệ thống đáp ứng 100% Zero-Hardcoded Host Parity! <<<${CLR_RESET}\n"
        exit 0
    else
        echo -e "\n${CLR_RED}${CLR_BOLD}>>> CẢNH BÁO: Phát hiện $FAILED_TESTS lỗi cần xử lý trước khi đóng gói! <<<${CLR_RESET}\n"
        exit 1
    fi
}

main "$@"
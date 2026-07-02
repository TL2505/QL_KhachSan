#!/bin/bash
# =============================================================================
# setup-ubuntu.sh — Script cài đặt lần đầu Ubuntu Server
# Cài Docker, clone repo, chạy backend, cài GitHub Actions Runner
#
# Cách dùng:
#   curl -fsSL https://raw.githubusercontent.com/<USER>/Quanlykhachsan/main/scripts/setup-ubuntu.sh | bash
#   hoặc:
#   chmod +x scripts/setup-ubuntu.sh
#   ./scripts/setup-ubuntu.sh
# =============================================================================

set -e

# ─── CẤU HÌNH — SỬA CÁC GIÁ TRỊ NÀY ──────────────────────────────────────
GITHUB_REPO_URL="${GITHUB_REPO_URL:-https://github.com/YOUR_USERNAME/Quanlykhachsan.git}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/hotel}"
RUNNER_VERSION="2.319.1"

# ─── MÀUJHIỂN THỊ ─────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC}   $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERR]${NC}  $1"; exit 1; }

# ─── 1. CẬP NHẬT HỆ THỐNG ─────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  Hotel Management System — Ubuntu Setup       ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

info "Bước 1/5: Cập nhật hệ thống..."
sudo apt-get update -qq
sudo apt-get upgrade -y -qq
success "Hệ thống đã được cập nhật."

# ─── 2. CÀI DOCKER ────────────────────────────────────────────────────────
info "Bước 2/5: Cài Docker Engine..."

if command -v docker &> /dev/null; then
    warn "Docker đã được cài đặt: $(docker --version)"
else
    sudo apt-get install -y -qq ca-certificates curl gnupg lsb-release
    
    # Thêm GPG key của Docker
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
        sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg
    
    # Thêm Docker repository
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
        sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    sudo apt-get update -qq
    sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # Cho phép user hiện tại dùng Docker không cần sudo
    sudo usermod -aG docker "$USER"
    success "Docker đã được cài đặt: $(docker --version)"
fi

# Kiểm tra Docker Compose
if docker compose version &> /dev/null; then
    success "Docker Compose đã sẵn sàng: $(docker compose version)"
else
    error "Docker Compose plugin không tìm thấy. Thử: sudo apt install docker-compose-plugin"
fi

# ─── 3. CLONE REPOSITORY ──────────────────────────────────────────────────
info "Bước 3/5: Clone repository..."

if [ -d "$DEPLOY_DIR/.git" ]; then
    warn "Repo đã tồn tại tại $DEPLOY_DIR. Đang pull code mới nhất..."
    cd "$DEPLOY_DIR"
    git pull origin main
else
    sudo mkdir -p "$DEPLOY_DIR"
    sudo chown "$USER":"$USER" "$DEPLOY_DIR"
    
    if [ "$GITHUB_REPO_URL" = "https://github.com/YOUR_USERNAME/Quanlykhachsan.git" ]; then
        warn "Chưa cấu hình GITHUB_REPO_URL. Nhập URL repo GitHub:"
        read -r -p "GitHub URL: " GITHUB_REPO_URL
    fi
    
    git clone "$GITHUB_REPO_URL" "$DEPLOY_DIR"
    success "Đã clone repo vào $DEPLOY_DIR"
fi

cd "$DEPLOY_DIR"

# ─── 4. KHỞI ĐỘNG DOCKER STACK ────────────────────────────────────────────
info "Bước 4/5: Khởi động Backend Stack (MariaDB + Java API + Adminer)..."

# Chạy với newgrp để áp dụng group docker mà không cần logout
sg docker -c "docker compose up -d --build"

# Đợi backend health check
info "Đang chờ backend khởi động (tối đa 90 giây)..."
MAX_WAIT=90
WAITED=0
until curl -s http://localhost:8081/api/health 2>/dev/null | grep -q '"status":"success"'; do
    if [ $WAITED -ge $MAX_WAIT ]; then
        warn "Backend chưa phản hồi sau ${MAX_WAIT}s. Xem log: docker compose logs backend"
        break
    fi
    echo -n "."
    sleep 3
    WAITED=$((WAITED + 3))
done
echo ""
success "Backend đang chạy tại http://localhost:8081"

# ─── 5. SETUP CRON AUTO-DEPLOY ────────────────────────────────────────────
info "Bước 5/5: Cấu hình tự động cập nhật code..."

# Thêm cron job để tự pull & deploy mỗi 5 phút
CRON_CMD="*/5 * * * * cd $DEPLOY_DIR && git pull origin main --quiet && sg docker -c 'docker compose up -d --build' >> /var/log/hotel-deploy.log 2>&1"

# Kiểm tra cron đã có chưa
if crontab -l 2>/dev/null | grep -q "hotel-deploy"; then
    warn "Cron job đã tồn tại."
else
    (crontab -l 2>/dev/null; echo "$CRON_CMD") | crontab -
    success "Đã thêm cron job: tự deploy mỗi 5 phút từ nhánh main"
fi

# ─── TÓM TẮT ──────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║              ✅ CÀI ĐẶT HOÀN TẤT                         ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Backend API  : http://$(hostname -I | awk '{print $1}'):8081/api        ║"
echo "║  Health Check : http://$(hostname -I | awk '{print $1}'):8081/api/health  ║"
echo "║  Adminer DB   : http://$(hostname -I | awk '{print $1}'):8082             ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Adminer login:                                          ║"
echo "║    System   : MySQL                                      ║"
echo "║    Server   : db                                         ║"
echo "║    User     : root                                       ║"
echo "║    Password : rootpass                                   ║"
echo "║    Database : hotel_prod_db                              ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Xem logs   : docker compose logs -f backend            ║"
echo "║  Dừng       : docker compose down                       ║"
echo "║  Deploy mới : ./scripts/deploy.sh                       ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "⚠️  Nếu docker không chạy được (permission denied),"
echo "   hãy chạy: newgrp docker  rồi thử lại."
echo ""

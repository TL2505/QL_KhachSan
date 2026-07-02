#!/bin/bash
# =============================================================================
# deploy.sh — Script deploy Quản Lý Khách Sạn lên Ubuntu Server
# Tự động pull code mới nhất từ GitHub nhánh main và restart Docker stack
#
# Cách dùng:
#   chmod +x scripts/deploy.sh
#   ./scripts/deploy.sh
#   ./scripts/deploy.sh --branch develop   (deploy nhánh khác)
# =============================================================================

set -e  # Dừng ngay nếu có lỗi

# ─── CẤU HÌNH ──────────────────────────────────────────────────────────────
DEPLOY_DIR="${DEPLOY_DIR:-/opt/hotel}"
BRANCH="${1:-main}"
LOG_FILE="/var/log/hotel-deploy.log"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# Cho phép ghi đối số --branch
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --branch) BRANCH="$2"; shift ;;
    esac
    shift
done

# ─── HÀM TIỆN ÍCH ──────────────────────────────────────────────────────────
log() {
    echo "[${TIMESTAMP}] $1" | tee -a "$LOG_FILE"
}

check_dependency() {
    if ! command -v "$1" &> /dev/null; then
        log "❌ Lỗi: '$1' chưa được cài đặt. Chạy: sudo apt install $2"
        exit 1
    fi
}

# ─── KIỂM TRA DEPENDENCIES ─────────────────────────────────────────────────
log "═══════════════════════════════════════════"
log "🚀 Bắt đầu deploy Hotel Management System"
log "═══════════════════════════════════════════"

check_dependency "git"     "git"
check_dependency "docker"  "docker.io"

# Docker Compose v2 (plugin)
if ! docker compose version &> /dev/null; then
    log "❌ Lỗi: 'docker compose' (v2) chưa được cài đặt."
    log "   Cài đặt: sudo apt install docker-compose-plugin"
    exit 1
fi

# ─── DI CHUYỂN ĐẾN THƯ MỤC DỰ ÁN ──────────────────────────────────────────
if [ ! -d "$DEPLOY_DIR" ]; then
    log "❌ Thư mục deploy không tồn tại: $DEPLOY_DIR"
    log "   Hãy clone repo trước: git clone <URL> $DEPLOY_DIR"
    exit 1
fi

cd "$DEPLOY_DIR"
log "📂 Thư mục: $DEPLOY_DIR"

# ─── PULL CODE MỚI NHẤT ────────────────────────────────────────────────────
log "⬇️  Đang pull code mới nhất từ nhánh '$BRANCH'..."

# Lưu thông tin commit hiện tại để so sánh
OLD_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo "unknown")

git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

NEW_COMMIT=$(git rev-parse HEAD)

if [ "$OLD_COMMIT" = "$NEW_COMMIT" ]; then
    log "✅ Code đã ở phiên bản mới nhất (commit: ${NEW_COMMIT:0:8}). Không cần rebuild."
    # Vẫn đảm bảo containers đang chạy
    docker compose up -d
    log "🏁 Deploy hoàn thành (không thay đổi)."
    exit 0
fi

log "📝 Cập nhật từ commit ${OLD_COMMIT:0:8} → ${NEW_COMMIT:0:8}"
git log --oneline "$OLD_COMMIT..$NEW_COMMIT" 2>/dev/null | head -5 | while read line; do
    log "   • $line"
done

# ─── BACKUP DATABASE (AN TOÀN) ─────────────────────────────────────────────
if docker compose ps db | grep -q "Up"; then
    BACKUP_DIR="$DEPLOY_DIR/backups"
    mkdir -p "$BACKUP_DIR"
    BACKUP_FILE="$BACKUP_DIR/hotel_db_$(date +%Y%m%d_%H%M%S).sql"
    log "💾 Đang backup database → $BACKUP_FILE"
    docker compose exec -T db mysqldump -uroot -prootpass hotel_prod_db > "$BACKUP_FILE" 2>/dev/null || \
        log "⚠️  Bỏ qua backup (DB chưa có dữ liệu hoặc chưa khởi động)"
    
    # Giữ lại tối đa 7 bản backup gần nhất
    ls -t "$BACKUP_DIR"/*.sql 2>/dev/null | tail -n +8 | xargs rm -f 2>/dev/null || true
fi

# ─── REBUILD VÀ RESTART SERVICES ───────────────────────────────────────────
log "🐳 Đang rebuild Docker images..."
docker compose build --no-cache backend

log "🔄 Đang khởi động lại services..."
docker compose up -d --build

# ─── CHỜ BACKEND HEALTHY ────────────────────────────────────────────────────
log "⏳ Đang chờ backend khởi động..."
MAX_WAIT=60
WAITED=0
until curl -s http://localhost:8081/api/health | grep -q '"status":"success"' 2>/dev/null; do
    if [ $WAITED -ge $MAX_WAIT ]; then
        log "❌ Backend không phản hồi sau ${MAX_WAIT}s. Xem logs: docker compose logs backend"
        exit 1
    fi
    sleep 3
    WAITED=$((WAITED + 3))
done

log "✅ Backend đang chạy tại: http://localhost:8081"
log "🏁 Deploy thành công! Commit: ${NEW_COMMIT:0:8}"
log "═══════════════════════════════════════════"

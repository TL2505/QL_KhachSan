# 🚀 DEPLOYMENT.md — Hướng Dẫn Triển Khai Chi Tiết

> **Mô hình:** Backend chạy trên Ubuntu Server (Docker), Frontend chạy trên máy khách.
> Code tự động cập nhật từ GitHub nhánh `main` khi có push mới.

---

## MỤC LỤC

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Cài đặt Ubuntu Server lần đầu](#2-cài-đặt-ubuntu-server-lần-đầu)
3. [Setup tự động deploy từ GitHub](#3-setup-tự-động-deploy-từ-github)
4. [Chạy Frontend trên máy khách](#4-chạy-frontend-trên-máy-khách)
5. [Kiểm tra hệ thống](#5-kiểm-tra-hệ-thống)
6. [Quản lý & Vận hành](#6-quản-lý--vận-hành)
7. [Xử lý sự cố](#7-xử-lý-sự-cố)

---

## 1. Yêu Cầu Hệ Thống

### Ubuntu Server
| Thành phần | Phiên bản tối thiểu |
|------------|---------------------|
| Ubuntu | 22.04 LTS |
| Docker | 24.x+ |
| Docker Compose | v2.x+ (plugin, dùng `docker compose` không dấu gạch ngang) |
| RAM | 1 GB (khuyến nghị 2 GB) |
| Disk | 5 GB trống |
| Cổng mở | 8081 (Backend API), 8082 (Adminer, tuỳ chọn) |

### Máy Khách (Frontend)
| Thành phần | Ghi chú |
|------------|---------|
| Node.js 18+ | Chỉ cần nếu dùng `node server.js` |
| Trình duyệt | Chrome/Firefox/Edge (ES Modules support) |

---

## 2. Cài Đặt Ubuntu Server Lần Đầu

### 2.1 Cài Docker & Docker Compose

```bash
# Cập nhật hệ thống
sudo apt update && sudo apt upgrade -y

# Cài Docker Engine
sudo apt install -y ca-certificates curl gnupg lsb-release
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Cho phép user hiện tại dùng Docker không cần sudo (đăng xuất & đăng nhập lại)
sudo usermod -aG docker $USER
newgrp docker

# Kiểm tra
docker --version
docker compose version
```

### 2.2 Clone Repository & Chạy Backend Lần Đầu

```bash
# Chọn thư mục deploy
cd /opt
sudo mkdir -p hotel && sudo chown $USER:$USER hotel
cd hotel

# Clone repo (thay bằng URL GitHub thực tế của bạn)
git clone https://github.com/<YOUR_USERNAME>/Quanlykhachsan.git .
# Hoặc nếu repo private:
# git clone https://<TOKEN>@github.com/<YOUR_USERNAME>/Quanlykhachsan.git .

# Khởi động toàn bộ stack
docker compose up -d --build

# Xem logs để chắc chắn đã start thành công
docker compose logs -f backend
```

### 2.3 Mở Firewall (nếu dùng UFW)

```bash
sudo ufw allow 8081/tcp   # Backend API
sudo ufw allow 8082/tcp   # Adminer (DB admin UI) — chỉ mở nếu cần
sudo ufw status
```

---

## 3. Setup Tự Động Deploy Từ GitHub

### Phương án A: GitHub Actions + Self-Hosted Runner (Khuyến nghị)

**GitHub Actions đã được cấu hình tại `.github/workflows/deploy.yml`.**
Mỗi khi push lên nhánh `main`, GitHub sẽ SSH vào server và re-deploy tự động.

#### Bước 1: Cài GitHub Actions Runner trên Ubuntu Server

```bash
# Tạo thư mục runner
mkdir -p /opt/actions-runner && cd /opt/actions-runner

# Tải runner (kiểm tra phiên bản mới nhất tại https://github.com/actions/runner/releases)
RUNNER_VERSION="2.319.1"
curl -o actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz -L \
  https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz
tar xzf actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz
```

#### Bước 2: Đăng ký Runner với GitHub Repo

1. Vào GitHub repo của bạn → **Settings** → **Actions** → **Runners** → **New self-hosted runner**
2. Sao chép token và URL hiện trên trang đó
3. Chạy lệnh đăng ký:

```bash
cd /opt/actions-runner

# Điền URL repo và token từ GitHub vào đây
./config.sh --url https://github.com/<YOUR_USERNAME>/Quanlykhachsan \
             --token <RUNNER_TOKEN>

# Chạy runner như service systemd (tự khởi động khi reboot)
sudo ./svc.sh install
sudo ./svc.sh start
sudo ./svc.sh status
```

#### Bước 3: Cấp quyền Docker cho runner

```bash
# Runner chạy dưới user nào thì cấp quyền docker cho user đó
sudo usermod -aG docker $(whoami)
# Hoặc:
# sudo usermod -aG docker actions-runner
```

#### Kết quả: Mỗi khi push lên `main`
```
Developer push code → GitHub Actions trigger → Runner trên Ubuntu Server
→ docker compose down → docker compose up -d --build → Done ✅
```

---

### Phương án B: Cron Job tự pull & deploy mỗi 5 phút

Dùng khi KHÔNG muốn cài GitHub Actions Runner.

```bash
# Chỉnh sửa crontab
crontab -e

# Thêm dòng này vào cuối file:
*/5 * * * * cd /opt/hotel && git pull origin main --quiet && docker compose up -d --build >> /var/log/hotel-deploy.log 2>&1
```

---

### Phương án C: Script deploy thủ công

```bash
# Chạy script deploy.sh (tạo ở bước dưới)
/opt/hotel/scripts/deploy.sh
```

---

## 4. Chạy Frontend Trên Máy Khách

### Option A: Node.js Static Server (Khuyến nghị)

```bash
# Yêu cầu Node.js 18+
node --version

# Clone hoặc copy thư mục web-ui về máy khách
git clone https://github.com/<YOUR_USERNAME>/Quanlykhachsan.git
cd Quanlykhachsan/web-ui

# Khởi động (KHÔNG cần npm install)
node server.js

# Truy cập
open http://localhost:3000
```

Sau khi vào ứng dụng, đến **Settings → API Configuration** và nhập:
```
http://<UBUNTU_SERVER_IP>:8081/api
```

---

### Option B: Mở trực tiếp file HTML

```bash
# Không cần cài gì thêm — chỉ cần trình duyệt
# Mở file này trong Chrome/Firefox/Edge:
web-ui/public/index.html
```

> ⚠️ Một số browser chặn ES Modules khi mở từ `file://`. Nếu bị lỗi, dùng Option A.

---

### Option C: Dùng http-server (nếu đã có npm)

```bash
npx http-server web-ui/public -p 3000 --cors
# Truy cập: http://localhost:3000
```

---

### Cấu Hình API URL Trên Frontend

Frontend tự động phát hiện backend URL theo logic:
```javascript
// Nếu frontend chạy trên localhost → backend là localhost:8081
const DEFAULT_API_BASE = window.location.hostname === "localhost"
    ? "http://localhost:8081/api"
    : `http://${window.location.hostname}:8081/api`;
```

**Để trỏ frontend đến Ubuntu Server:**
1. Mở ứng dụng tại `http://localhost:3000`
2. Vào **Settings** → **API Server URL**
3. Nhập: `http://<UBUNTU_SERVER_IP>:8081/api`
4. Nhấn **Save** → trang reload → đăng nhập

Hoặc set thẳng trong localStorage (dùng DevTools Console):
```javascript
localStorage.setItem("hotel_api_url", "http://192.168.1.100:8081/api");
location.reload();
```

---

## 5. Kiểm Tra Hệ Thống

### Kiểm tra Backend đang chạy:
```bash
# Từ Ubuntu Server
curl http://localhost:8081/api/health
# → {"status":"success","data":"OK"}

# Từ máy khách (thay IP)
curl http://192.168.1.100:8081/api/health
```

### Kiểm tra Database:
```bash
# Adminer web UI (nếu mở port 8082)
http://<UBUNTU_SERVER_IP>:8082
# Login: System=MySQL, Server=db, User=root, Password=rootpass, Database=hotel_prod_db
```

### Kiểm tra tất cả services Docker:
```bash
docker compose ps
# Tất cả phải ở trạng thái "Up"

docker compose logs backend --tail=50
docker compose logs db --tail=20
```

---

## 6. Quản Lý & Vận Hành

### Xem logs realtime:
```bash
docker compose logs -f               # Tất cả services
docker compose logs -f backend       # Chỉ backend
docker compose logs -f db            # Chỉ database
```

### Restart một service:
```bash
docker compose restart backend
docker compose restart db
```

### Deploy thủ công phiên bản mới:
```bash
cd /opt/hotel
git pull origin main
docker compose down
docker compose up -d --build
```

### Backup database:
```bash
docker compose exec db mysqldump -uroot -prootpass hotel_prod_db > backup_$(date +%Y%m%d).sql
```

### Restore database:
```bash
docker compose exec -T db mysql -uroot -prootpass hotel_prod_db < backup_20260101.sql
```

### Xem thống kê tài nguyên:
```bash
docker stats
```

---

## 7. Xử Lý Sự Cố

### Backend không start (lỗi DB connection):
```bash
# Kiểm tra DB có chạy không
docker compose ps db
# Nếu db chưa healthy, restart:
docker compose restart db
# Đợi 10 giây rồi restart backend:
sleep 10 && docker compose restart backend
```

### Frontend không kết nối được API:
1. Kiểm tra `curl http://<SERVER_IP>:8081/api/health` từ máy khách
2. Nếu không được → kiểm tra firewall: `sudo ufw status`
3. Kiểm tra API URL trong Settings có đúng không

### Lỗi CORS trên trình duyệt:
- Backend đang dùng `Access-Control-Allow-Origin: *` nên không bị CORS
- Nếu vẫn lỗi: đảm bảo URL API đúng định dạng (không có trailing slash)

### Lỗi compile Java khi build Docker:
```bash
# Xem log build chi tiết
docker compose build --no-cache backend 2>&1 | tail -50
```

### Runner không nhận job GitHub Actions:
```bash
cd /opt/actions-runner
sudo ./svc.sh status
sudo ./svc.sh start
```

---

## 📊 Cổng & Địa Chỉ Tóm Tắt

| Service | Địa chỉ | Ghi chú |
|---------|---------|---------|
| Backend API | `http://<SERVER_IP>:8081` | Mở cho máy khách |
| Adminer DB UI | `http://<SERVER_IP>:8082` | Tuỳ chọn, chỉ dùng nội bộ |
| MariaDB | `<SERVER_IP>:3307` | Chỉ dùng nội bộ |
| Frontend | `http://localhost:3000` | Chạy trên máy khách |

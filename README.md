# 🏨 Quản Lý Khách Sạn — Hotel Management System

Hệ thống quản lý khách sạn toàn diện: đặt phòng, check-in/check-out, hóa đơn, khuyến mãi, loyalty, báo cáo và chat nội bộ.

## 📚 Tài Liệu

| File | Nội dung |
|------|---------|
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | **Đọc trước** — Kiến trúc, API, cấu trúc thư mục, lưu ý cho AI agent |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Hướng dẫn triển khai chi tiết (Ubuntu Server + máy khách) |

## ⚡ Quick Start

### Backend (Ubuntu Server)
```bash
# Clone repo
git clone <GITHUB_URL> && cd Quanlykhachsan

# Khởi động toàn bộ stack (MariaDB + Java API)
docker compose up -d --build

# Kiểm tra
curl http://localhost:8081/api/health
```

### Frontend (Máy khách)
```bash
# Node.js static server
cd web-ui && node server.js
# Vào: http://localhost:3000
# Settings → đổi API URL thành http://<SERVER_IP>:8081/api
```

## 🏗️ Công Nghệ

| Thành phần | Công nghệ |
|------------|-----------|
| Backend | Java 17, `com.sun.net.httpserver`, JDBC |
| Frontend | Vanilla HTML/CSS/JavaScript (ES Modules) |
| Database | MariaDB 11 / MySQL 8 |
| Static Server | Node.js 20 (built-in `http` module) |
| Deploy | Docker, Docker Compose, GitHub Actions |

## 🔑 Biến Môi Trường Backend

```
DB_HOST=db
DB_PORT=3306
DB_NAME=hotel_prod_db
DB_USER=root
DB_PASSWORD=rootpass
```

## 📋 API Nhanh

- Health: `GET /api/health`
- Login: `POST /api/auth/login` → trả JWT token
- Rooms: `GET /api/rooms`
- Bookings: `GET /api/bookings`

Xem đầy đủ trong [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md#-api-endpoints-đầy-đủ).

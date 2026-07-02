# 🏨 Quản Lý Khách Sạn — Project Context

> Tài liệu này được viết để bất kỳ AI agent hoặc developer nào cũng có thể hiểu toàn bộ kiến trúc
> và triển khai dự án chỉ qua một file duy nhất.

---

## 📌 Tổng Quan Dự Án

| Mục | Chi tiết |
|-----|---------|
| **Tên dự án** | Quản Lý Khách Sạn (Hotel Management System) |
| **Ngôn ngữ Backend** | Java 17 (thuần, không dùng Spring/Maven/Gradle) |
| **Frontend** | Vanilla HTML + CSS + JavaScript (ES Modules) |
| **Cơ sở dữ liệu** | MariaDB 11 / MySQL 8 |
| **Static File Server** | Node.js 20 (server.js thuần, không dùng Express) |
| **Cổng Backend** | `8081` |
| **Cổng Frontend** | `3000` |
| **Cổng DB Admin** | `8082` (Adminer) |
| **GitHub Repository** | Nhánh `main` là nhánh production |
| **Triển khai** | Docker + Docker Compose trên Ubuntu Server |

---

## 🏗️ Kiến Trúc Hệ Thống

```
┌──────────────────────────────────────────────────────────────┐
│                  UBUNTU SERVER (Ubuntu 22.04+)               │
│                                                              │
│  ┌─────────────┐   ┌──────────────┐   ┌──────────────────┐  │
│  │  MariaDB:11 │   │ Java Backend │   │ Adminer (DB UI)  │  │
│  │  Port: 3307 │◄──│  Port: 8081  │   │   Port: 8082     │  │
│  └─────────────┘   └──────────────┘   └──────────────────┘  │
│       Docker           Docker                Docker           │
└──────────────────────────────────────────────────────────────┘
                              ▲
                              │ HTTP REST API (port 8081)
                              │
┌──────────────────────────────────────────────────────────────┐
│               MÁY KHÁCH (Windows / Mac / Linux)              │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Node.js Static Server  →  http://localhost:3000       │  │
│  │  (cd web-ui && node server.js)                         │  │
│  └────────────────────────────────────────────────────────┘  │
│                          hoặc                                │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Mở trực tiếp: web-ui/public/index.html trong browser │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

**Kiến trúc Backend (3-tier):**
- **Controller** → tiếp nhận HTTP request, kiểm tra JWT, ánh xạ DTO, gọi Service
- **Service** → xử lý business logic (đặt phòng, tính tiền, loyalty point…)
- **DAO** → tương tác trực tiếp với MySQL qua JDBC

---

## 📁 Cấu Trúc Thư Mục

```
Quanlykhachsan/
├── src/quanlykhachsan/
│   ├── backend/
│   │   ├── Main.java              # Entry point — khởi động HttpServer port 8081
│   │   ├── auth/                  # Login/Register + JWT generation & validation
│   │   ├── booking/               # Đặt phòng, CheckIn/CheckOut, Payment, Invoice
│   │   ├── customer/              # Khách hàng + Loyalty (điểm thân thiết)
│   │   ├── hotelservice/          # Dịch vụ khách sạn (minibar, spa…)
│   │   ├── interaction/           # Chat (real-time poll) + Review (đánh giá)
│   │   ├── promotion/             # Mã khuyến mãi, giảm giá
│   │   ├── report/                # Báo cáo doanh thu, thống kê
│   │   ├── room/                  # CRUD phòng, trạng thái phòng
│   │   ├── user/                  # CRUD nhân viên + phân quyền (ADMIN/STAFF)
│   │   └── utils/
│   │       ├── BCrypt.java        # Mã hoá mật khẩu
│   │       ├── JwtUtil.java       # Tạo/verify JWT token
│   │       ├── DBconn.java        # Kết nối JDBC (đọc DB_HOST/PORT/... từ env)
│   │       ├── CorsFilter.java    # CORS header cho mọi request
│   │       └── UpdateDbUtility.java # Auto-migrate schema khi khởi động
│   └── frontend/                  # Swing UI cũ (KHÔNG dùng trong web deploy)
│
├── web-ui/                        # ← FRONTEND chạy trên máy khách
│   ├── server.js                  # Node.js static server (built-in http module)
│   └── public/
│       ├── index.html             # SPA entry point (single page)
│       ├── style.css              # Global styles (~30KB)
│       ├── app.js                 # Client-side router + render logic (~18KB)
│       ├── api.js                 # HTTP client wrapper (fetch + auth + ngrok bypass)
│       └── js/
│           ├── view-admin.js      # Admin dashboard loader
│           ├── view-staff.js      # Staff dashboard loader
│           ├── view-customer.js   # Customer dashboard loader
│           ├── view-shared.js     # Shared components (profile, settings…)
│           ├── admin/             # Màn hình quản lý (rooms, users, reports…)
│           ├── staff/             # Màn hình nhân viên (booking, chat…)
│           └── customer/          # Màn hình khách (booking, promotions…)
│
├── database/
│   └── hotel.sql                  # Schema đầy đủ — tự động import lần đầu
│
├── lib/                           # Thư viện Java (KHÔNG dùng Maven)
│   ├── gson-2.10.1.jar            # JSON parse/serialize
│   ├── mysql-connector-j-8.0.31.jar  # JDBC driver cho MySQL/MariaDB
│   ├── itextpdf-5.5.13.3.jar     # Xuất hóa đơn PDF
│   ├── jfreechart-1.5.3.jar      # Biểu đồ doanh thu (Swing)
│   ├── flatlaf-3.4.1.jar         # UI theme FlatLaf (Swing)
│   └── jcalendar-1.4.jar         # DatePicker component (Swing)
│
├── docs/                          # Tài liệu kỹ thuật
├── Dockerfile                     # Build & run backend Java trong Docker
├── docker-compose.yml             # Stack: MariaDB + Backend + Adminer
├── config.properties              # Cấu hình URL API (dùng bởi Swing frontend)
├── build.xml                      # Ant build file (alternative cho Windows)
├── build_project.bat              # Script build thủ công trên Windows
├── seed_data.sql                  # Dữ liệu mẫu để test
└── .github/workflows/deploy.yml  # GitHub Actions CI/CD (self-hosted runner)
```

---

## 🔗 API Endpoints Đầy Đủ

Backend lắng nghe tại `http://<SERVER_IP>:8081`

| Endpoint | Method | Chức năng | Auth Required |
|----------|--------|-----------|---------------|
| `/api/health` | GET | Health check | ❌ |
| `/api/auth/login` | POST | Đăng nhập → JWT | ❌ |
| `/api/auth/register` | POST | Đăng ký tài khoản | ❌ |
| `/api/rooms` | GET/POST/PUT/DELETE | CRUD phòng | ✅ |
| `/api/users` | GET/POST/PUT/DELETE | CRUD nhân viên | ✅ ADMIN |
| `/api/users/update-profile` | PUT | Cập nhật hồ sơ | ✅ |
| `/api/users/change-password` | PUT | Đổi mật khẩu | ✅ |
| `/api/users/update-theme` | PUT | Lưu theme (dark/light) | ✅ |
| `/api/roles` | GET | Danh sách vai trò | ✅ ADMIN |
| `/api/customers` | GET/POST/PUT | CRUD khách hàng | ✅ |
| `/api/bookings` | GET/POST/PUT | Đặt phòng / Check-in / Check-out | ✅ |
| `/api/payments` | GET/POST | Thanh toán hóa đơn | ✅ |
| `/api/invoices` | GET/POST | Xem / Xuất hóa đơn PDF | ✅ |
| `/api/reviews` | GET/POST | Đánh giá phòng | ✅ |
| `/api/chat` | GET/POST | Nhắn tin (long-poll) | ✅ |
| `/api/promotions` | GET/POST/DELETE | Quản lý khuyến mãi | ✅ |
| `/api/loyalty` | GET/POST | Điểm thân thiết | ✅ |
| `/api/reports` | GET | Báo cáo thống kê doanh thu | ✅ ADMIN |

**Chuẩn phản hồi JSON:**
```json
// Thành công:
{ "status": "success", "data": { ... } }
// Lỗi:
{ "status": "error", "message": "Mô tả lỗi" }
```

**Xác thực:**
- Header `Authorization: Bearer <jwt_token>` — gửi kèm mọi request cần auth
- Header `X-User-Role: 1` — role ID (1=ADMIN, 2=STAFF, 3=CUSTOMER)

---

## ⚙️ Biến Môi Trường (Backend Docker)

| Biến | Mặc định Docker | Mô tả |
|------|-----------------|-------|
| `DB_HOST` | `db` (tên service trong Compose) | Hostname MariaDB |
| `DB_PORT` | `3306` | Cổng MariaDB bên trong Docker network |
| `DB_NAME` | `hotel_prod_db` | Tên database |
| `DB_USER` | `root` | Username |
| `DB_PASSWORD` | `rootpass` | Mật khẩu |

---

## 🔐 Tài Khoản Mặc Định (sau khi import hotel.sql)

| Vai trò | Username | Password |
|---------|----------|---------|
| Admin | `admin` | `admin123` |
| Staff | `staff` | `staff123` |
| Customer | Tạo qua `/api/auth/register` | — |

---

## 📌 Lưu Ý Quan Trọng Cho AI Agent

1. **KHÔNG dùng Maven/Gradle** — compile thủ công bằng `javac` với classpath từ `lib/*.jar`
2. **KHÔNG dùng Spring Boot** — Backend là `com.sun.net.httpserver.HttpServer` của JDK
3. **Frontend thuần (không React/Vue)** — `api.js` tự phát hiện backend URL qua `window.location.hostname`
4. **`web-ui/server.js` không cần `npm install`** — chỉ dùng Node.js built-in modules
5. **DB schema tự động** — `hotel.sql` mount vào container, `UpdateDbUtility.java` migrate khi start
6. **CORS đang mở `*`** — có thể giới hạn sau khi production stable
7. **Frontend có thể mở trực tiếp** — `index.html` chạy được mà không cần web server nếu backend cùng origin

---

## 🚀 Quick Deploy (Tóm Tắt)

### Backend trên Ubuntu Server:
```bash
docker compose up -d --build
curl http://localhost:8081/api/health  # → {"status":"success","data":"OK"}
```

### Frontend trên máy khách:
```bash
# Node.js static server
cd web-ui && node server.js
# Vào: http://localhost:3000 → Settings → đổi API URL thành http://<SERVER_IP>:8081/api
```

> Xem hướng dẫn chi tiết trong `DEPLOYMENT.md`

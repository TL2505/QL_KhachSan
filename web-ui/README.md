# Aurelia Hotel - Web UI

Giao diện web quản lý khách sạn Aurelia, viết bằng HTML, CSS và JavaScript thuần (không cần build).

## 🚀 Cách chạy (Phát triển cục bộ)

1. Đảm bảo **Node.js** đã được cài đặt.
2. Mở terminal tại thư mục `web-ui` này.
3. Khởi động web server:
   ```bash
   node server.js
   ```
4. Truy cập `http://localhost:3000` trên trình duyệt.

*Lưu ý: Backend Java phải đang chạy tại `http://localhost:8081`.*

---

## 🐳 Triển khai Docker (Ubuntu Server)

Từ thư mục gốc dự án (`Quanlykhachsan/`), chạy:

```bash
docker compose up -d --build
```

Sau khi khởi động, người dùng có thể truy cập:

| Dịch vụ | URL | Mô tả |
|---|---|---|
| 🌐 Web UI | `http://<IP-server>:3000` | Giao diện quản lý |
| ⚙️ Backend API | `http://<IP-server>:8081/api` | REST API Java |
| 🗄️ Adminer (DB) | `http://<IP-server>:8082` | Quản trị database |

---

## 📁 Cấu trúc mã nguồn

```
web-ui/
├── server.js              ← Node.js static file server
├── public/
│   ├── index.html         ← Khung HTML chính
│   ├── style.css          ← Toàn bộ giao diện (Dark mode)
│   ├── api.js             ← Client fetch wrapper (tự detect server URL)
│   ├── app.js             ← App shell, router, login/logout
│   └── js/
│       ├── view-admin.js  ← Coordinator Admin
│       ├── view-staff.js  ← Coordinator Staff
│       ├── view-customer.js ← Coordinator Customer
│       ├── view-shared.js ← Hồ sơ cá nhân
│       ├── admin/         ← 6 module tính năng Admin
│       ├── staff/         ← 6 module tính năng Lễ tân
│       └── customer/      ← 5 module tính năng Khách hàng
```

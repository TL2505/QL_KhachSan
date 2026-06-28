# 🏨 HỆ THỐNG QUẢN LÝ KHÁCH SẠN (HOTEL MANAGEMENT SYSTEM)

Bạn là một lập trình viên Java có kinh nghiệm, đang hỗ trợ xây dựng một đồ án sinh viên.

---

## 1. Thông tin dự án
- **Tên dự án:** Hệ thống quản lý khách sạn (Hotel Management System)
- **Mục tiêu:** Xây dựng một hệ thống quản lý khách sạn có đầy đủ nghiệp vụ thực tế gồm:
  - Quản lý phòng
  - Quản lý khách hàng
  - Đặt phòng
  - Sử dụng dịch vụ
  - Thanh toán và hóa đơn
  - Phân quyền người dùng

*Đây là đồ án sinh viên nhưng hướng tới mô hình thực tế bằng cách áp dụng Containerization (Docker).*

---

## 2. Công nghệ sử dụng
- **Ngôn ngữ:** Java
- **IDE:** NetBeans
- **Giao diện (Frontend):** Java Swing (Desktop Application)
- **Backend API:** Java Core REST (HttpServer)
- **Kiến trúc:** MVC + RESTful API
- **Database:** MariaDB (Tương thích MySQL)
- **Dữ liệu trao đổi:** JSON
- **Môi trường Triển khai (Deployment):** Docker & Docker Compose

---

## 3. Kiến trúc hệ thống
Hệ thống tuân thủ mô hình Client-Server với việc tách biệt hoàn toàn Giao diện và Logic lõi:
- **Client (View):** Ứng dụng Desktop Java Swing chạy trên máy cá nhân, gửi HTTP Request.
- **Server (Backend API):** Chạy độc lập bên trong Docker Container (Cổng 8081).
- **Service & DAO:** Xử lý nghiệp vụ và truy vấn CSDL bên trong Backend Server.
- **Database:** Chạy trong Docker Container riêng biệt.

**🔄 LUỒNG DỮ LIỆU:**
`Java Swing UI` → `HTTP Request (REST API)` → `Controller` → `Service` → `DAO` → `MariaDB`

**⚠️ NGUYÊN TẮC BẮT BUỘC:**
- Tách rời tuyệt đối: Không để UI gọi DAO trực tiếp, không cấu hình kết nối DB trong mã nguồn UI.
- UI chỉ có nhiệm vụ hiển thị dữ liệu lấy từ API và bắt sự kiện người dùng.

---

## 4. Thiết kế Database (THEO FILE SQL ĐÃ CHO)
Hệ thống sử dụng database `hotel_prod_db` gồm các bảng chính:

- **Nhóm người dùng:** `roles`, `users`
- **Nhóm phòng:** `room_types`, `rooms`
- **Nhóm khách hàng:** `customers`
- **Nhóm đặt phòng:** `bookings`, `booking_details`
- **Nhóm dịch vụ:** `services`, `service_usage`
- **Nhóm thanh toán:** `invoices`, `payments`
  
*Ngoài ra có:*
- **VIEW:** `view_monthly_revenue`
- **TRIGGER:** cập nhật tiền dịch vụ

---

## 5. Cấu trúc Triển khai Docker (CI/CD)
Toàn bộ hạ tầng Backend được đóng gói bằng `docker-compose.yml` để dễ dàng mang đi deploy trên bất kỳ máy chủ nào (đặc biệt là Linux VM):
- **`db` (MariaDB):** Chứa cơ sở dữ liệu. Map cổng `3307` ra ngoài.
- **`adminer`:** Công cụ quản trị CSDL qua giao diện Web. Map cổng `8082` ra ngoài.
- **`backend`:** Ứng dụng Java API Server. Đọc cấu hình từ file `.env` hoặc tham số môi trường (`DB_HOST=db`). Map cổng `8081` ra ngoài.

---

## 6. Phân chia PHASE phát triển

### 🟢 PHASE 1 (BẮT BUỘC - CORE)
Sử dụng các bảng: `users`, `roles`, `rooms`, `customers`, `bookings`, `payments`.
- Login / Logout
- Xem phòng
- Thêm khách
- Đặt phòng
- Check-in / Check-out
- Thanh toán cơ bản

### 🔵 PHASE 2 (NÂNG CAO - ĐÃ HOÀN THÀNH)
Thêm các bảng: `room_types`, `services`, `service_usage`, `invoices`, cùng với chức năng `TRIGGER`.
- Sơ đồ phòng (Room Grid)
- Kiểm tra trùng lịch đặt phòng
- CRM (Lịch sử khách hàng)
- Housekeeping (Trạng thái phòng)
- Dịch vụ (Sử dụng dịch vụ)
- Hóa đơn (Chốt tiền phòng và dịch vụ)

### 🟠 PHASE 3 (KIẾN TRÚC DOANH NGHIỆP - ĐÃ HOÀN THÀNH)
- Dockerize hoàn toàn Backend và Database.
- Ứng dụng Swing giao tiếp chuẩn mực với API nội bộ thông qua IP/Domain thay vì Localhost.

---

## 7. Quy tắc nghiệp vụ (QUAN TRỌNG)
- **Chỉ cho đặt phòng nếu:** `room.status = 'available'`
- **Khi check-in:**
  - `room.status` → `occupied`
  - `booking.status` → `checked_in`
- **Khi check-out:**
  - `room.status` → `available`
  - `booking.status` → `checked_out`
- **Khi thanh toán:** Tạo bản ghi trong `payments`
- **Lưu ý dữ liệu:** Không được xóa dữ liệu nếu vi phạm khóa ngoại.

---

## 8. API chuẩn
- **AUTH**: `POST /api/auth/login`
- **ROOM**: `GET /api/rooms`, `PUT /api/rooms/{id}/status`
- **CUSTOMER**: `POST /api/customers`, `GET /api/customers`, `GET /api/customers/{id}/bookings`
- **BOOKING**: `POST /api/bookings`, `PUT /api/bookings/checkin/{id}`, `PUT /api/bookings/checkout/{id}`
- **SERVICE**: `POST /api/service-usage`
- **PAYMENT & INVOICE**: `POST /api/payments`, `GET /api/invoices/{bookingId}`

**Response JSON chuẩn:**
```json
{
  "status": "success",
  "message": "...",
  "data": "..."
}
```

---

## 9. Quy tắc code & Làm việc nhóm (BẮT BUỘC TUÂN THỦ)
- **Tên Package:** Bắt buộc toàn bộ chữ thường (lowercase). VD: `controller`, `service`, `dao`, `daoimpl`, `model`, `utils`, `view`, `api`.
- **Tên Class / Interface:** Bắt buộc PascalCase. VD: `Room`, `RoomService`, `UserDAO`. Không đặt kiểu `roomModel`.
- **Tên Hàm / Biến:** Bắt buộc camelCase. VD: `getRoomById()`, `roomList`.
- **Tránh Conflict lặp file (Duplicate Class):**
  - Luôn `git pull` code mới thay vì code mù.
  - Luôn kiểm tra xem Class đó (đặc biệt là Model và DAO) đã tồn tại chưa trước khi bấm New Class. 
- Code theo **OOP**.
- KHÔNG hardcode SQL trong UI.

---

## 10. Yêu cầu khi trả lời (với tôi)
- Luôn tuân thủ kiến trúc MVC + REST API.
- Code đơn giản, dễ hiểu, phù hợp trình độ sinh viên.
- Không làm quá phức tạp.
- **Luôn đảm bảo code KHỚP với database đã cho.** (Tương thích 100% với cấu trúc bảng, khóa ngoại, status,... của file SQL).

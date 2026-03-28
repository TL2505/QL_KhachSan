# 🎯 ROADMAP PHASE 1 (ĐÃ GẮN NGƯỜI THEO FOLDER)

## 🗓️ NGÀY 1: SETUP + MODEL + DAO

**👨💻 Người 2 – DATABASE + DAO**
_(backend/dao + database/)_
- Tạo DB từ file SQL
- Làm:
  - `RoomDAO.java`
  - `CustomerDAO.java`
  - `BookingDAO.java`
  - `UserDAO.java`
- Test query MySQL

**👨💻 Người 3 – MODEL**
_(backend/model/)_
- Tạo class:
  - `User.java`
  - `Role.java`
  - `Room.java`
  - `Customer.java`
  - `Booking.java`
  - `Payment.java`
👉 Đảm bảo mapping đúng DB

**👨💻 Người 1 – DB Connection**
_(backend/utils/)_
- Viết:
  - `DBConnection.java`
- Test connect MySQL

---

## 🗓️ NGÀY 2: SERVICE + CONTROLLER (API)

**👨💻 Người 3 – SERVICE**
_(backend/service/)_
- Viết:
  - `AuthService.java`
  - `RoomService.java`
  - `CustomerService.java`
  - `BookingService.java`
  - `PaymentService.java`
👉 Xử lý: login, check phòng trống, check-in / check-out, tính tiền

**👨💻 Người 1 – CONTROLLER (API)**
_(backend/controller/)_
- Viết:
  - `AuthController.java`
  - `RoomController.java`
  - `CustomerController.java`
  - `BookingController.java`
  - `PaymentController.java`
👉 Trả JSON đúng API contract

**👨💻 Người 5 – MAIN BACKEND**
_(backend/Main.java)_
- Setup chạy server (hoặc test controller tạm)
- Kết nối toàn bộ backend

---

## 🗓️ NGÀY 3: FRONTEND (SWING)

**👨💻 Người 4 – UI**
_(frontend/view/)_
- Làm giao diện:
  - `LoginForm.java`
  - `RoomForm.java`
  - `CustomerForm.java`
  - `BookingForm.java`

**👨💻 Người 4 – API CALL**
_(frontend/api/)_
- Viết:
  - `AuthAPI.java`
  - `RoomAPI.java`
  - `CustomerAPI.java`
  - `BookingAPI.java`
👉 Gọi HTTP tới backend

**👨💻 Người 5 – MAIN UI**
_(frontend/MainUI.java + Quanlykhachsan.java)_
- Điều hướng màn hình
- Sau login → vào dashboard

---

## 🗓️ NGÀY 4: INTEGRATION + TEST

**👨💻 Người 5 – INTEGRATION (LEADER)**
_(TOÀN BỘ PROJECT)_
- Test: UI → API → DB
- Fix bug: JSON lỗi, sai field
- Đảm bảo demo chạy mượt

---

## 🧠 PHÂN CÔNG NHANH THEO THƯ MỤC

```text
Quanlykhachsan/ (Thư mục gốc)
├── src/
│   └── quanlykhachsan/
│       ├── backend/
│       │   ├── controller/         → 👨💻 Người 1
│       │   ├── service/            → 👨💻 Người 3
│       │   ├── dao/                → 👨💻 Người 2
│       │   ├── model/              → 👨💻 Người 3
│       │   ├── utils/              → 👨💻 Người 1
│       │   └── Main.java           → 👨💻 Người 5
│       │
│       └── frontend/
│           ├── view/               → 👨💻 Người 4
│           ├── api/                → 👨💻 Người 4
│           ├── MainUI.java         → 👨💻 Người 5
│           └── Quanlykhachsan.java → 👨💻 Người 5
│
├── database/                       → 👨💻 Người 2
└── docs/                           → 👨💻 Người 5
```

---

## ⚠️ QUY ƯỚC TEAM (RẤT QUAN TRỌNG)

* 👨💻 **Người 4 (UI)** KHÔNG được gọi DAO
* 👨💻 **Người 1 (API)** KHÔNG viết SQL
* 👨💻 **Người 2 (DAO)** KHÔNG viết UI
* 👨💻 **Người 3 (Service)** KHÔNG gọi HTTP

---

# 🚀 PHASE 2 – Nâng cao (Advanced Features)

## 🎯 Mục tiêu

Mở rộng hệ thống từ Phase 1 với các chức năng nâng cao:

* Sơ đồ phòng (Room Grid)
* Kiểm tra trùng lịch đặt phòng
* CRM (lịch sử khách hàng)
* Housekeeping (trạng thái phòng)
* Hóa đơn (Invoice)
* Dịch vụ (Service)

---

# 👥 PHÂN CÔNG CÔNG VIỆC

---

## 👨💻 Người 1 – CONTROLLER (API)

### 🎯 Nhiệm vụ:

Xây dựng các REST API cho frontend

### ✅ Checklist:

* [x] GET /rooms (hiển thị room + status)
* [x] POST /bookings (có kiểm tra trùng lịch)
* [ ] GET /customers/{id}/bookings (CRM)
* [x] PUT /rooms/{id}/status (Housekeeping)
* [ ] GET /invoices/{bookingId}
* [ ] POST /service-usage

### ⚠️ Lưu ý:

* Không viết SQL
* Chỉ gọi Service

---

## 👨💻 Người 2 – DAO + DATABASE

### 🎯 Nhiệm vụ:

Xử lý dữ liệu và truy vấn MySQL

### ✅ Checklist:

* [x] Query danh sách phòng + status
* [x] Query kiểm tra trùng lịch booking
* [x] Query booking theo customer_id
* [x] Update room.status
* [ ] Insert service_usage
* [ ] Query invoice + service

### 📌 SQL kiểm tra trùng lịch:

```sql
SELECT * FROM bookings 
WHERE room_id = ? 
AND (newCheckIn < check_out_date AND newCheckOut > check_in_date)
```

---

## 👨💻 Người 3 – SERVICE (BUSINESS LOGIC)

### 🎯 Nhiệm vụ:

Xử lý nghiệp vụ chính

### ✅ Checklist:

* [x] checkAvailable(roomId, checkIn, checkOut)
* [x] Validate booking (không trùng lịch)
* [x] Lấy lịch sử booking theo customer
* [x] Logic housekeeping:

  * checkout → dirty
  * cleaning → available
* [ ] Tính tiền hóa đơn:

  * tiền phòng
  * tiền dịch vụ
* [ ] Tính dịch vụ:

  * total = quantity × price

---

## 👨💻 Người 4 – UI (JAVA SWING)

### 🎯 Nhiệm vụ:

Xây dựng giao diện người dùng

### ✅ Checklist:

#### 🏨 Room Grid

* [x] Hiển thị phòng dạng lưới
* [x] Màu theo trạng thái:

  * 🟢 available
  * 🟡 booked
  * 🔴 occupied
  * ⚪ maintenance

---

#### 📅 Booking

* [x] Form đặt phòng
* [x] Hiển thị lỗi khi trùng lịch

---

#### 👤 CRM

* [ ] Giao diện xem lịch sử khách

---

#### 🧹 Housekeeping

* [x] Nút đổi trạng thái phòng

---

#### 🧾 Invoice

* [ ] Hiển thị hóa đơn

---

#### 🍹 Service

* [ ] Chọn dịch vụ + số lượng

---

## 👨💻 Người 5 – INTEGRATION + TEST (LEADER)

### 🎯 Nhiệm vụ:

Kết nối hệ thống và kiểm thử

### ✅ Checklist:

* [x] Test UI → API → DB
* [ ] Fix lỗi JSON / mapping
* [x] Test booking:

  * trùng → fail
  * đúng → pass
* [ ] Test trạng thái phòng:

  * check-in → occupied
  * checkout → dirty
* [ ] Test invoice:

  * tổng tiền chính xác

---

### 🎬 Demo Flow:

* [ ] Login
* [ ] Xem room grid
* [ ] Đặt phòng
* [ ] Check-in
* [ ] Thêm dịch vụ
* [ ] Check-out
* [ ] Xem hóa đơn

---

# ⚠️ QUY TẮC TEAM

* Không để UI gọi trực tiếp database
* Không viết SQL trong Controller
* Service là nơi xử lý logic
* Mỗi người làm đúng phần của mình

---

# 🔄 THỨ TỰ TRIỂN KHAI

```text
DAO → SERVICE → API → UI → TEST
```

---

# 🏁 KẾT LUẬN

Hoàn thành Phase 2 sẽ giúp hệ thống:

* Trực quan hơn (Room Grid)
* Chính xác hơn (tránh trùng lịch)
* Thực tế hơn (dịch vụ + hóa đơn)
* Gần giống phần mềm quản lý khách sạn thật

---

# 🌟 PHASE 3 – Lựa chọn thêm (Optional – nếu còn thời gian)

### 3.1. Báo cáo (Report)
- Xây dựng tính năng thống kê cơ bản cho quản lý.
- Truy xuất dữ liệu từ view `view_monthly_revenue` để hiển thị báo cáo doanh thu theo tháng.

### 3.2. Phân quyền (Role-based access)
- Dựa trên cấp bậc trong bảng `roles`, giới hạn quyền truy cập của người dùng.
- Ví dụ: Lễ tân chỉ xử lý đặt phòng, Quản lý mới được xem doanh thu và cấu hình phòng/nhân viên.

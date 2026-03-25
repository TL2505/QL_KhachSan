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

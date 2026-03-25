# 📘 API CONTRACT - HOTEL MANAGEMENT SYSTEM

---

## 1. Tổng quan

Tài liệu này định nghĩa các API dùng trong hệ thống quản lý khách sạn.

**Mục tiêu:**
- Thống nhất cách giao tiếp giữa Frontend (Swing) và Backend
- Tránh lỗi khi tích hợp
- Dễ test bằng Postman

---

## 2. Base URL

> `http://localhost:8080/api`

---

## 3. Format Response chung

### ✅ Thành công
```json
{
  "status": "success",
  "message": "Thành công",
  "data": {}
}
```

### ❌ Lỗi
```json
{
  "status": "error",
  "message": "Mô tả lỗi",
  "data": null
}
```

---

## 4. Quy ước dữ liệu

- **🔤 Naming:** 
  - Sử dụng định dạng `camelCase` 
  - *Ví dụ:* `roomNumber`, `checkInDate`
- **📅 Date format:** 
  - Theo chuẩn `YYYY-MM-DD`
- **🔢 ID:** 
  - Kiểu số nguyên (`int`)

---

## 5. AUTH API

### 🔐 Login
- **Method:** `POST`
- **Endpoint:** `/auth/login`
- **Request:**
  ```json
  {
    "username": "admin",
    "password": "123456"
  }
  ```
- **Response:**
  ```json
  {
    "status": "success",
    "message": "Đăng nhập thành công",
    "data": {
      "userId": 1,
      "username": "admin",
      "role": "ADMIN"
    }
  }
  ```

---

## 6. ROOM API

### 📋 Lấy danh sách phòng
- **Method:** `GET`
- **Endpoint:** `/rooms`
- **Response:**
  ```json
  {
    "status": "success",
    "data": [
      {
        "id": 1,
        "roomNumber": "101",
        "type": "VIP",
        "price": 500000,
        "status": "available"
      }
    ]
  }
  ```

---

## 7. CUSTOMER API

### ➕ Thêm khách hàng
- **Method:** `POST`
- **Endpoint:** `/customers`
- **Request:**
  ```json
  {
    "name": "Nguyen Van A",
    "phone": "0123456789",
    "cccd": "123456789"
  }
  ```

### 📋 Lấy danh sách khách hàng
- **Method:** `GET`
- **Endpoint:** `/customers`

---

## 8. BOOKING API

### 📅 Đặt phòng
- **Method:** `POST`
- **Endpoint:** `/bookings`
- **Request:**
  ```json
  {
    "customerId": 1,
    "roomId": 2,
    "checkInDate": "2026-03-25",
    "checkOutDate": "2026-03-27"
  }
  ```
- **Logic Nghiệp vụ:**
  - *Chỉ cho đặt nếu:* `room.status = "available"`
  - *Sau khi đặt phòng:* trạng thái `room.status` sẽ chuyển thành `"booked"`

### 🚪 Check-in
- **Method:** `PUT`
- **Endpoint:** `/bookings/checkin/{id}`
- **Logic Nghiệp vụ:**
  - `booking.status` chuyển thành `"checked_in"`
  - `room.status` chuyển thành `"occupied"`

### 🚪 Check-out
- **Method:** `PUT`
- **Endpoint:** `/bookings/checkout/{id}`
- **Logic Nghiệp vụ:**
  - `booking.status` chuyển thành `"checked_out"`
  - `room.status` chuyển thành `"available"`

---

## 9. PAYMENT API

### 💰 Thanh toán
- **Method:** `POST`
- **Endpoint:** `/payments`
- **Request:**
  ```json
  {
    "bookingId": 1,
    "amount": 1000000,
    "paymentMethod": "cash"
  }
  ```
- **paymentMethod hợp lệ:**
  - `cash` (Tiền mặt)
  - `credit_card` (Thẻ tín dụng)
  - `bank_transfer` (Chuyển khoản)
  - `e_wallet` (Ví điện tử)

---

## 10. ENUM QUY ƯỚC

### 🏨 Room Status
- `available`
- `booked`
- `occupied`
- `maintenance`

### 📅 Booking Status
- `pending`
- `confirmed`
- `checked_in`
- `checked_out`
- `cancelled`

---

## 11. QUY TẮC QUAN TRỌNG

- **[QUAN TRỌNG]** Frontend chỉ gọi API → KHÔNG gọi DB trực tiếp.
- Backend phải trả đúng format JSON.
- Không tự ý đổi tên field.
- Phải test API bằng Postman trước khi kết nối với giao diện UI.

---

## 12. FLOW DEMO (PHASE 1)

1. Mở Login
2. Xem danh sách phòng
3. Thêm khách hàng
4. Đặt phòng
5. Check-in
6. Check-out
7. Thanh toán

---

## 13. GHI CHÚ
- **Phase 1** chưa sử dụng:
  - `services`
  - `invoices`
- Có thể mở rộng chức năng này ở **Phase 2**.

---

## 14. KẾT LUẬN
Tất cả thành viên trong team **bắt buộc tuân thủ tài liệu này** để đảm bảo:
- Hệ thống hoạt động đồng nhất.
- Tránh xảy ra lỗi khi tích hợp.
- Dễ dàng debug và demo ứng dụng.

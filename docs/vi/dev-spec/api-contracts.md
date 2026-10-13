# BẢN ĐẶC TẢ HỢP ĐỒNG API & HÀNG RÀO BẢO VỆ (GIAI ĐOẠN 1, 2 & 3)
**Dự án:** Hệ thống Quản trị Khách sạn 5★ & Nền tảng AI (`QL_KhachSan` — 200 Phòng)  
**Định dạng Chuẩn:** Chi tiết Lỗi RFC-7807 Problem Details cho HTTP APIs & Chuẩn Envelope JSON `{status, data, meta}`.  
**Các Tiêu đề Bắt buộc (Headers):** `Idempotency-Key` (UUIDv4 cho các tác vụ thay đổi dữ liệu), `Authorization` (`Bearer <jwt>`), `X-Signature` (HMAC-SHA256 cho webhook ngân hàng).

---

## 1. TIÊU CHUẨN GIAO THỨC & CẤU TRÚC ENVELOPE TOÀN CỤC

### 1.1 Khung Phản hồi Thành công (`200 OK`, `201 Created`, `204 No Content`)
```json
{
  "status": "success",
  "data": {
    /* Đối tượng hoặc mảng dữ liệu nghiệp vụ */
  },
  "meta": {
    "timestamp": "2026-10-14T03:30:00Z",
    "request_id": "req-9876543210",
    "page": 1,
    "size": 20,
    "total": 150
  }
}
```

### 1.2 Định dạng Lỗi Chuẩn Hóa RFC-7807
Mọi phản hồi lỗi phía máy khách hoặc máy chủ (`400`, `401`, `403`, `404`, `409`, `429`, `500`, `503`, `504`) đều tuân thủ chặt chẽ chuẩn RFC-7807:
```json
{
  "type": "https://hotel.example.com/errors/ERR_409_OPTIMISTIC_LOCK",
  "title": "Optimistic Locking Conflict",
  "status": 409,
  "detail": "Phòng 205 đã bị thay đổi bởi một giao dịch đồng thời khác. Phiên bản kỳ vọng: 0, phiên bản hiện tại: 1.",
  "instance": "/api/v1/bookings/public",
  "error_code": "ERR_409_OPTIMISTIC_LOCK",
  "timestamp": "2026-10-14T03:30:00Z"
}
```

### 1.3 Ma trận Tiêu đề HTTP Bắt buộc (Mandatory Headers Matrix)
| Header | Kiểu dữ liệu | Các Endpoint Yêu cầu | Hành vi & Hàng rào Kiểm soát |
|---|---|---|---|
| `Idempotency-Key` | Chuỗi UUIDv4 | `POST /api/v1/bookings*`, `POST /api/v1/folios/{id}/pay`, `POST /api/v1/rooms/{id}/minibar` | Động cơ khử trùng lặp khóa bản ghi; các yêu cầu gửi lại cùng khóa trả về ngay kết quả cũ mà không thực thi lặp giao dịch sổ cái. |
| `Authorization` | `Bearer <JWT>` | Các endpoint bảo vệ (Nhân viên & Khách có token) | Xác thực chữ ký điện tử HMAC-SHA256; kiểm tra quyền hạn RBAC (`front_desk`, `housekeeping`, `f&b`, `admin`, `guest`). |
| `X-Signature` | Chuỗi Hex | `POST /api/v1/webhooks/vietqr` | `hex(HMAC_SHA256(bank_secret, raw_body))`; chữ ký không hợp lệ bị từ chối lập tức với mã HTTP 401. |
| `Origin` | Tên miền | Toàn bộ request Web CORS | Bộ lọc `CorsFilter` kiểm tra tên miền và phản hồi tiền kiểm `OPTIONS` với `204 No Content`. |

---

## 2. ĐẶC TẢ HỢP ĐỒNG API GIAI ĐOẠN 2

### 2.1 Xác thực Khách hàng qua Mã OTP & Cấp Token
* **Endpoint:** `POST /api/v1/auth/guest-otp/request`
  * **Quyền hạn:** Công khai (Nằm trong danh sách trắng `PUBLIC_PATHS`)
  * **Body Yêu cầu:**
    ```json
    {
      "phone": "0901234567",
      "email": "guest@example.com"
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "message": "Mã xác thực OTP đã được gửi thành công.",
        "expires_in_seconds": 300
      },
      "meta": { "timestamp": "2026-10-14T03:30:00Z" }
    }
    ```

* **Endpoint:** `POST /api/v1/auth/guest-otp/verify`
  * **Quyền hạn:** Công khai
  * **Body Yêu cầu:**
    ```json
    {
      "phone": "0901234567",
      "otp_code": "849201"
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "access_token": "eyJhbGciOiJIUzI1NiIsIn...",
        "token_type": "Bearer",
        "expires_in_seconds": 86400,
        "guest_profile": {
          "customer_id": 412,
          "phone": "0901234567",
          "loyalty_level": "Member",
          "total_points": 0
        }
      },
      "meta": { "timestamp": "2026-10-14T03:30:00Z" }
    }
    ```

### 2.2 Đặt phòng Công cộng & Web Booking Engine
* **Endpoint:** `POST /api/v1/bookings/public`
  * **Quyền hạn:** Công khai hoặc Token Khách Bearer
  * **Headers:** `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` (Bắt buộc)
  * **Body Yêu cầu:**
    ```json
    {
      "customer_name": "Nguyễn Văn An",
      "phone": "0901234567",
      "email": "an.nguyen@example.com",
      "room_id": 105,
      "room_version": 2,
      "check_in_date": "2026-11-01 14:00:00",
      "check_out_date": "2026-11-04 12:00:00",
      "voucher_code": "SUMMER10"
    }
    ```
  * **Phản hồi Thành công (`201 Created`):**
    ```json
    {
      "status": "success",
      "data": {
        "booking_id": 1089,
        "booking_code": "BKG-WEB-2026-1089",
        "room_number": "105",
        "status": "confirmed",
        "room_version": 3,
        "folio": {
          "id": 2045,
          "code": "FOLIO-MASTER-105",
          "balance": 4500000.00,
          "status": "open"
        }
      },
      "meta": { "timestamp": "2026-10-14T03:30:00Z" }
    }
    ```
  * **Phản hồi Xung đột (`409 Conflict`):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_409_ROOM_UNAVAILABLE",
      "title": "Room Conflict",
      "status": 409,
      "detail": "Phòng 105 vừa được đặt bởi một khách hàng khác.",
      "error_code": "ERR_409_CONFLICT",
      "timestamp": "2026-10-14T03:30:00Z"
    }
    ```

### 2.3 Tạo Mã Thanh toán VietQR & Webhook Đối soát Ngân hàng
* **Endpoint:** `GET /api/v1/folios/{id}/vietqr`
  * **Quyền hạn:** Bearer Token (Nhân viên hoặc Khách hàng)
  * **Tham số Đường dẫn:** `id` (Folio ID hoặc Mã Folio Code)
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "folio_id": 2045,
        "folio_code": "FOLIO-MASTER-105",
        "amount": 4500000.00,
        "currency": "VND",
        "beneficiary_bank": "MBBank",
        "account_number": "0901234567899",
        "account_holder": "5 STAR LUXURY HOTEL SAIGON",
        "emvco_payload": "00020101021238540010A00000072701240006970422011009012345678995303704540745000005802VN62240820FOLIO-MASTER-1056304E8A2",
        "qr_image_base64": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAASwAAAEs...",
        "expires_in_seconds": 300
      },
      "meta": { "timestamp": "2026-10-14T03:30:00Z" }
    }
    ```

* **Endpoint:** `POST /api/v1/webhooks/vietqr`
  * **Quyền hạn:** Xác thực Chữ ký `X-Signature`
  * **Headers:** `X-Signature: c894d35e729a4a159f81a7b5...`, `Content-Type: application/json`
  * **Body Yêu cầu:**
    ```json
    {
      "bank_trans_id": "MB-TXN-20261014-998811",
      "reference_code": "FOLIO-MASTER-105",
      "amount": 5000000.00,
      "currency": "VND",
      "payment_time": "2026-10-14 03:35:12",
      "gateway": "NAPAS247"
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "bank_trans_id": "MB-TXN-20261014-998811",
        "folio_id": 2045,
        "settled_amount": 4500000.00,
        "credit_adjustment": 500000.00,
        "new_balance": 0.00,
        "status": "settled"
      },
      "meta": { "timestamp": "2026-10-14T03:35:13Z" }
    }
    ```

### 2.4 Quản lý Buồng phòng & Tự động hóa Minibar Thời gian thực
* **Endpoint:** `PUT /api/v1/rooms/{id}/housekeeping`
  * **Quyền hạn:** Bearer Token (Chỉ cho phép role `housekeeping`, `admin`)
  * **Tham số Đường dẫn:** `id` (ID Phòng)
  * **Body Yêu cầu:**
    ```json
    {
      "status": "vacant_clean",
      "cleaner_notes": "Đã khử khuẩn sâu, thay toàn bộ ga trải giường."
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "room_id": 105,
        "room_number": "105",
        "previous_status": "vacant_dirty",
        "current_status": "vacant_clean",
        "version": 4,
        "cleaned_by": "maid_04",
        "cleaned_at": "2026-10-14 03:40:00"
      },
      "meta": { "timestamp": "2026-10-14T03:40:00Z" }
    }
    ```
  * **Phản hồi Bị chặn (`403 Forbidden` đối với Lễ tân):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_403_FORBIDDEN",
      "title": "Access Denied",
      "status": 403,
      "detail": "Tài khoản quyền 'front_desk' không được phép chuyển trạng thái phòng từ bẩn sang sạch.",
      "error_code": "ERR_403_FORBIDDEN",
      "timestamp": "2026-10-14T03:40:00Z"
    }
    ```

* **Endpoint:** `POST /api/v1/rooms/{id}/minibar`
  * **Quyền hạn:** Bearer Token (`housekeeping`, `front_desk`, `admin`)
  * **Headers:** `Idempotency-Key: 7b3dcb6d-4bad-9bdd-3b7d-9b1deb4d2b0d` (Bắt buộc)
  * **Body Yêu cầu:**
    ```json
    {
      "items": [
        { "item_id": 1, "item_name": "Bia Heineken lon", "quantity": 2, "unit_price": 50000.00 },
        { "item_id": 4, "item_name": "Hạt Mắc ca", "quantity": 1, "unit_price": 80000.00 }
      ]
    }
    ```
  * **Phản hồi (`201 Created`):**
    ```json
    {
      "status": "success",
      "data": {
        "room_id": 105,
        "target_folio_id": 2046,
        "target_folio_code": "FOLIO-GUEST-105",
        "total_posted": 180000.00,
        "transaction_id": 8412,
        "new_incidental_balance": 180000.00
      },
      "meta": { "timestamp": "2026-10-14T03:45:00Z" }
    }
    ```

### 2.5 Giá Linh hoạt, Khuyến mãi & Hóa đơn Thông tư 78
* **Endpoint:** `GET /api/v1/pricing/quote`
  * **Quyền hạn:** Công khai (`PUBLIC_PATHS` Whitelist)
  * **Tham số Truy vấn:** `room_type_id=1&check_in=2026-11-01&check_out=2026-11-03`
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "room_type_id": 1,
        "room_type_name": "Deluxe King",
        "base_rate": 2000000.00,
        "occupancy_rate": 0.74,
        "occupancy_tier": "70% - 85%",
        "surge_multiplier": 1.30,
        "nightly_rate": 2600000.00,
        "number_of_nights": 2,
        "subtotal": 5200000.00,
        "service_charge_5pct": 260000.00,
        "vat_8pct": 436800.00,
        "total_quote": 5896800.00
      },
      "meta": { "timestamp": "2026-10-14T03:50:00Z" }
    }
    ```

* **Endpoint:** `GET /api/v1/invoices/{id}/pdf`
  * **Quyền hạn:** Bearer Token
  * **Headers:** `Accept: application/pdf`
  * **Phản hồi (`200 OK`):**
    * Headers: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="HD-2026-2045.pdf"`
    * Nội dung Body: Luồng byte nhị phân PDF chuẩn hóa theo Thông tư 78/2021/TT-BTC (nhúng font Unicode DejaVu Sans, bóc tách thuế GTGT, mã QR tra cứu cơ quan thuế).

* **Endpoint:** `GET /api/v1/reports/financial/dbr`
  * **Quyền hạn:** Bearer Token (`admin`, `front_desk`)
  * **Tham số Truy vấn:** `date=2026-10-13`
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "business_date": "2026-10-13",
        "total_rooms": 200,
        "occupied_rooms": 164,
        "out_of_order_rooms": 4,
        "occupancy_percentage": 83.67,
        "room_revenue": 393600000.00,
        "fnb_minibar_revenue": 45800000.00,
        "other_service_revenue": 12400000.00,
        "total_gross_revenue": 451800000.00,
        "service_charge_total": 22590000.00,
        "vat_tax_total": 37951200.00,
        "adr": 2400000.00,
        "revpar": 2008163.27,
        "ledger_reconciliation": {
          "total_debits": 512341200.00,
          "total_credits": 512341200.00,
          "variance": 0.00
        }
      },
      "meta": { "timestamp": "2026-10-14T03:55:00Z" }
    }
    ```

---

## 3. ĐẶC TẢ HỢP ĐỒNG API GIAI ĐOẠN 3

### 3.1 Cổng AI Thị giác Máy tính & Check-in Sinh trắc học
* **Endpoint:** `POST /api/v1/ai/id-ocr`
  * **Quyền hạn:** Bearer Token (`front_desk`, `admin`)
  * **Body Yêu cầu:**
    ```json
    {
      "image_base64": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a...",
      "document_type": "VIETNAMESE_CCCD"
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "id_number": "079095012345",
        "full_name": "TRẦN VĂN HẢI",
        "dob": "1995-08-15",
        "gender": "Nam",
        "nationality": "Việt Nam",
        "origin_address": "Xã An Bình, Huyện Phú Giáo, Bình Dương",
        "residence_address": "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
        "expiry_date": "2035-08-15",
        "confidence_score": 0.98
      },
      "meta": { "timestamp": "2026-10-14T04:00:00Z" }
    }
    ```
  * **Phản hồi Chuyển mạch Dự phòng (`504 Gateway Timeout` khi AI ngắt kết nối):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_504_AI_TIMEOUT",
      "title": "AI Service Timeout",
      "status": 504,
      "detail": "Dịch vụ AI không phản hồi sau 3000ms. Nhân viên chuyển sang chế độ đối chiếu thủ công.",
      "error_code": "ERR_504_GATEWAY_TIMEOUT",
      "timestamp": "2026-10-14T04:00:03Z"
    }
    ```

* **Endpoint:** `POST /api/v1/ai/face-verify`
  * **Quyền hạn:** Bearer Token (`front_desk`, `admin`)
  * **Body Yêu cầu:**
    ```json
    {
      "id_photo_base64": "/9j/4AAQSkZJRgABAQEAYABgAAD...",
      "live_face_base64": "/9j/4AAQSkZJRgABAQEASABIAAD..."
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "is_match": true,
        "similarity_score": 0.842,
        "adaptive_threshold": 0.680,
        "decision": "ACCEPT",
        "message": "Sinh trắc học khuôn mặt trùng khớp với ảnh trên CCCD."
      },
      "meta": { "timestamp": "2026-10-14T04:05:00Z" }
    }
    ```

### 3.2 Trợ lý Ảo Concierge & Cổng Tra cứu RAG Quy chế
* **Endpoint:** `POST /api/v1/ai/concierge/chat`
  * **Quyền hạn:** Scoped Guest Token hoặc Bearer Token
  * **Giới hạn Tần suất:** 10 requests / phút / session
  * **Body Yêu cầu:**
    ```json
    {
      "session_id": "sess-guest-room-105-abc1234",
      "message": "Cho tôi hỏi giờ mở cửa của hồ bơi vô cực và phòng tập gym?"
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "reply": "Dạ, hồ bơi vô cực tầng thượng phục vụ từ 06:00 đến 22:00 hàng ngày. Phòng tập gym tại tầng 3 mở cửa phục vụ Quý khách 24/7 bằng thẻ phòng ạ.",
        "citations": [
          { "doc": "Hotel_SOP_Amenities.md", "section": "Bể bơi vô cực & Fitness" }
        ],
        "suggested_actions": ["Đặt khăn tắm", "Xem dịch vụ Spa"],
        "confidence_score": 0.96
      },
      "meta": { "timestamp": "2026-10-14T04:10:00Z" }
    }
    ```
  * **Chặn Đòn Tấn công Prompt Injection (`400 Bad Request`):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_400_PROMPT_INJECTION",
      "title": "Security Policy Violation",
      "status": 400,
      "detail": "Yêu cầu không hợp lệ do chứa chỉ lệnh can thiệp hệ thống hoặc vi phạm bảo mật dữ liệu.",
      "error_code": "PROMPT_INJECTION_DETECTED",
      "timestamp": "2026-10-14T04:10:00Z"
    }
    ```

* **Endpoint:** `POST /api/v1/ai/concierge/escalate`
  * **Quyền hạn:** Scoped Guest Token
  * **Body Yêu cầu:**
    ```json
    {
      "session_id": "sess-guest-room-105-abc1234",
      "reason": "Khách cần hỗ trợ đổi phòng do máy lạnh phòng 105 kêu ồn."
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "ticket_id": 501,
        "room_number": "105",
        "status": "OPEN",
        "eta_minutes": 3,
        "message": "Yêu cầu đã được chuyển tới Lễ tân ca trực. Nhân viên sẽ liên hệ với Quý khách ngay lập tức."
      },
      "meta": { "timestamp": "2026-10-14T04:12:00Z" }
    }
    ```

### 3.3 Tối ưu hóa Doanh thu & Dự báo Học máy (ML Yield)
* **Endpoint:** `GET /api/v1/revenue/forecast`
  * **Quyền hạn:** Bearer Token (`admin`, `revenue_manager`)
  * **Tham số Truy vấn:** `days=30`
  * **Phản hồi (`200 OK` - Lưu đệm Cache 6 giờ):**
    ```json
    {
      "status": "success",
      "data": {
        "model": "Prophet-Additive-VietnamHolidays",
        "generated_at": "2026-10-14T00:00:00Z",
        "forecast": [
          {
            "date": "2026-10-15",
            "predicted_occupancy": 78.4,
            "confidence_lower": 74.2,
            "confidence_upper": 82.6,
            "recommended_multiplier": 1.30,
            "recommended_bar": 2600000.00
          },
          {
            "date": "2026-10-16",
            "predicted_occupancy": 89.2,
            "confidence_lower": 85.0,
            "confidence_upper": 93.4,
            "recommended_multiplier": 1.50,
            "recommended_bar": 3000000.00
          }
        ]
      },
      "meta": { "timestamp": "2026-10-14T04:15:00Z" }
    }
    ```

* **Endpoint:** `POST /api/v1/revenue/apply-rates`
  * **Quyền hạn:** Bearer Token (Chỉ cho phép `admin`)
  * **Body Yêu cầu:**
    ```json
    {
      "rates": [
        { "date": "2026-10-15", "room_type_id": 1, "approved_multiplier": 1.30 },
        { "date": "2026-10-16", "room_type_id": 1, "approved_multiplier": 1.50 }
      ],
      "approved_by_user_id": 1
    }
    ```
  * **Phản hồi (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "updated_count": 2,
        "message": "Các hệ số giá động đã được phê duyệt và áp dụng đồng bộ trên toàn hệ thống."
      },
      "meta": { "timestamp": "2026-10-14T04:20:00Z" }
    }
    ```

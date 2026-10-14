# API CONTRACTS & GUARDRAILS (PHASE 1, 2 & 3 SPECIFICATIONS)
**Project:** 5★ Luxury Hotel PMS & AI Platform (`QL_KhachSan` — 200 Rooms)  
**Standard Format:** RFC-7807 Problem Details for HTTP APIs & Standard JSON Envelope `{status, data, meta}`.  
**Mandatory Headers:** `Idempotency-Key` (UUIDv4 for mutations), `Authorization` (`Bearer <jwt>`), `X-Signature` (HMAC-SHA256 for bank webhooks).

---

## 1. GLOBAL PROTOCOL & ENVELOPE STANDARDS

### 1.1 Success Response Envelope (`200 OK`, `201 Created`, `204 No Content`)
```json
{
  "status": "success",
  "data": {
    /* Payload object or array */
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

### 1.2 RFC-7807 Standard Error Format
All client/server errors (`400`, `401`, `403`, `404`, `409`, `429`, `500`, `503`, `504`) return standard RFC-7807 compliant problem details:
```json
{
  "type": "https://hotel.example.com/errors/ERR_409_OPTIMISTIC_LOCK",
  "title": "Optimistic Locking Conflict",
  "status": 409,
  "detail": "Room 205 was modified by another concurrent transaction. Expected version 0, current version 1.",
  "instance": "/api/v1/bookings/public",
  "error_code": "ERR_409_OPTIMISTIC_LOCK",
  "timestamp": "2026-10-14T03:30:00Z"
}
```

### 1.3 Mandatory HTTP Headers Matrix
| Header | Type | Required Endpoints | Behavior & Guardrails |
|---|---|---|---|
| `Idempotency-Key` | UUIDv4 (String) | `POST /api/v1/bookings*`, `POST /api/v1/folios/{id}/pay`, `POST /api/v1/rooms/{id}/minibar` | Deduplication engine locks key; duplicate submissions return original response without re-executing ledger mutations. |
| `Authorization` | `Bearer <JWT>` | Protected endpoints (Staff & Scoped Guest) | Validates signature (HMAC-SHA256); verifies RBAC roles (`front_desk`, `housekeeping`, `f&b`, `admin`, `guest`). |
| `X-Signature` | Hex String | `POST /api/v1/webhooks/vietqr` | `hex(HMAC_SHA256(bank_secret, raw_body))`; invalid signature rejected immediately with HTTP 401. |
| `Origin` | Domain String | All Web CORS requests | Native `CorsFilter` verifies domain and responds to preflight `OPTIONS` with `204 No Content`. |

---

## 2. PHASE 2 API CONTRACT SPECIFICATIONS

### 2.1 Public Guest OTP & Token Authentication
* **Endpoint:** `POST /api/v1/auth/guest-otp/request`
  * **Auth:** Public (`PUBLIC_PATHS` Whitelist)
  * **Request Body:**
    ```json
    {
      "phone": "0901234567",
      "email": "guest@example.com"
    }
    ```
  * **Response (`200 OK`):**
    ```json
    {
      "status": "success",
      "data": {
        "message": "OTP verification code sent successfully.",
        "expires_in_seconds": 300
      },
      "meta": { "timestamp": "2026-10-14T03:30:00Z" }
    }
    ```

* **Endpoint:** `POST /api/v1/auth/guest-otp/verify`
  * **Auth:** Public
  * **Request Body:**
    ```json
    {
      "phone": "0901234567",
      "otp_code": "849201"
    }
    ```
  * **Response (`200 OK`):**
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

### 2.2 Public Room Availability & Web Booking Engine
* **Endpoint:** `POST /api/v1/bookings/public`
  * **Auth:** Public or Guest Bearer Token
  * **Headers:** `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` (Required)
  * **Request Body:**
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
  * **Response (`201 Created`):**
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
  * **Error Response (`409 Conflict`):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_409_ROOM_UNAVAILABLE",
      "title": "Room Conflict",
      "status": 409,
      "detail": "Room 105 has been reserved by another guest.",
      "error_code": "ERR_409_CONFLICT",
      "timestamp": "2026-10-14T03:30:00Z"
    }
    ```

### 2.3 VietQR Payment Generation & Bank Webhook Reconciliation
* **Endpoint:** `GET /api/v1/folios/{id}/vietqr`
  * **Auth:** Bearer Token (Staff or Guest)
  * **Path Variable:** `id` (Folio BIGINT PK or Folio Code)
  * **Response (`200 OK`):**
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
  * **Auth:** `X-Signature` Verification
  * **Headers:** `X-Signature: c894d35e729a4a159f81a7b5...`, `Content-Type: application/json`
  * **Request Body:**
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
  * **Response (`200 OK`):**
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

### 2.4 Real-time Housekeeping & Minibar Management
* **Endpoint:** `PUT /api/v1/rooms/{id}/housekeeping`
  * **Auth:** Bearer Token (`housekeeping`, `admin` only)
  * **Path Variable:** `id` (Room ID)
  * **Request Body:**
    ```json
    {
      "status": "vacant_clean",
      "cleaner_notes": "Deep sanitization completed, linen replaced."
    }
    ```
  * **Response (`200 OK`):**
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
  * **Error Response (`403 Forbidden` for Front Desk role):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_403_FORBIDDEN",
      "title": "Access Denied",
      "status": 403,
      "detail": "Role 'front_desk' is not authorized to transition room status from dirty to clean.",
      "error_code": "ERR_403_FORBIDDEN",
      "timestamp": "2026-10-14T03:40:00Z"
    }
    ```

* **Endpoint:** `POST /api/v1/rooms/{id}/minibar`
  * **Auth:** Bearer Token (`housekeeping`, `front_desk`, `admin`)
  * **Headers:** `Idempotency-Key: 7b3dcb6d-4bad-9bdd-3b7d-9b1deb4d2b0d` (Required)
  * **Request Body:**
    ```json
    {
      "items": [
        { "item_id": 1, "item_name": "Heineken Can", "quantity": 2, "unit_price": 50000.00 },
        { "item_id": 4, "item_name": "Macadamia Nuts", "quantity": 1, "unit_price": 80000.00 }
      ]
    }
    ```
  * **Response (`201 Created`):**
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

### 2.5 Dynamic Pricing, Promotions & Circular 78 Invoicing
* **Endpoint:** `GET /api/v1/pricing/quote`
  * **Auth:** Public (`PUBLIC_PATHS` Whitelist)
  * **Query Parameters:** `room_type_id=1&check_in=2026-11-01&check_out=2026-11-03`
  * **Response (`200 OK`):**
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
  * **Auth:** Bearer Token
  * **Headers:** `Accept: application/pdf`
  * **Response (`200 OK`):**
    * Headers: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="HD-2026-2045.pdf"`
    * Body: Binary PDF stream compliant with Circular 78/2021/TT-BTC (embedded DejaVu Sans Unicode font, tax details, VAT breakdown, tax QR code).

* **Endpoint:** `GET /api/v1/reports/financial/dbr`
  * **Auth:** Bearer Token (`admin`, `front_desk`)
  * **Query Parameters:** `date=2026-10-13`
  * **Response (`200 OK`):**
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

## 3. PHASE 3 API CONTRACT SPECIFICATIONS

### 3.1 AI Vision & Biometric Check-in Gateway
* **Endpoint:** `POST /api/v1/ai/id-ocr`
  * **Auth:** Bearer Token (`front_desk`, `admin`)
  * **Request Body:**
    ```json
    {
      "image_base64": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a...",
      "document_type": "VIETNAMESE_CCCD"
    }
    ```
  * **Response (`200 OK`):**
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
  * **Fallback Response (`504 Gateway Timeout` when AI Service offline):**
    ```json
    {
      "type": "https://hotel.example.com/errors/ERR_504_AI_TIMEOUT",
      "title": "AI Service Timeout",
      "status": 504,
      "detail": "AI service unavailable or timed out after 3000ms. Switch to manual Front Desk verification.",
      "error_code": "ERR_504_GATEWAY_TIMEOUT",
      "timestamp": "2026-10-14T04:00:03Z"
    }
    ```

* **Endpoint:** `POST /api/v1/ai/face-verify`
  * **Auth:** Bearer Token (`front_desk`, `admin`)
  * **Request Body:**
    ```json
    {
      "id_photo_base64": "/9j/4AAQSkZJRgABAQEAYABgAAD...",
      "live_face_base64": "/9j/4AAQSkZJRgABAQEASABIAAD..."
    }
    ```
  * **Response (`200 OK`):**
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

### 3.2 Virtual Concierge & RAG Policy Gateway
* **Endpoint:** `POST /api/v1/ai/concierge/chat`
  * **Auth:** Scoped Guest Token or Bearer Token
  * **Rate Limit:** 10 requests / minute / session
  * **Request Body:**
    ```json
    {
      "session_id": "sess-guest-room-105-abc1234",
      "message": "Cho tôi hỏi giờ mở cửa của hồ bơi vô cực và phòng tập gym?"
    }
    ```
  * **Response (`200 OK`):**
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
  * **Prompt Injection Defense (`400 Bad Request`):**
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
  * **Auth:** Scoped Guest Token
  * **Request Body:**
    ```json
    {
      "session_id": "sess-guest-room-105-abc1234",
      "reason": "Khách cần hỗ trợ đổi phòng do máy lạnh phòng 105 kêu ồn."
    }
    ```
  * **Response (`200 OK`):**
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

### 3.3 ML Yield Optimization & Forecasting
* **Endpoint:** `GET /api/v1/revenue/forecast`
  * **Auth:** Bearer Token (`admin`, `revenue_manager`)
  * **Query Parameters:** `days=30`
  * **Response (`200 OK` - Cached 6h):**
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
  * **Auth:** Bearer Token (`admin` only)
  * **Request Body:**
    ```json
    {
      "rates": [
        { "date": "2026-10-15", "room_type_id": 1, "approved_multiplier": 1.30 },
        { "date": "2026-10-16", "room_type_id": 1, "approved_multiplier": 1.50 }
      ],
      "approved_by_user_id": 1
    }
    ```
  * **Response (`200 OK`):**
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

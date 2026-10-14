# API CONTRACTS & INTEGRATION GUIDE (PHASE 1, 2 & 3 REST API)

> **Contract Freeze Status:** FROZEN (OpenAPI 3.0.3 Contract Parity Verified)  
> **Target Document:** [`openapi-p1.json`](file:///c:/Users/Admin/OneDrive/Documents/NetBeansProjects/QL_KhachSan_main/docs/dev-spec/openapi-p1.json)  
> **Server Base URL:** `http://localhost:8081`  
> **Envelope Standard:** JSON Envelope `{ "status": "SUCCESS", "data": { ... }, "meta": { "timestamp": ... } }`  
> **Error Standard:** RFC-7807 Standardized Error `{ "error_code": "ERR_...", "message": "...", "timestamp": "..." }`

---

## 1. PHASE 1 CORE ENDPOINTS & ROLE-BASED ACCESS CONTROL (RBAC)

The table below defines the 10 core endpoints of Phase 1 and their required role permissions:

| # | HTTP Method | Endpoint Path | Summary | Role Permissions | Auth Required |
|---|-------------|---------------|---------|------------------|---------------|
| 1 | `POST` | `/api/v1/auth/login` | User authentication & JWT token issuance | `Public` (All) | No |
| 2 | `GET` | `/api/v1/health` | Deep health check (DB `SELECT 1`, Hikari, JVM) | `Public` (Monitoring) | No |
| 3 | `GET` | `/api/v1/rooms` | Catalog of 200 rooms with versions & pricing | `Admin`, `FrontDesk`, `Housekeeping` | Yes (Bearer JWT) |
| 4 | `GET` | `/api/v1/rooms/availability` | Query available rooms by date range | `Admin`, `FrontDesk`, `Public` | Yes (Bearer JWT) |
| 5 | `POST` | `/api/v1/customers` | Register customer profile (dedup ID/phone) | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| 6 | `GET` | `/api/v1/customers` | Fast indexed keyword lookup (<20ms) | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| 7 | `GET` | `/api/v1/bookings` | List recent hotel bookings | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| 8 | `GET` | `/api/v1/bookings/{id}` | Booking details with Master Folio balance | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| 9 | `POST` | `/api/v1/bookings` | Atomic booking creation with CAS room version | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| 10 | `POST` | `/api/v1/bookings/{id}/cancel` | Cancel booking & release room to `vacant_clean` | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| *11* | `PUT` | `/api/v1/bookings/{id}` | Modify stay details / scheduled checkout | `Admin`, `FrontDesk` | Yes (Bearer JWT) |
| *12* | `POST` | `/api/v1/pos/sync` | Store-and-forward batch sync post-freeze | `Admin`, `PosTerminal` | Yes (Bearer JWT) |

---

## 2. STANDARDIZED ERROR CODES & HTTP STATUSES

All error responses strictly adhere to the RFC-7807 error format:

```json
{
  "error_code": "ERR_409_CONFLICT",
  "message": "Room version conflict detected during booking. Please refresh room status.",
  "timestamp": "2026-10-14 20:00:00"
}
```

### Error Code Reference Table

| HTTP Status | Error Code | Description / Trigger Scenario |
|---|---|---|
| `400 Bad Request` | `ERR_400_BAD_REQUEST` | Malformed JSON body, missing mandatory fields, invalid dates (`check_out <= check_in`). |
| `401 Unauthorized` | `ERR_401_UNAUTHORIZED` | Missing `Authorization` header, invalid credentials at `/auth/login`. |
| `401 Unauthorized` | `ERR_401_TOKEN_INVALID` | Expired, malformed, or signature-tampered JWT Bearer token. |
| `403 Forbidden` | `ERR_403_FORBIDDEN` | Authenticated user lacks required role for the requested operation. |
| `404 Not Found` | `ERR_404_NOT_FOUND` | Target resource does not exist (e.g. Booking ID or Customer ID not found). |
| `405 Method Not Allowed` | `ERR_405_METHOD_NOT_ALLOWED` | HTTP verb not allowed for this route (e.g. `POST` to `/rooms`). |
| `409 Conflict` | `ERR_409_CONFLICT` | CAS version mismatch, duplicate customer ID card/phone, room already occupied. |
| `500 Internal Error` | `ERR_500_INTERNAL_SERVER_ERROR` | Unhandled database or system exception caught by `GlobalExceptionHandler`. |
| `503 Unavailable` | `ERR_503_SYSTEM_FROZEN` | Write operation blocked during the 00:00–00:05 Night Audit Freeze window. |

---

## 3. MANDATORY OWASP DEFENSE HEADERS

Every HTTP response from the backend contains 6 standard OWASP security defense headers injected by `SecurityHardeningFilter`:

```http
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://cdn.jsdelivr.net https://unpkg.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:;
Strict-Transport-Security: max-age=31536000; includeSubDomains
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), camera=(), microphone=()
```

---

## 4. DETAILED ENDPOINT SPECIFICATIONS & CURL EXAMPLES

### 1. User Authentication (Login)
- **Method:** `POST`
- **Route:** `/api/v1/auth/login`
- **Auth:** None (Public)

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Password123!"
  }'
```

**Success Response (200 OK):**
```json
{
  "status": "SUCCESS",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "role": "ADMIN",
    "user_id": 1,
    "username": "admin",
    "full_name": "System Administrator"
  },
  "meta": {
    "timestamp": "2026-10-14 20:00:00"
  }
}
```

> **Night Audit Freeze (00:00–00:05) Zero-Pool Behavior:** To strictly uphold the "Zero Connection Pool" isolation commitment and prevent DB lock contention with audit transactions, `/auth/login` verifies credentials via internal RAM cache (`CREDENTIAL_CACHE`) with zero HikariCP pool consumption. Uncached accounts during freeze return `503 ERR_503_SYSTEM_FROZEN`.

---

### 2. Deep Health & Resource Liveness Check
- **Method:** `GET`
- **Route:** `/api/v1/health`
- **Auth:** None (Public / Monitoring)

```bash
curl -X GET http://localhost:8081/api/v1/health
```

**Success Response (200 OK):**
```json
{
  "status": "SUCCESS",
  "data": {
    "status": "UP",
    "database": "CONNECTED",
    "hikari_pool": {
      "active_connections": 1,
      "idle_connections": 9,
      "total_connections": 10,
      "threads_awaiting_connection": 0
    },
    "jvm": {
      "free_memory_mb": 182.4,
      "total_memory_mb": 256.0,
      "max_memory_mb": 1024.0
    }
  },
  "meta": {
    "timestamp": "2026-10-14 20:00:00"
  }
}
```

---

### 3. Room Catalog
- **Method:** `GET`
- **Route:** `/api/v1/rooms`
- **Auth:** Bearer JWT

```bash
curl -X GET http://localhost:8081/api/v1/rooms \
  -H "Authorization: Bearer <TOKEN>"
```

**Success Response (200 OK):**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 101,
      "room_number": "101",
      "room_type": "Deluxe",
      "unit_price": 1800000.00,
      "status": "vacant_clean",
      "version": 0
    }
  ],
  "meta": {
    "timestamp": "2026-10-14 20:00:00"
  }
}
```

---

### 4. Dynamic Room Availability Search
- **Method:** `GET`
- **Route:** `/api/v1/rooms/availability?check_in=2026-10-15&check_out=2026-10-18&room_type=Deluxe`
- **Auth:** Bearer JWT

```bash
curl -X GET "http://localhost:8081/api/v1/rooms/availability?check_in=2026-10-15&check_out=2026-10-18&room_type=Deluxe" \
  -H "Authorization: Bearer <TOKEN>"
```

> **Dynamic Availability Business Rules:**
> - **Same-day check-in (`check_in <= today`):** Room physical status must be strictly `vacant_clean`.
> - **Future check-in (`check_in > today`):** Room physical status must NOT be `out_of_order` or `maintenance` (currently occupied or uncleaned rooms are available for future dates), and zero schedule overlap with existing bookings (`status IN ('confirmed', 'checked_in', 'reserved')`).

---

### 5. Register Customer Profile
- **Method:** `POST`
- **Route:** `/api/v1/customers`
- **Auth:** Bearer JWT

```bash
curl -X POST http://localhost:8081/api/v1/customers \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "Tran Thi B",
    "id_card": "079195001234",
    "phone_number": "0912345678",
    "email": "tranthib@luxuryhotel.vn",
    "address": "456 Tran Hung Dao, Da Nang",
    "date_of_birth": "1992-05-20"
  }'
```

**Duplicate Conflict Response (409 Conflict):**
```json
{
  "error_code": "ERR_409_CONFLICT",
  "message": "Customer with ID card '079195001234' or phone '0912345678' already exists.",
  "timestamp": "2026-10-14 20:00:00"
}
```

---

### 6. Search Customer Profiles
- **Method:** `GET`
- **Route:** `/api/v1/customers?search=Tran`
- **Auth:** Bearer JWT

```bash
curl -X GET "http://localhost:8081/api/v1/customers?search=Tran" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 7. List Recent Bookings
- **Method:** `GET`
- **Route:** `/api/v1/bookings`
- **Auth:** Bearer JWT

```bash
curl -X GET http://localhost:8081/api/v1/bookings \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 8. Get Booking Details by ID
- **Method:** `GET`
- **Route:** `/api/v1/bookings/1001`
- **Auth:** Bearer JWT

```bash
curl -X GET http://localhost:8081/api/v1/bookings/1001 \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 9. Create Atomic Booking (Optimistic CAS)
- **Method:** `POST`
- **Route:** `/api/v1/bookings`
- **Auth:** Bearer JWT

```bash
curl -X POST http://localhost:8081/api/v1/bookings \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": 1,
    "room_id": 101,
    "room_version": 0,
    "check_in_date": "2026-10-15 14:00:00",
    "check_out_date": "2026-10-18 12:00:00",
    "num_guests": 2,
    "deposit_amount": 1000000.00
  }'
```

**CAS Conflict Response (409 Conflict):**
```json
{
  "error_code": "ERR_409_CONFLICT",
  "message": "Room version conflict detected. Room was modified or is not vacant_clean.",
  "timestamp": "2026-10-14 20:00:00"
}
```

---

### 10. Cancel Booking
- **Method:** `POST`
- **Route:** `/api/v1/bookings/1001/cancel`
- **Auth:** Bearer JWT

```bash
curl -X POST http://localhost:8081/api/v1/bookings/1001/cancel \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 11. Modify Stay Dates & Overbooking Guardrail
- **Method:** `PUT`
- **Route:** `/api/v1/bookings/{id}`
- **Auth:** Bearer JWT

```bash
curl -X PUT http://localhost:8081/api/v1/bookings/1001 \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "room_version": 1,
    "check_in_date": "2026-10-25 14:00:00",
    "check_out_date": "2026-10-28 12:00:00",
    "total_amount": 5400000.00
  }'
```

**Overbooking Conflict Response (409 Conflict):**
```json
{
  "error_code": "ERR_409_CONFLICT",
  "message": "Schedule conflict: Room is already booked by another reservation for the requested stay window (2026-10-25 14:00:00 to 2026-10-28 12:00:00)",
  "timestamp": "2026-10-14 20:00:00"
}
```

> **Data Integrity Guardrail:** Amending booking dates requires a match on `room_version` (CAS optimistic lock) AND executes an overlap check across the `bookings` table for the target room. Overlapping dates with another reservation are rejected with `409 Conflict`, completely preventing double-booking.

---

### 12. Store-and-Forward POS Sync Batch
- **Method:** `POST`
- **Route:** `/api/v1/pos/sync`
- **Auth:** Bearer JWT

```bash
curl -X POST http://localhost:8081/api/v1/pos/sync \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "BATCH-20261014-POS-01",
    "terminal_id": "POS-REST-01",
    "transactions": [
      {
        "transaction_id": "TXN-90812",
        "folio_id": 501,
        "amount": 450000.00,
        "service_code": "REST_DINING",
        "description": "Dinner Buffet for 2",
        "timestamp": "2026-10-14 00:02:15"
      }
    ]
  }'
```

---

## 5. ROOM STATUS RBAC TRANSITIONS

Allowed states: `vacant_clean`, `vacant_dirty`, `occupied_clean`, `occupied_dirty`, `out_of_order`, `reserved`.

- **Front Desk:** `vacant_clean` → `occupied_clean` (Check-in), `occupied` → `vacant_dirty` (Check-out), `vacant_clean` → `reserved` (Booking)
- **Housekeeping:** `vacant_dirty` → `vacant_clean`, `occupied_dirty` → `occupied_clean`
- **Maintenance / Admin:** Any state → `out_of_order` → `vacant_clean`

---

## 6. AI GATEWAY GUARDRAILS

- **AI Gateway** CANNOT call `POST /api/v1/bookings` directly to commit reservations. It must call `POST /api/v1/bookings/draft` returning a front-desk UI confirmation deep-link.
- **AI Gateway** CANNOT execute Room Check-in / Check-out status transitions. Read-only availability access is strictly enforced.

---

## 7. MANDATORY HTTP HEADERS MATRIX
| Header | Type | Required Endpoints | Behavior & Guardrails |
|---|---|---|---|
| `Idempotency-Key` | UUIDv4 (String) | `POST /api/v1/bookings*`, `POST /api/v1/folios/{id}/pay`, `POST /api/v1/rooms/{id}/minibar` | Deduplication engine locks key; duplicate submissions return original response without re-executing ledger mutations. |
| `Authorization` | `Bearer <JWT>` | Protected endpoints (Staff & Scoped Guest) | Validates signature (HMAC-SHA256); verifies RBAC roles (`front_desk`, `housekeeping`, `f&b`, `admin`, `guest`). |
| `X-Signature` | Hex String | `POST /api/v1/webhooks/vietqr` | `hex(HMAC_SHA256(bank_secret, raw_body))`; invalid signature rejected immediately with HTTP 401. |
| `Origin` | Domain String | All Web CORS requests | Native `CorsFilter` verifies domain and responds to preflight `OPTIONS` with `204 No Content`. |

### 1.4 Phase 1 Folio and Room Operations
These routes support Phase 1 folio routing and the OOO-room manager override:
- `POST /api/v1/folios/{id}/split` — create or return the booking's Guest Folio alongside the Master Folio.
- `PUT /api/v1/folios/{id}/routing` — request folio selection with `{"charge_type":"room_rate|service|..."}`; room charges route to Master and incidentals to Guest.
- `POST /api/v1/rooms/{bookingId}/downgrade` — admin/manager override with `{"replacement_room_id":123}`; reassigns an eligible booking to a lower-rate vacant room and applies a folio credit.

---

## 8. PHASE 2 API CONTRACT SPECIFICATIONS

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

## 9. PHASE 3 API CONTRACT SPECIFICATIONS

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

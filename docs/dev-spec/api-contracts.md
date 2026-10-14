# API CONTRACTS & INTEGRATION GUIDE (PHASE 1 CORE REST API)

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

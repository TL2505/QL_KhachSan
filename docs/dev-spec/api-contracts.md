# API CONTRACTS & GUARDRAILS

## 1. STANDARD ERROR FORMAT & HTTP CODES
HTTP codes: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `500 Internal Server Error`, `503 Service Unavailable`.
```json
{
  "error_code": "ERR_409_CONFLICT",
  "message": "Room already assigned to another guest.",
  "timestamp": "2026-10-10T00:00:00Z"
}
```

## 2. API ENDPOINTS BY PHASE
### PHASE 1: CORE PMS
- `POST /api/v1/auth/login` (Returns JWT)
- `GET /api/v1/rooms` (List rooms)
- `GET /api/v1/rooms/availability`
- `POST /api/v1/customers`
- `GET /api/v1/bookings` (List/Filter)
- `GET /api/v1/bookings/{id}`
- `POST /api/v1/bookings` (Requires `Idempotency-Key`)
- `PUT /api/v1/bookings/{id}` (Modify)
- `POST /api/v1/bookings/{id}/cancel`
- `POST /api/v1/folios/{id}/pay` (Requires `Idempotency-Key`)

### PHASE 2: OPERATIONS & AI
- `POST /api/v1/auth/guest`
- `POST /api/v1/rooms/{id}/check-in`
- `POST /api/v1/bookings/{id}/check-out`
- `POST /api/v1/services/request`
- `POST /api/v1/bookings/group`
- `PUT /api/v1/folios/{id}/routing`
- `POST /api/v1/folios/{id}/split`
- `POST /api/v1/system/night-audit`
- `POST /api/v1/ai/chat` (AI Gateway - Guardrails applied)
- `POST /api/v1/ai/escalate`
- `GET /api/v1/ai/interactions`

### PHASE 3: SCALE & LOYALTY
- `GET /api/v1/promotions/validate`
- `PUT /api/v1/promotions/{id}`
- `GET /api/v1/loyalty/{id}`
- `POST /api/v1/feedback`
- `POST /api/v1/staff`
- `GET /api/v1/reports/dbr`
- `GET /api/v1/reports/occupancy`
- `POST /api/v1/notifications/send`
- `POST /api/v1/webhooks/subscribe`
- `POST /api/v1/webhooks/receive`

## 3. ROOM STATUS RBAC TRANSITIONS
Allowed states: `vacant_clean`, `vacant_dirty`, `occupied_clean`, `occupied_dirty`, `out_of_order`, `reserved`.
- **Front Desk:** `vacant_clean` → `occupied_clean` (Check-in), `occupied` → `vacant_dirty` (Check-out)
- **Housekeeping:** `dirty` → `clean`
- **Maintenance:** Any state → `out_of_order` → `vacant_clean`

## 4. RESPONSE EXAMPLES
**Pagination:**
```json
{
  "data": [...],
  "meta": { "page": 1, "size": 20, "total": 150 }
}
```
**Booking Object:**
```json
{
  "id": "BKG-123",
  "guest_id": "CUS-456",
  "room_type": "DELUXE",
  "status": "CONFIRMED",
  "total_amount": 1500.00
}
```

## 5. ENDPOINT GUARDRAILS (AI Enforcement)
- **AI Gateway** CANNOT call `POST /bookings` directly. It calls a wrapper `POST /bookings/draft` returning a UI deep-link.
- **AI Gateway** CANNOT call `ROOM-02` (Check-in). Read-only access to `ROOM-01` only.

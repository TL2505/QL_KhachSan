# HỢP ĐỒNG API (API CONTRACTS) & GUARDRAILS

## 1. ĐỊNH DẠNG LỖI CHUẨN & MÃ HTTP
Mã HTTP chuẩn: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `500 Internal Server Error`, `503 Service Unavailable`.
```json
{
  "error_code": "ERR_409_CONFLICT",
  "message": "Phòng đã được gán cho một khách khác.",
  "timestamp": "2026-10-10T00:00:00Z"
}
```

## 2. API ENDPOINTS THEO GIAI ĐOẠN
### GIAI ĐOẠN 1: PMS CỐT LÕI
- `POST /api/v1/auth/login` (Trả về JWT)
- `GET /api/v1/rooms` (Danh sách phòng)
- `GET /api/v1/rooms/availability`
- `POST /api/v1/customers`
- `GET /api/v1/bookings` (Danh sách/Lọc)
- `GET /api/v1/bookings/{id}`
- `POST /api/v1/bookings` (Bắt buộc có `Idempotency-Key`)
- `PUT /api/v1/bookings/{id}` (Chỉnh sửa)
- `POST /api/v1/bookings/{id}/cancel`
- `POST /api/v1/folios/{id}/pay` (Bắt buộc có `Idempotency-Key`)

### GIAI ĐOẠN 2: VẬN HÀNH & AI
- `POST /api/v1/auth/guest`
- `POST /api/v1/rooms/{id}/check-in`
- `POST /api/v1/bookings/{id}/check-out`
- `POST /api/v1/services/request`
- `POST /api/v1/bookings/group`
- `PUT /api/v1/folios/{id}/routing`
- `POST /api/v1/folios/{id}/split`
- `POST /api/v1/system/night-audit`
- `POST /api/v1/ai/chat` (AI Gateway - Áp dụng Guardrails)
- `POST /api/v1/ai/escalate`
- `GET /api/v1/ai/interactions`

### GIAI ĐOẠN 3: MỞ RỘNG & KHÁCH THÂN THIẾT
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

## 3. CHUYỂN ĐỔI TRẠNG THÁI PHÒNG (RBAC)
Các trạng thái hợp lệ: `vacant_clean`, `vacant_dirty`, `occupied_clean`, `occupied_dirty`, `out_of_order`, `reserved`.
- **Lễ tân (Front Desk):** `vacant_clean` → `occupied_clean` (Check-in), `occupied` → `vacant_dirty` (Check-out)
- **Buồng phòng (Housekeeping):** `dirty` → `clean`
- **Bảo trì (Maintenance):** Bất kỳ trạng thái nào → `out_of_order` → `vacant_clean`

## 4. VÍ DỤ PHẢN HỒI (RESPONSE EXAMPLES)
**Phân trang (Pagination):**
```json
{
  "data": [...],
  "meta": { "page": 1, "size": 20, "total": 150 }
}
```
**Đối tượng Booking:**
```json
{
  "id": "BKG-123",
  "guest_id": "CUS-456",
  "room_type": "DELUXE",
  "status": "CONFIRMED",
  "total_amount": 1500.00
}
```

## 5. RÀNG BUỘC ĐIỂM CUỐI (Thực thi bởi AI)
- **AI Gateway** KHÔNG THỂ gọi trực tiếp `POST /bookings`. Nó gọi một wrapper `POST /bookings/draft` trả về một deep-link (đường dẫn mở UI).
- **AI Gateway** KHÔNG THỂ gọi `ROOM-02` (Check-in). Nó chỉ có quyền đọc đối với `ROOM-01`.

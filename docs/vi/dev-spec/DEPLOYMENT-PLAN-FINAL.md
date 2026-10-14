# KẾ HOẠCH TRIỂN KHAI CUỐI CÙNG — HỆ THỐNG PMS 5★
*Tổng hợp từ: Phân tích khoảng trống + Đặc tả 17 file + Kiểm toán hệ thống hiện tại*

## 0. KẾT LUẬN
- **Sẵn sàng cho Production?** KHÔNG.
- **Sẵn sàng cho Demo nội bộ?** CÓ — sau 2 bản vá nhanh (JWT + định dạng lỗi, ~2h).
- **Sẵn sàng cho 5★ hoàn chỉnh?** Giai đoạn 4 (Tuần 12-16).

## 1. GIAI ĐOẠN 0: VÁ LỖI NHANH (Ngày 1-2, tổng ~4h)
*Làm cho hệ thống hiện tại đủ an toàn để demo nội bộ.*

| # | Bản vá | Công sức | File bị ảnh hưởng |
|---|---|---|---|
| 1 | Thêm JWT vào mọi endpoints (hiện tại 0 auth) | 2h | docker-compose, backend |
| 2 | Định dạng lỗi chuẩn + Mã HTTP | 1h | backend |
| 3 | Xóa mở port DB (3307 → chỉ nội bộ) | 10p | docker-compose.yml |
| 4 | Chuyển password vào .env | 10p | docker-compose.yml |
| 5 | Thêm UFW (chỉ mở 22, 80) | 10p | OS |

**Sau Giai đoạn 0:** Hệ thống an toàn để demo nội bộ. KHÔNG dành cho khách thực.

## 2. GIAI ĐOẠN 1: PMS CỐT LÕI (Tuần 1-4)
*Xây dựng nền tảng. 3 containers.*

### Containers:
| Container | Port | Mở ra ngoài? |
|---|---|---|
| Nginx (TLS) | 443/80 | Có |
| Java Backend (Spring Boot 17) | 8081 | Chỉ nội bộ |
| MariaDB 11 | 3306 | Chỉ nội bộ |

### Phạm vi (10 endpoints):
- POST /api/v1/auth/login (JWT, hết hạn 1h)
- GET /api/v1/rooms/availability?start=&end=&type=
- GET /api/v1/rooms (phân trang)
- PUT /api/v1/rooms/{id}/status (Chuyển đổi RBAC)
- POST /api/v1/customers
- POST /api/v1/bookings (Idempotency-Key)
- GET /api/v1/bookings?date=&type=
- POST /api/v1/bookings/{id}/cancel
- POST /api/v1/folios/{id}/pay (Idempotency-Key)
- GET /api/v1/health

### Trạng thái phòng (6 trạng thái + RBAC):
| Vai trò | Chuyển đổi cho phép |
|---|---|
| BUỒNG PHÒNG | VD→VC, OD→OC, bất kỳ→OOO |
| LỄ TÂN | VC→RESERVED, RESERVED→OC, OC→VD |
| ADMIN | bất kỳ→bất kỳ |

### Kiểm toán đêm (QUAN TRỌNG — xây trong Giai đoạn 1):
- 7 bước: Khóa → Xác minh → Đăng Phí Phòng → Đăng Thuế → Đối soát POS → Chuyển Ngày → Mở khóa
- Khóa = 503 + Retry-After: 300
- @Scheduled 00:00 (Spring)
- Thuế: VAT 10% + Thuế thành phố (theo luật địa phương)
- No-Show: tự động đăng phí phạt 1 đêm

### Bảo mật:
- [ ] Middleware JWT + RBAC
- [ ] DB chỉ nội bộ
- [ ] .env (không trong git)
- [ ] UFW (22, 80, 443)
- [ ] Chỉ dùng SSH key + fail2ban
- [ ] HTTPS (self-signed cho dev / Let's Encrypt cho prod)

### Tiêu chí Go/No-Go → Giai đoạn 2:
- [ ] Cả 10 endpoints vượt qua bài test Postman
- [ ] Night Audit chạy thành công 3 đêm liên tiếp
- [ ] 0 PII bị truy cập nếu không có JWT
- [ ] Chuyển đổi trạng thái phòng được thực thi (test: Buồng phòng thử đặt RESERVED → 403)

## 3. GIAI ĐOẠN 2: AI + VẬN HÀNH (Tuần 5-8)
*Thêm AI Gateway + hóa đơn nâng cao. 5 containers.*

### Containers mới:
| Container | Port | Mở ra ngoài? |
|---|---|---|
| pgvector (PostgreSQL 16) | 5432 | Chỉ nội bộ |
| AI Gateway (Python 3.11, FastAPI) | 8082 | Chỉ nội bộ |

### Endpoints mới (15):
- POST /api/v1/auth/guest (Xác minh OTP → token giới hạn)
- POST /api/v1/bookings/{id}/check-in (tùy chọn roomId, tiền cọc)
- POST /api/v1/bookings/{id}/check-out (kiểm tra số dư, ngoại lệ trả sau)
- POST /api/v1/services/request
- POST /api/v1/bookings/group (hóa đơn tổng + định tuyến thanh toán)
- PUT /api/v1/folios/{id}/routing
- POST /api/v1/folios/{id}/split
- GET /api/v1/folios/{id}/interim
- POST /api/v1/ai/chat (áp dụng guardrails)
- POST /api/v1/ai/escalate (chuyển tiếp ấm + SLA)
- GET /api/v1/ai/interactions?date=&page=&size= (Chỉ ADMIN)
- POST /api/v1/system/night-audit/execute (Chỉ ADMIN/AUDITOR)
- POST /api/v1/system/night-audit/abort (Chỉ ADMIN, 2FA)
- GET /api/v1/reports/dbr
- GET /api/v1/reports/occupancy

### Kiến trúc AI Gateway:
`Yêu cầu → Guardrail Đầu vào → RAG (pgvector) → LLM (gpt-4o-mini) → Guardrail Đầu ra → Kiểm tra Leo thang → Audit Log → Phản hồi`

### Guardrails (mức độ code, KHÔNG chỉ trong prompt):
- Xác thực giá: phản hồi phải khớp với giá PMS (regex + đối chiếu chéo API)
- Che giấu PII: chặn bất kỳ guest_id ≠ authenticated_id
- Danh sách chặn từ khóa: refund, discount, free, upgrade
- Độ tự tin < 80% → tự động dự phòng
- Tiêm nhiễm prompt: khớp mẫu → bỏ qua + ghi log

### Tiêu chí Go/No-Go → Giai đoạn 3:
- [ ] Tỷ lệ vi phạm AI < 0.1% trong 7 ngày
- [ ] SLA Leo thang > 95% (phản hồi trong 2p)
- [ ] 0 rò rỉ PII trong 1000 tương tác
- [ ] Vượt qua cả 6 kịch bản kiểm thử (đoàn, overbooking, POS offline, tiêm nhiễm, xung đột, lách luật)
- [ ] Vượt qua kịch bản Night Audit + POS offline

## 4. GIAI ĐOẠN 3: MỞ RỘNG & TÍCH HỢP (Tuần 9-12)
*OTA, thanh toán, POS, báo cáo. 6+ containers.*

### Endpoints mới (15+):
- POST /api/v1/promotions (Marketing/Admin)
- GET /api/v1/promotions/validate
- GET /api/v1/loyalty/{customerId}
- POST /api/v1/loyalty/{customerId}/adjust
- POST /api/v1/feedback (đánh giá của khách)
- POST /api/v1/notifications/send (SMS/Email)
- POST /api/v1/staff
- PUT /api/v1/staff/{id}/roles
- POST /api/v1/webhooks/subscribe (HMAC-SHA256)
- POST /api/v1/webhooks/receive (OTA/Thanh toán đến)
- GET /api/v1/reports/revenue-by-channel
- GET /api/v1/reports/revpar

### Tích hợp:
| Hệ thống | Giao thức | Đồng bộ |
|---|---|---|
| Booking.com / Agoda | XML (HTNG) + Webhook | Đẩy mỗi 5p + theo sự kiện |
| Thanh toán (Stripe/Nội địa) | REST API + Webhook | Thời gian thực |
| POS | Direct API + Store & Forward | Thời gian thực + Đối soát Night Audit |
| SMS/Email | Provider API (Twilio/Nội địa) | Theo sự kiện |

### Sự kiện Webhook (8):
reservation_created, reservation_cancelled, check_in_completed,
check_out_completed, room_status_changed, payment_captured,
night_audit_completed, no_show_processed

### Tiêu chí Go/No-Go → Sản xuất (Production):
- [ ] Đồng bộ OTA: 0 overbooking trong 7 ngày
- [ ] Thanh toán: 0 trừ tiền kép trong 100 giao dịch
- [ ] AI CSAT ≥ 4/5 trong 2 tuần
- [ ] Uptime > 99.5%
- [ ] Backup: lưu 30 ngày, đã kiểm thử phục hồi

## 5. VẬN HÀNH (tiếp diễn)

### Hàng ngày:
- Night Audit tự động chạy 00:00
- Backup: cron 02:00 (mysqldump → /backups/, lưu 30 ngày)
- Giám sát: Bảng điều khiển Grafana (tỷ lệ vi phạm, SLA, AHT, uptime)

### Hàng tuần:
- Đánh giá log tương tác AI (100 mẫu ngẫu nhiên)
- Báo cáo vi phạm → cập nhật constraints.md nếu cần
- Đánh giá CHANGELOG.md

### Hàng tháng:
- Xoay vòng khóa API AI
- Quét bảo mật (OWASP ZAP)
- Test tải (50 khách đồng thời + 10 nhân viên)

### Ứng phó Sự cố:
- Cấp 1 (Lộ PII / sai giá): CÔNG TẮC NGẮT (KILL SWITCH) → Báo IT + GM ngay lập tức
- Cấp 2 (Sai chính sách / AI offline): Hạ cấp xuống FAQ tĩnh
- Cấp 3 (Tỷ lệ dự phòng cao): Cảnh báo IT, theo dõi

## 6. ĐỘI NGŨ & RACI

| Nhiệm vụ | Bạn (Nghiệp vụ) | Dev | AI (công cụ) |
|---|---|---|---|
| Ràng buộc & quy tắc | **R/A** | C | A |
| Xây dựng API | C | **R** | A |
| Xây dựng AI Gateway | C (đặc tả) | **R** | A |
| Guardrails (code) | C (đặc tả) | **R** | A |
| Kịch bản test | **R** (logic) | R (code) | A |
| Giám sát thử nghiệm | **R** | C | — |
| Quyết định Go/No-Go | **A** | C | — |

## 7. TỔNG HỢP TIẾN ĐỘ

| Giai đoạn | Thời lượng | Bàn giao |
|---|---|---|
| Giai đoạn 0 (Vá nhanh) | 2 ngày | Bản demo nội bộ an toàn |
| Giai đoạn 1 (PMS Lõi) | 4 tuần | 10 endpoints + Night Audit |
| Giai đoạn 2 (AI + Vận hành) | 4 tuần | AI Gateway + 15 endpoints |
| Giai đoạn 3 (Mở rộng) | 4 tuần | OTA + Thanh toán + Báo cáo |
| **TỔNG CỘNG** | **~12 tuần + 2 ngày** | **Sẵn sàng cho Sản xuất** |

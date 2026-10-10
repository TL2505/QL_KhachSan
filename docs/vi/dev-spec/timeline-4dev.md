# LỘ TRÌNH DỰ ÁN — NHÓM 4 DEV (HỆ THỐNG PMS 5★ + AI)

## Vai trò trong nhóm
| Dev | Vai trò | Trọng tâm |
|---|---|---|
| Dev A | Backend API | Endpoints, JWT, RBAC, Thanh toán |
| Dev B | Logic Nghiệp vụ | Night Audit, Hóa đơn (Folio), Đặt phòng đoàn, AI Gateway |
| Dev C | Frontend | React/Vue, Dashboard Nhân viên, Chat UI cho Khách |
| Dev D | DevOps + QA | Docker, CI/CD, Kiểm thử, Bảo mật, Giám sát |

## Giai đoạn 0: Vá lỗi nhanh (Ngày 1-2)
| Dev | Nhiệm vụ |
|---|---|
| Dev A | JWT cho mọi endpoints + Lỗi chuẩn + Mã HTTP |
| Dev B | Thiết kế schema DB + Khung Docker (docker-compose.yml) |
| Dev C | Khởi tạo dự án React/Vue + Cấu hình Nginx + TLS (self-signed) |
| Dev D | .env + .gitignore + UFW (22,80,443) + Chỉ SSH key + fail2ban |

**Cột mốc (Ngày 3):** Hoàn thành Giai đoạn 0 → An toàn để demo nội bộ.

## Giai đoạn 1: PMS Cốt lõi (Tuần 1-4)
| | Tuần 1 | Tuần 2 | Tuần 3 | Tuần 4 |
|---|---|---|---|---|
| Dev A | Endpoints: Xác thực, Phòng, Trống, Khách, Đặt phòng | Endpoints: Hủy, Hóa đơn, Thanh toán, Health, Danh sách | Test tích hợp với Dev B | Test Go/No-Go |
| Dev B | 7 bước Night Audit + Quy tắc khóa | Ngoại lệ Night Audit (POS offline, C/O nửa đêm, No-Show) + Thuế | Đặt phòng đoàn + Định tuyến/tách/tạm tính Hóa đơn | Test tích hợp |
| Dev C | UI Login + DS Phòng + Form Đặt (dữ liệu giả) | UI Khách + Hóa đơn + Thanh toán | Kết nối API thật (xóa giả) | Test E2E |
| Dev D | Code 6 kịch bản test + Khung Unit test | Khung Integration test | Test tải (50 concurrent) + Quét bảo mật (OWASP ZAP) | Checklist Go/No-Go |

**Cột mốc (Cuối Tuần 4):** Go/No-Go Giai đoạn 1.
**Tiêu chí:**
- [ ] 10 endpoints vượt qua Postman
- [ ] Night Audit chạy 3 đêm liên tiếp
- [ ] 0 PII bị truy cập nếu không có JWT
- [ ] Chuyển đổi trạng thái phòng bị cấm (Buồng phòng đặt RESERVED → 403)
- [ ] Vượt qua 6 kịch bản test

## Giai đoạn 2: AI + Vận hành (Tuần 5-7)
| | Tuần 5 | Tuần 6 | Tuần 7 |
|---|---|---|---|
| Dev A | Xác thực khách (OTP), Check-in/out, Dịch vụ, Endpoints Đoàn | Hóa đơn nâng cao (định tuyến, tách), Kích hoạt Night Audit thủ công | Test tích hợp |
| Dev B | AI Gateway: main.py + llm_client.py | AI Gateway: rag.py + guardrails.py + escalation.py | AI Gateway: audit log + webhook giám sát |
| Dev C | Chat UI Khách + Dashboard Nhân viên | UI Cảnh báo leo thang + Trình xem tương tác AI | Test E2E (Kịch bản AI) |
| Dev D | Cài đặt pgvector + Pipeline RAG (index ngoại lệ, leo thang, SOPs) | 4 kịch bản test AI (tiêm nhiễm, lách luật, đồng thời, offline) | Dashboard giám sát (Grafana) + Luật cảnh báo |

**Cột mốc (Cuối Tuần 7):** Go/No-Go Giai đoạn 2.
**Tiêu chí:**
- [ ] Tỷ lệ vi phạm AI < 0.1% trong 7 ngày
- [ ] SLA Leo thang > 95% (xác nhận trong 2 phút)
- [ ] 0 rò rỉ PII trong 1000 tương tác
- [ ] Vượt qua 6 kịch bản test (bao gồm AI)
- [ ] Vượt qua kịch bản Night Audit + POS offline

## Giai đoạn 3: Mở rộng & Tích hợp (Tuần 8-9)
| | Tuần 8 | Tuần 9 |
|---|---|---|
| Dev A | Endpoints: Khuyến mãi, Loyalty, Phản hồi, Thông báo, QL Nhân viên | Webhooks (nhận + đăng ký), Báo cáo (DBR, công suất, doanh thu kênh) |
| Dev B | Tích hợp OTA (Booking.com/Agoda XML/HTNG) | Cổng thanh toán (Stripe/local) + Tích hợp POS |
| Dev C | Dashboard Admin (báo cáo, nhân viên, KM) | UI Cấu hình Webhook + Template thông báo |
| Dev D | Pipeline CI/CD (GitHub Actions) + Phân tách môi trường (dev/staging/prod) | Tăng cường bảo mật Prod + Cron sao lưu + Test phục hồi |

**Cột mốc (Cuối Tuần 9):** Go/No-Go Production.
**Tiêu chí:**
- [ ] Đồng bộ OTA: 0 overbooking trong 7 ngày (staging)
- [ ] Thanh toán: 0 trừ tiền kép trong 100 giao dịch
- [ ] Uptime > 99.5% (staging)
- [ ] Sao lưu: giữ 30 ngày, đã test phục hồi

## GO-LIVE (Tuần 10)
- Triển khai lên Production (6 containers)
- Phạm vi giới hạn: Chatbot khách CHỈ-ĐỌC (xem phòng, FAQ, dịch vụ)
- Nhân viên dùng PMS thủ công (không AI ghi đè)
- Kích hoạt giám sát (Grafana + Cảnh báo Slack)

## Giai đoạn 4: Thí điểm & Mở rộng (Tuần 11-15)
| Tuần | Mở khóa tính năng | Rủi ro | Go/No-Go |
|---|---|---|---|
| 11 | Chatbot khách Live (20-50 khách thật) | Thấp | Vi phạm < 0.1% trong 7 ngày |
| 12 | Staff Copilot (Lễ tân) | TB | CSAT Nhân viên ≥ 4/5 |
| 13 | Nháp đặt phòng (Preview trước khi ghi) | Cao | 0 lỗi đặt phòng trong 14 ngày |
| 14 | Yêu cầu dịch vụ + Điểm Loyalty | TB | 0 rò rỉ PII |
| 15 | Phân tích doanh thu (Admin) | Thấp | Chỉ dùng nội bộ |

## Vận hành liên tục (Tuần 16+)
| Tần suất | Nhiệm vụ | Người phụ trách |
|---|---|---|
| Hàng ngày | Night Audit chạy tự động + Backup (02:00) | Hệ thống |
| Hàng tuần | Đánh giá log AI (100 mẫu) + Báo cáo vi phạm | Bạn + Dev D |
| Hàng tuần | Đánh giá CHANGELOG.md | Mọi Dev |
| Hàng tháng | Đổi khóa API AI + Quét bảo mật (OWASP ZAP) | Dev D |
| Hàng quý | Test tải (50 khách + 10 nv) + Diễn tập thảm họa (DR) | Dev D + Bạn |

## Tổng kết
| Cột mốc | Thời gian |
|---|---|
| Giai đoạn 0 (Demo an toàn) | Ngày 3 |
| Giai đoạn 1 (PMS Cốt lõi) | Tuần 4 |
| Giai đoạn 2 (AI + Vận hành) | Tuần 7 |
| Giai đoạn 3 (Tích hợp) | Tuần 9 |
| GO-LIVE (Giới hạn) | Tuần 10 |
| AI 5★ hoàn chỉnh (Toàn bộ) | Tuần 15 |

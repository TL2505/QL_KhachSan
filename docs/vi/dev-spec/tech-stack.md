# ĐỊNH NGHĨA NGĂN XẾP CÔNG NGHỆ (TECH STACK)

| Hạng mục | Công nghệ | Phiên bản | Lý do chọn lựa |
| :--- | :--- | :--- | :--- |
| **Nhà cung cấp LLM** | OpenAI `gpt-4o-mini` | Mới nhất | Tốc độ cao, tối ưu chi phí cho volume chat lớn của 5★; Dự phòng: Claude 3 Haiku |
| **Vector Database** | `pgvector` (PostgreSQL) | 16 | Tuân thủ ACID, tìm kiếm vector native (1536 dim), dễ triển khai |
| **PMS Backend** | Java 17 (plain HttpServer) | 17 | Tiêu chuẩn Enterprise, máy chủ HTTP nhẹ (`com.sun.net.httpserver`), đa luồng mạnh mẽ |
| **Công cụ Build** | Maven (pom.xml) + javac | 3.9+ | Quản lý phụ thuộc tiêu chuẩn và build javac sạch sẽ trong Docker |
| **AI Gateway** | Python (FastAPI) | 3.11 | Thư viện native cho ML/AI, xử lý bất đồng bộ (async), lý tưởng cho điều phối LLM |
| **Frontend UI** | React + Vite | 18 | Hiệu năng cao, render cực nhanh cho Staff Dashboard |
| **DB Giao dịch** | MariaDB | 11 | DB quan hệ hiệu năng cao cho folios, bookings, và log tài chính |
| **Triển khai** | Docker Compose | v2 | Đơn giản hóa việc quản lý 6 microservices trên 1 máy ảo Ubuntu duy nhất |
| **Hệ điều hành** | Ubuntu LTS | 22.04 | Môi trường production ổn định, bảo mật |
| **CI/CD** | GitHub Actions | - | Tích hợp sẵn với repo, tự động hóa test và build image docker |
| **Giám sát** | Prometheus + Grafana | - | Bảng điều khiển thời gian thực cho độ trễ API và tỷ lệ vi phạm AI |

## KIẾN TRÚC NỀN TẢNG (PLATFORM ARCHITECTURE)
| Nền tảng | Người dùng | Công nghệ | Giai đoạn | Ghi chú |
|---|---|---|---|---|
| Desktop (Nhân viên) | Lễ tân, Buồng phòng, F&B | Java Swing (sẵn có) | Giai đoạn 1-2 (giữ) | Gọi /api/v1/* qua HTTP. Không đổi UI. |
| Web (Khách hàng) | Khách lưu trú | React 18 + TypeScript | Giai đoạn 3 | Nginx phục vụ. Xác thực OTP. |
| Web (Quản trị) | Admin/GM | React 18 + TypeScript | Giai đoạn 3 | Cùng frontend, khác quyền RBAC. |
| Mobile (Tương lai) | Khách lưu trú | PWA / React Native | Giai đoạn 4+ | Tái sử dụng các endpoints /api/v1/*. |

### NỀN TẢNG THEO GIAI ĐOẠN
| Giai đoạn | UI Nhân viên | UI Khách hàng | Trọng tâm Dev C |
|---|---|---|---|
| 1-2 | Swing (không đổi) | Không có | Tích hợp API vào Swing sẵn có. KHÔNG làm UI mới. |
| 3 | Swing + React Admin | React (đặt phòng, chat, đánh giá) | Xây dựng React web. |
| 4+ | React (tùy chọn) | React + PWA | Chuyển đổi Swing → React. |

## TRIẾT LÝ THIẾT KẾ NỀN TẢNG (PLATFORM DESIGN PHILOSOPHY)

**Nguyên tắc: Nền tảng = NƠI NGƯỜI DÙNG NGỒI.**

| Người dùng | Ngồi tại | Nền tảng | Giai đoạn | Lý do cốt lõi |
|---|---|---|---|---|
| Lễ tân / Buồng phòng / F&B | 1 bàn, 8h, nhiều màn hình | Desktop (Swing) | 1-3 (giữ) | Tốc độ, phím tắt bàn phím, không mất trạng thái khi F5 |
| Khách lưu trú | Điện thoại/tablet, 1-3 phút | Web (React) + PWA | 3-4 | Không cần cài đặt, giao diện thích ứng, đa ngôn ngữ |
| Admin / GM | Bất kỳ đâu | Web thích ứng (React) | 3+ | Báo cáo thân thiện trên di động |

### Hệ quả thiết kế
- Giai đoạn 1-2: **KHÔNG làm React.** Dev C chỉ tích hợp API vào Swing sẵn có.
- Giai đoạn 3: Làm React cho Khách + Quản trị. Swing giữ nguyên cho Nhân viên.
- Giai đoạn 4+: Di chuyển tùy chọn từ Swing → React (chỉ khi nhân viên yêu cầu).
- AI Chat: Khách = React. Nhân viên = Swing (JWebView). Hai UI tách biệt, chung một API.
- **Không có 'frontend thống nhất' cho đến Giai đoạn 4+.**

### Backend dùng chung (Độc lập nền tảng)
Mọi nền tảng → cùng một Java backend → cùng tập endpoint `/api/v1/*`.
Khác biệt: Phương thức xác thực + Vai trò phân quyền RBAC + Thư viện UI. Ngoài ra không có gì khác.

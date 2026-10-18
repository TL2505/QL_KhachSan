# BẢNG SO SÁNH TOÀN DIỆN HỆ THỐNG CŨ (LEGACY) VS HỆ THỐNG MỚI (NEW PMS) & LỘ TRÌNH NÂNG CẤP
## Aurelia Grand Palace 5★ Luxury Property Management System

**Tài liệu:** Baseline System Architecture Gap Analysis & Modernization Roadmap  
**Đường dẫn tài liệu:** `docs/SYSTEM_GAP_ANALYSIS_AND_ROADMAP.md`  
**Phiên bản:** 3.5.0  
**Tác giả:** Principal Full-Stack Architect & Lead Enterprise Systems Engineer  

---

## 1. TỔNG QUAN KIẾN TRÚC: CHUYỂN DỊCH TỪ LEGACY SANG MODERN CLOUD-READY ARCHITECTURE

| Tiêu chí | Hệ thống Cũ (Legacy Monolith Swing) | Hệ thống Mới (Modern 5★ Multi-tier PMS) |
| :--- | :--- | :--- |
| **Kiến trúc** | Client-Server 2 tầng cổ điển, kết nối JDBC trực tiếp từ Desktop đến MySQL. | Kiến trúc 3 tầng phân tán: Native ES Modules Web UI + FlatLaf Desktop Client + Java REST API Engine + Docker Microservices. |
| **Giao thức kết nối** | Kết nối socket raw MySQL qua port 3306 trực tiếp. | Chuẩn hóa HTTP/1.1 RESTful APIs, JSON Envelopes (RFC 7807), Webhooks, JWT Bearer Token, mTLS. |
| **Nền tảng khách hàng** | Không có giao diện cho khách hàng (chỉ có nhân viên lễ tân thao tác tại quầy). | Cổng thành viên Web Portal đa kênh (`portal.html`), trang đặt phòng trực tuyến 5★ (`index.html`) hỗ trợ thanh toán VietQR tự động. |
| **Mở rộng & Hạ tầng** | Cài đặt đơn lẻ trên từng máy trạm Windows bằng file .jar. | Đóng gói 6 container Docker (`mariadb`, `backend`, `web`, `ai-service`, `redis`, `monitoring`), Nginx reverse proxy, Netplan Static IP (`192.168.56.102`). |

---

## 2. MA TRẬN SO SÁNH CHI TIẾT 6 PHÂN HỆ NGHIỆP VỤ

### Phân hệ 1: Xác Thực, Tài Khoản & Phân Quyền (Auth & RBAC)
| Chức năng | Hệ thống Cũ (Legacy Schema / Desktop) | Hệ thống Mới (REST API + Web UI + Desktop) | Đánh giá & Khoảng cách đã đóng |
| :--- | :--- | :--- | :--- |
| **Cơ chế xác thực** | Lưu mật khẩu plain-text hoặc MD5 cũ; đối soát trực tiếp câu truy vấn `SELECT * FROM nhanvien WHERE username=? AND password=?`. | Mã hóa mật khẩu bằng BCrypt (`$2a$10$`), cấp phát JWT (HMAC-SHA256) có thời hạn, tự động thu hồi phiên qua `/api/v1/auth/logout`. | Đã khắc phục 100%: Bảo mật tối ưu, chống đánh cắp credential. |
| **Phân quyền (RBAC)** | Phân quyền cứng trong code Java Swing (cột `role` hoặc `chucvu` giới hạn trong nhân viên lễ tân/quản lý). | RBAC đa tầng (`ADMIN`, `STAFF`, `CUSTOMER`/`GUEST`), bộ lọc bảo vệ `JwtFilter`, `X-User-Role`, phân biệt rạch ròi giữa nhân viên và khách lưu trú. | Đã hoàn thiện: Bảo vệ trọn vẹn dữ liệu PII nội bộ khách sạn. |
| **Đăng ký khách hàng** | Nhân viên nhập thủ công tại quầy lễ tân khi khách tới nhận phòng. | Khách hàng tự đăng ký tài khoản trực tuyến qua Modal 2 tab trên [index.html](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/index.html) hoặc [portal.html](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/portal.html). | Đã hoàn thiện: Khách hàng chủ động quản lý tài khoản. |
| **Đổi mật khẩu** | Yêu cầu can thiệp kỹ thuật hoặc đổi mật khẩu thô trong form nội bộ. | Tự đổi mật khẩu bảo mật qua `#password-form` tại Cổng thành viên, kiểm tra mật khẩu hiện tại và độ dài tối thiểu. | Đã khắc phục với API `/api/v1/auth/change-password`. |

---

### Phân hệ 2: Khách Hàng, Hạng Hội Viên & Điểm Tích Lũy (CRM & Loyalty Tiers)
| Chức năng | Hệ thống Cũ (Legacy Schema / Desktop) | Hệ thống Mới (REST API + Web UI + Desktop) | Đánh giá & Khoảng cách đã đóng |
| :--- | :--- | :--- | :--- |
| **Cấu trúc hạng hội viên** | Bảng `khachhang` lưu `diemtichluy`, tính toán 4 cấp bậc: Member (<500), Silver (500-999), Gold (1000-4999), VIP (>=5000). | Kế thừa 100% logic tính hạng thẻ legacy qua [tier-helper.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/public/js/customer/tier-helper.js) và [LoyaltyService.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/customer/LoyaltyService.java). | Đã tích hợp 100% vào Thẻ thành viên kỹ thuật số (Digital Member Card). |
| **Cơ chế quy đổi điểm** | 1 điểm = 1.000 VNĐ chi tiêu phòng. | Hiển thị trực quan thanh tiến trình thăng hạng (Tier Progress Bar), tính chính xác số điểm và số tiền chi tiêu còn thiếu để lên hạng kế tiếp. | Đã nâng cấp vượt bậc so với hệ thống cũ. |
| **Cập nhật hồ sơ cá nhân** | Chỉ nhân viên lễ tân mới sửa được thông tin CCCD, ngày sinh, số điện thoại của khách trong database. | Khách hàng tự cập nhật thông tin cá nhân qua form `#profile-form` trên Portal, đồng bộ dữ liệu vào bảng `customers`. | Đã khắc phục với khối `try...catch...finally` chống treo UI. |

---

### Phân hệ 3: Danh Mục Phòng, Loại Phòng & Định Giá Động (Room Catalog & Dynamic Pricing)
| Chức năng | Hệ thống Cũ (Legacy Schema / Desktop) | Hệ thống Mới (REST API + Web UI + Desktop) | Đánh giá & Khoảng cách đã đóng |
| :--- | :--- | :--- | :--- |
| **Danh mục phòng** | Bảng `phong`, `loaiphong` với đơn giá cố định, không có mô tả chi tiết tiện ích hay hình ảnh chất lượng cao. | Danh mục phòng chuẩn quốc tế 5★: Deluxe King Ocean, Executive Suite, Royal Penthouse, Presidential Suite; có thư viện ảnh Lightbox 4K, tiện ích chi tiết, đánh giá sao. | Giao diện Web cực kỳ sang trọng, trải nghiệm người dùng vượt bậc. |
| **Chiến lược giá (Yield Management)** | Đơn giá cứng (static price) theo loại phòng. | Công cụ định giá động (Yield Management & Rate Recommendations) dự báo doanh thu dựa trên tỷ lệ lấp đầy phòng theo mùa. | Có endpoint `/api/v1/revenue/forecast` và `/api/v1/pricing/quote`. |
| **Khóa lạc quan (Concurrency)** | Không có khóa lạc quan; dễ bị xung đột Overbooking khi nhiều nhân viên cùng chọn 1 phòng. | Trường `version` trong bảng `rooms` kết hợp thuật toán Optimistic Locking (`ERR_409_CONFLICT`), triệt tiêu 100% rủi ro Overbooking. | Hoàn thiện ở cả tầng Database và API Controller. |

---

### Phân hệ 4: Vòng Đời Đặt Phòng, VietQR, Check-in/Check-out & Hủy Phòng
| Chức năng | Hệ thống Cũ (Legacy Schema / Desktop) | Hệ thống Mới (REST API + Web UI + Desktop) | Đánh giá & Khoảng cách đã đóng |
| :--- | :--- | :--- | :--- |
| **Quy trình đặt phòng** | Lễ tân mở form tạo đặt phòng thủ công trên Swing Desktop, nhận tiền mặt hoặc quẹt thẻ POS. | Luồng đặt phòng 3 bước trực quan trên Web: Lịch trình & Chi phí -> Điền thông tin (tự động auto-fill nếu đã đăng nhập) -> Quét mã VietQR. | Khách hàng tự đặt phòng 24/7 mà không cần qua nhân viên. |
| **Thanh toán đặt cọc** | Nhân viên kiểm tra sao kê ngân hàng bằng tay rồi cập nhật trạng thái phòng. | Tích hợp mã VietQR chuẩn NAPAS 247, sinh mã động theo số tiền và mã booking, Webhook tự động cập nhật trạng thái đã thanh toán. | Đã hoàn thiện qua [BankWebhookController.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/booking/BankWebhookController.java). |
| **Hủy đặt phòng** | Lễ tân xóa hoặc sửa trạng thái bản ghi trong bảng `datphong`. | Khách hàng tự hủy các đơn đặt phòng ở trạng thái `pending` trực tiếp từ Cổng thành viên [bookings.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/public/js/customer/bookings.js); không cho phép hủy khi đã check-in. | Đã hoàn thiện toàn diện ở cả Frontend và Backend. |
| **Nhận phòng nhanh (Express Check-in)** | Khách xếp hàng tại sảnh xuất trình giấy tờ tùy thân để lễ tân tra cứu. | Khách hàng sở hữu thẻ **Express Check-in QR Pass** trong Portal; lễ tân chỉ cần quét mã QR để lấy thông tin tức thì. | Tính năng 5★ hiện đại đã tích hợp vào Portal. |

---

### Phân hệ 5: Dịch Vụ Phụ Trợ, Minibar & Ví Voucher (Services & Voucher Wallet)
| Chức năng | Hệ thống Cũ (Legacy Schema / Desktop) | Hệ thống Mới (REST API + Web UI + Desktop) | Đánh giá & Khoảng cách đã đóng |
| :--- | :--- | :--- | :--- |
| **Gọi dịch vụ phòng / Minibar** | Khách gọi điện thoại bàn đến lễ tân, nhân viên ghi sổ tay rồi nhập vào phần mềm khi trả phòng. | Phân hệ **Web Concierge 24/7** trên Portal: Khách chọn dịch vụ (Giặt ủi, Spa Hoàng Gia, Đồ uống minibar, Dọn phòng, Đưa đón sân bay), chọn giờ phục vụ, gửi trực tiếp vào hệ thống. | Đã hoàn thiện module [concierge.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/public/js/customer/concierge.js) kết nối backend `/api/v1/service-usage`. |
| **Quản lý mã giảm giá / Voucher** | Không có tính năng voucher trực tuyến; giảm giá dựa vào quyết định thủ công của thu ngân. | Phân hệ **Ví Voucher (Voucher Wallet)** hiển thị danh sách ưu đãi (`LUXURY2026`, `VIP5STAR`, `WELCOME50K`, `SPA2026`), sao chép 1 chạm và tự động tính toán trừ vào tổng tiền phòng. | Đã hoàn thiện và tích hợp vào luồng tính giá tại [app.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/js/app.js). |

---

### Phân hệ 6: Hóa Đơn, Thanh Toán & Báo Cáo Thống Kê (Billing, iTextPDF & Analytics)
| Chức năng | Hệ thống Cũ (Legacy Schema / Desktop) | Hệ thống Mới (REST API + Web UI + Desktop) | Đánh giá & Khoảng cách đã đóng |
| :--- | :--- | :--- | :--- |
| **Hóa đơn điện tử** | In phiếu thanh toán bằng iText 5 cổ điển trên máy in nhiệt hóa đơn kết nối Desktop. | Mô-đun **Hóa đơn điện tử E-Folio Modal** trên Web Portal: phân tách minh bạch Tiền phòng, Thuế VAT 8%, Phí phục vụ 5★, Giảm giá voucher; hỗ trợ in ấn trực tuyến và tải PDF. | Đã hoàn thiện qua E-Folio modal tại [bookings.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/public/js/customer/bookings.js). |
| **Quản lý Folio đa tài khoản** | Mỗi phòng chỉ gắn với 1 tổng tiền duy nhất; không hỗ trợ tách hóa đơn công ty và cá nhân. | Hệ thống Folio Routing Engine chuẩn quốc tế: Folio A (Master - Công ty) và Folio B (Guest - Khách lẻ), hỗ trợ chuyển khoản mục phí giữa các folio linh hoạt. | Đã triển khai hoàn chỉnh trong [FolioService.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/booking/FolioService.java). |
| **Báo cáo tài chính & Night Audit** | Thống kê cuối ngày bằng truy vấn SQL cơ bản, dễ gặp rủi ro sai lệch dữ liệu nếu có giao dịch dở dang. | Thủ tục khóa sổ đêm tự động (Night Audit freeze) lúc 02:00 sáng, tính toán DBR (Daily Business Report), báo cáo ADR, RevPAR, Occupancy Rate chuẩn thống kê khách sạn 5 sao. | Đã hoàn thiện và tích hợp kiểm soát an toàn giao dịch. |

---

## 3. CÁC TÍNH NĂNG ĐÃ BỔ SUNG VƯỢT TRỘI (5-STAR ENHANCEMENTS)
1. **Giao diện FlatLaf "Dark Luxury" trên Desktop App:** Chuyển đổi toàn diện từ giao diện xám xịt của Swing cũ sang phong cách Hoàng Gia với bảng màu Slate Dark (`#0F172A`), Panels (`#1E293B`), ánh vàng Metallic Gold (`#F59E0B`), độ cao dòng bảng 36px và bo góc mềm mại 10-12px.
2. **Khóa mạng tĩnh Netplan (`192.168.56.102`):** Giải quyết triệt để vấn đề mất kết nối cơ sở dữ liệu khi máy chủ Windows đổi mạng Wi-Fi, đảm bảo ứng dụng Desktop Swing luôn kết nối thông suốt với cơ sở dữ liệu trên máy ảo.
3. **Chống đóng băng nút bấm UI (Button Freeze Prevention):** Toàn bộ các thao tác gửi yêu cầu (Đăng nhập, Đăng ký, Đổi mật khẩu, Cập nhật thông tin, Yêu cầu dịch vụ, Hủy đặt phòng) đều được bọc trong khối `try...catch...finally`, đảm bảo nút bấm luôn được mở khóa và phản hồi rõ ràng ngay cả khi có sự cố mạng.
4. **Đồng bộ nhị phân 100% giữa môi trường Dev và Production:** Cơ chế kiểm tra nhị phân `fc.exe /b` xác nhận không có bất kỳ sự sai khác nào giữa mã nguồn máy chủ cục bộ và mã nguồn đóng gói Nginx trên máy ảo.

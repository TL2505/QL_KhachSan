# QUY CHUẨN GIAO DIỆN: WEB QUẢN TRỊ & TỔNG GIÁM ĐỐC (REACT 18 + MUI 5.X — PHASE 4)

## 1. NỀN TẢNG THIẾT KẾ
- **Framework:** React 18 + Material-UI (MUI) `5.x` + TypeScript (Thống nhất 1 design system duy nhất với Guest Web; triển khai ở Phase 4 trong khi Phase 1–3 dùng Swing `AdminForm.java`).
- **Đối tượng sử dụng:** Tổng giám đốc (GM), Giám đốc khối vận hành, Quản trị viên hệ thống.
- **Triết lý:** Mật độ thông tin cao, dễ quét thông số, báo cáo chuyên sâu, tương thích mọi thiết bị (laptop, tablet, phone).
- **Quy chuẩn Token:** Màu sắc, typography, trạng thái phòng và khoảng cách tham chiếu từ `ui-tokens.md` (tái sử dụng `theme.ts` & `ApiClient.ts`).


## 2. ĐIỀU HƯỚNG VÀ BỐ CỤC KHUNG LÀM VIỆC
- **Bố cục máy tính (> 1200px):**
  - Thanh bên trái: Rộng `240px` (có thể thu gọn thành `64px` dạng biểu tượng).
  - Thanh điều hướng trên cùng: Cao `56px`, đổ bóng elevation-l1. Ô tìm kiếm toàn cục bên trái; chuông thông báo và ảnh đại diện bên phải.
  - Vùng nội dung chính: Đệm trong `24px`, nền `#FAFAFA` (hoặc `#1E1E1E` ở chế độ tối).
- **Menu thanh bên:**
  - Cao `40px`, biểu tượng `20px` + chữ `14px`.
  - Mục đang chọn: Nền màu accent độ trong suốt 10% + viền trái đậm `3px` màu accent.
  - Các mục chính: Bảng điều khiển (Dashboard), Phòng, Đặt phòng, Doanh thu, Nhân sự, Giám sát AI, Cài đặt.
- **Chế độ thu gọn (`64px`):** Căn giữa biểu tượng, hiển thị tooltip khi rê chuột.

## 3. MÀN HÌNH BẢNG ĐIỀU KHIỂN CHÍNH (ANALYTICS)
- **Hàng 1 — 4 Thẻ KPI (Chia đều chiều rộng):**
  - Thông số: Nhãn danh mục (`12px`, `#757575`), Giá trị KPI (`32px`, đậm `700`).
  - Xu hướng: Mũi tên xanh tăng (`#2E7D32`) hoặc đỏ giảm (`#C62828`) (`12px`).
  - Bộ lọc kỳ: Công tắc chọn nhanh (7 ngày / 30 ngày / 90 ngày) ở góc trên bên phải.
- **Hàng 2 — Lưới biểu đồ (Chiều cao cố định `280px`):**
  - Chiếm 60% chiều rộng: Biểu đồ đường Line Chart (Xu hướng Công suất phòng & RevPAR).
  - Chiếm 40% chiều rộng: Biểu đồ tròn Donut Chart (Tỷ trọng Kênh bán & Phân khúc).
- **Hàng 3 — Bảng danh sách Đặt phòng gần đây:**
  - Hiển thị 10 dòng. Các cột: Tên khách, Phòng, Ngày đến, Ngày đi, Nhãn trạng thái, Tổng tiền.

## 4. BẢNG DỮ LIỆU VÀ NGĂN KÉO CHI TIẾT (DRAWER)
- **Kích thước hàng:** Cao chính xác `40px` mỗi dòng (Bảng mật độ cao MUI Table / DataGrid compact mode).
- **Cỡ chữ:** `14px` cho nội dung chính, `12px` cho nhãn phụ và mã định danh.
- **Điều khiển:** Biểu tượng mũi tên sắp xếp trên tiêu đề; thanh lọc trên đầu bảng gồm ô tìm kiếm và bộ lọc chọn nhiều; nút "Xuất CSV" và "Xuất PDF" góc trên bên phải.
- **Phân trang:** Tùy chọn 20 / 50 / 100 bản ghi trên mỗi trang.
- **Ngăn kéo chi tiết:** Nhấp vào dòng mở ngăn kéo trượt ra từ bên phải (`400px`, thời gian `200ms`) hiển thị toàn bộ chi tiết đặt phòng và nhật ký hoạt động.

## 5. THƯ VIỆN BIỂU ĐỒ (RECHARTS 2.X)
- **Các dạng biểu đồ:** Biểu đồ đường (xu hướng), Cột (so sánh kênh), Tròn (phân khúc khách).
- **Ràng buộc:** Tối đa 3 biểu đồ trên 1 trang. Chiều cao cố định `280px`.
- **Bảng màu:** Dùng màu accent `#1B2A4A` kết hợp với các màu phụ trong `ui-tokens.md`.
- **Tương tác:** Hiển thị ô tooltip ngay lập tức khi rê chuột với số lượng tuyệt đối và phần trăm. Không dùng hiệu ứng vẽ chuyển động lúc tải trang.

## 6. BIỂU MẪU VÀ BỐ CỤC FORM
- **Cấu trúc:** Biểu mẫu 2 cột (nhãn rộng `120px` bên trái, ô nhập liệu bên phải).
- **Nhóm thẻ:** Đặt trong các thẻ card trắng đệm `16px`, viền `1px solid #E0E0E0`, bo góc `8px`.
- **Tiêu đề phân đoạn:** Chữ `16px` độ đậm `600`, lề dưới `16px`.
- **Nút hành động:** Nút Lưu ở góc dưới bên phải (nền màu accent); nút Hủy kế bên (viền xám).

## 7. TƯƠNG THÍCH THIẾT BỊ VÀ CHỦ ĐỀ
- **Máy tính bảng (768px – 1200px):** Thanh bên tự động thu gọn về `64px`; biểu đồ xếp dọc; bảng dữ liệu cho phép cuộn ngang.
- **Điện thoại (< 768px):** Thanh bên ẩn vào menu hamburger góc trên; thẻ KPI chia 2 thẻ/hàng; bảng dữ liệu chuyển thành dạng thẻ thông tin (1 thẻ/bản ghi).
- **Chế độ ban đêm (Dark Mode):** Tùy chọn chuyển đổi trong menu người dùng (dùng MUI `ThemeProvider` với `palette.mode = 'dark'`). Lưu cấu hình vào `localStorage`.
- **Khung thông báo:** Rộng `320px` khi bấm chuông thông báo. Mục chưa đọc có chấm tròn accent và chữ đậm.

## 8. NGUYÊN TẮC BẮT BUỘC (DO / DON'T)
- **NÊN (DO):** Luôn có bộ chọn khoảng ngày trên báo cáo. Cung cấp tính năng xuất CSV/PDF. Hỗ trợ phím Tab chuyển trường.
- **KHÔNG NÊN (DON'T):** Không quá 3 biểu đồ trên một màn hình. Không dùng hiệu ứng chuyển động > `200ms`. Không tạo bảng dữ liệu thiếu phân trang.

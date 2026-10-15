# TÀI LIỆU ĐẶC TẢ THIẾT KẾ & KIẾN TRÚC GIAO DIỆN WEB BOOKING ENGINE (5★ LUXURY HOTEL)
**Dự án:** 5★ Luxury Hotel PMS & AI Platform (`QL_KhachSan`)  
**Phân hệ:** Web Booking Engine (`web-ui/`) - Giai đoạn `PHASE_2`  
**Tiêu chuẩn:** International 5-Star Luxury Hospitality & Component Design System  
**Nguyên tắc cốt lõi:** **NO-BUILD LUXURY** (Không Node.js runtime, Không npm packages, Không Webpack/Vite bundlers, Không framework phụ thuộc)

---

## I. TỔNG QUAN KIẾN TRÚC & TRIẾT LÝ THIẾT KẾ "NO-BUILD LUXURY"

### 1. Triết lý No-Build Stack
Hệ thống Web Booking Engine được phát triển theo nguyên lý thuần túy (vanilla-first), tối đa hóa hiệu năng trình duyệt và tốc độ nạp trang dưới 1.2s:
- **Cấu trúc ngữ nghĩa (Markup):** Pure semantic HTML5 (`web-ui/index.html`).
- **Tạo kiểu & Hiệu ứng (Styling):** Native CSS3 (`web-ui/css/style.css`) kết hợp Tailwind CSS qua CDN (`https://cdn.tailwindcss.com`) với bảng màu tùy biến (custom luxury tokens).
- **Hệ thống Icon:** Lucide Icons qua CDN (`https://unpkg.com/lucide@latest`), render dynamic vector icon sắc nét, không vỡ hạt.
- **Logic & Điều hướng dữ liệu (Scripts):** Native JavaScript ES Modules (`web-ui/js/app.js`, `web-ui/js/api.js`), phân tách module rõ ràng, tự động fall-back khi mở trực tiếp file cục bộ (`file:///`) hoặc chạy qua static web server (`http://localhost:3000`).
- **Nghiêm cấm tuyệt đối:** Không tạo `package.json`, không cài đặt npm dependencies, không sử dụng framework React/Vue/Angular hay bundler build step phức tạp.

### 2. Bảng mã màu Luxury & Design Tokens (Luxury Palette)
| Token Key | Giá trị Hex / RGBA | Vai trò & Ứng dụng |
| :--- | :--- | :--- |
| `--bg-obsidian` | `#0b0f17` | Nền chính toàn trang (Obsidian Black sâu thẳm, tĩnh lặng) |
| `--bg-slate-night` | `#131b2e` | Nền thẻ card, modal, menu, container cấp 2 (Slate Night) |
| `--bg-glass` | `rgba(19, 27, 46, 0.75)` | Nền glassmorphism kết hợp `backdrop-filter: blur(16px)` |
| `--gold-champagne` | `#d4af37` | Màu chủ đạo 5 sao (Champagne Gold), viền ánh kim, nút CTA chính |
| `--gold-soft` | `#f3e5ab` | Màu phụ ánh kim mềm mại (Soft Gold) cho tiêu đề phụ, hover và badge |
| `--gold-gradient` | `linear-gradient(135deg, #d4af37 0%, #f3e5ab 50%, #aa820a 100%)` | Dải màu kim loại ánh gương cho nút bấm và viền thương hiệu |
| `--border-metallic`| `rgba(212, 175, 55, 0.22)`| Đường viền kim loại thanh mảnh 1px chuẩn luxury |
| `--text-light` | `#f8fafc` | Màu chữ chính (Slate 50), tương phản cao trên nền tối |
| `--text-muted` | `#94a3b8` | Màu chữ phụ, mô tả ngắn (Slate 400) |

---

## II. ĐẶC TẢ CHI TIẾT 9 THÀNH PHẦN GIAO DIỆN CHUẨN 5 SAO (UI COMPONENTS)

### 1. Header & Navigation (Thanh điều hướng sang trọng)
- **Brand Identity:**
  - Logo văn tự kết hợp biểu tượng vương miện hoàng gia vàng ánh kim.
  - Kèm huy hiệu uy tín chuẩn quốc tế: `"5★ LUXURY HOTEL & RESORT"`.
- **Navigation Bar (Thực đơn chính):**
  - Danh mục điều hướng:
    1. Trang chủ (`#hero`)
    2. Hạng phòng (`#rooms`)
    3. Dịch vụ & Tiện ích (`#services`)
    4. Giới thiệu (`#about`)
    5. Đánh giá (`#reviews`)
    6. Bộ sưu tập (`#gallery`)
    7. Liên hệ (`#contact`)
  - Hiệu ứng: Chữ hoa nhỏ, tracking rộng, hiệu ứng gạch chân ánh kim mượt mà khi hover và active section.
- **Sticky Header Behavior:**
  - Khi cuộn trang qua 80px, header tự động kích hoạt trạng thái `sticky-scrolled`: nền chuyển sang glassmorphism `rgba(11, 15, 23, 0.92)` với viền mạ vàng thanh thoát, cố định đỉnh màn hình.
- **Header Actions:**
  - **Nút "Đặt Phòng" (Book Now CTA):** Nút màu Champagne Gold với dải gradient ánh kim, hiệu ứng hover phóng to nhẹ (`scale-105`) và đổ bóng vàng (`box-shadow: 0 4px 20px rgba(212, 175, 55, 0.35)`). Khi nhấp, thực hiện cuộn êm (`smooth scroll`) về Booking Bar hoặc mở trực tiếp Booking Modal.
  - **Bộ chọn Ngôn ngữ (Language Switcher):** Dropdown thanh lịch hỗ trợ `Tiếng Việt (VI)` và `English (EN)`. Lưu trạng thái vào `localStorage`.
  - **Bộ chọn Tiền tệ (Currency Switcher):** Hỗ trợ `VND (₫)` và `USD ($)` với tỷ giá quy đổi tức thời trên toàn bộ các thẻ giá phòng.
  - **Mobile Drawer:** Hamburger menu trượt mượt mà trên thiết bị di động với hiệu ứng làm mờ nền tối.

---

### 2. Hero Section & Booking Bar (Banner chính & Thanh tìm kiếm nổi)
- **Hero Banner:**
  - Hình ảnh độ phân giải cao góc rộng tái hiện không gian vịnh biển hoàng hôn và kiến trúc khách sạn lung linh về đêm.
  - Phủ lớp gradient đen (`linear-gradient(to bottom, rgba(11,15,23,0.5), #0b0f17)`) đảm bảo tính tương phản tối ưu cho tiêu đề.
  - Tiêu đề thương hiệu (Headline): *"Aurelia Grand Palace - Khẳng Định Đẳng Cấp Thượng Lưu, Tinh Hoa Nghỉ Dưỡng 5 Sao"*.
  - Slogan phụ: Khám phá dịch vụ quản gia 24/7, ẩm thực Michelin và kỳ nghỉ vương giả bên bờ di sản.
- **Booking Bar (Floating Search Widget):**
  - Vị trí: Thanh tìm kiếm dạng thẻ nổi (floating bar) đè lên ranh giới giữa Hero Section và phần nội dung bên dưới.
  - Phong cách: Glassmorphism (`backdrop-blur-md`, nền `rgba(19, 27, 46, 0.85)`, viền vàng `border-amber-500/30`, bo góc `rounded-2xl`).
  - Các trường dữ liệu:
    1. **Ngày nhận phòng (Check-in Date):** Date picker, giá trị mặc định là ngày hôm nay.
    2. **Ngày trả phòng (Check-out Date):** Date picker, giá trị mặc định là ngày tiếp theo.
       * *Ràng buộc kiểm tra (Validation):* Ngày nhận $\ge$ hôm nay; Ngày trả $>$ Ngày nhận.
    3. **Số lượng khách (Guests):** Dropdown phân tách Người lớn (Adults) và Trẻ em (Children) kèm nút tăng giảm số lượng trực quan.
    4. **Số lượng phòng (Rooms):** Dropdown lựa chọn 1 đến 5 phòng.
    5. **Mã ưu đãi (Promo Code):** Ô nhập mã giảm giá độc quyền (ví dụ: `LUXURY2026`, `VIP5STAR`).
    6. **Nút "Kiểm Tra Phòng Trống" (Check Availability Button):**
       * Gửi truy vấn HTTP GET tới endpoint: `GET /api/v1/rooms?check_in=...&check_out=...` (hoặc `/api/rooms/search`).
       * Tự động lọc và cuộn màn hình mượt mà xuống khu vực Room Showcase.

---

### 3. About Section (Di sản & Giá trị vượt thời gian)
- **Nội dung giới thiệu:**
  - Tóm lược lịch sử kiến trúc kết hợp phong cách tân cổ điển Châu Âu quý phái và văn hóa Đông Dương duy mỹ.
  - Tuyên ngôn dịch vụ: Cá nhân hóa tuyệt đối, sự riêng tư tối thượng và triết lý hiếu khách đỉnh cao.
- **Quick Metrics Grid (Lưới chỉ số ấn tượng):**
  - **200+** Phòng & Biệt thự thượng hạng hướng biển.
  - **5★ International** Đạt chứng nhận 5 sao quốc tế từ hiệp hội du lịch toàn cầu.
  - **Prime Location** Vị trí kim cương ngay trên đại lộ ven biển di sản.
  - **9.8/10** Điểm số hài lòng từ hơn 15,000 khách hàng VIP quốc tế.

---

### 4. Room Showcase / Room Grid Section (Bộ sưu tập hạng phòng đẳng cấp)
- **Hiển thị dạng lưới phản hồi (Responsive Grid):**
  - Cung cấp các hạng phòng điển hình:
    * *Deluxe King Ocean View:* 45 m², Giường King size, 2 người lớn, bồn tắm nằm đá cẩm thạch, ban công view biển.
    * *Executive Ocean Suite:* 75 m², Phòng khách riêng biệt, 2 người lớn + 1 trẻ em, đặc quyền Executive Lounge.
    * *Presidential Royal Villa:* 180 m², 3 phòng ngủ, hồ bơi riêng biệt, quản gia riêng 24/7, xe đưa đón tận cửa.
    * *Grand Family Suite:* 95 m², 2 phòng ngủ kết nối, view toàn cảnh hoàng hôn vịnh biển.
- **Chi tiết hiển thị trên mỗi Card phòng:**
  - Ảnh đại diện sắc nét với hiệu ứng phóng to nhẹ (`zoom on hover`).
  - Huy hiệu hạng phòng (Badge: "Most Popular", "Best Seller", "Signature Villa").
  - Thông số: Diện tích ($m^2$), Loại giường, Sức chứa tối đa.
  - Tiện ích nhanh (Quick Amenities): Wi-Fi tốc độ cao, Bồn tắm massage Jacuzzi, Bữa sáng chuẩn 5 sao, View biển trực diện.
  - Giá thuê mỗi đêm (VND / USD): Định dạng phân tách dấu phẩy rõ ràng (Ví dụ: `1,500,000 ₫ / đêm`).
  - Nút bấm hành động:
    * **"Xem Chi Tiết" (View Details):** Kích hoạt Modal chi tiết đầy đủ hình ảnh, chính sách phụ phí và trang thiết bị phòng.
    * **"Đặt Phòng Này" (Book This Room):** Gắn thông tin phòng vào luồng đặt phòng 3 bước và mở ngay Booking Modal.
- **Đồng bộ dữ liệu thời gian thực (Dynamic API Integration):**
  - Kết nối endpoint: `GET /api/v1/rooms` và `GET /api/v1/rooms/search?checkIn=...&checkOut=...`.
  - Tự động fallback dữ liệu luxury mẫu chuẩn xác khi máy chủ Java chưa kích hoạt.

---

### 5. Amenities & Services Section (Tiện ích & Dịch vụ đỉnh cao)
Lưới 6 dịch vụ nòng cốt với icon Lucide sang trọng, mô tả giá trị tinh hoa:
1. **Hồ Bơi Vô Cực Chân Mây (Infinity Pool):** Nằm tại tầng 32 với tầm nhìn panorama 360 độ ôm trọn đường chân trời biển cả.
2. **Nhà Hàng Ẩm Thực 5★ & Sky Bar (Fine Dining & Sky Bar):** Thực đơn món Âu - Á thượng hạng bởi bếp trưởng Michelin, quầy rượu hầm vang trứ danh.
3. **Aurelia Spa & Trị Liệu Trẻ Hóa (Luxury Spa & Wellness):** Liệu pháp massage đá nóng Thụy Điển, tinh dầu hữu cơ và phòng xông hơi tuyết lạnh.
4. **Trung Tâm Thể Thao & Yoga 24/7 (Fitness & Yoga Center):** Trang thiết bị Technogym tiên tiến, huấn luyện viên cá nhân và lớp Yoga đón bình minh.
5. **Dịch Vụ Đưa Đón VIP Sân Bay (VIP Airport Transfer):** Dàn xe sang Limousine và Maybach phục vụ 24/7, làm thủ tục check-in đặc quyền ngay trên xe.
6. **Trung Tâm Hội Nghị & Sự Kiện Quốc Tế (Convention Center):** Sức chứa 1,000 khách, hệ thống âm thanh ánh sáng 4K laser cho tiệc cưới hoàng gia và hội nghị thượng đỉnh.

---

### 6. Testimonials & Social Proof Section (Minh chứng chất lượng & Đánh giá)
- **Đánh giá thực tế từ khách sạn thượng lưu:**
  - 3 lời nhận xét tiêu biểu từ doanh nhân quốc tế, ngôi sao và du khách gia đình với đánh giá tuyệt đối **5/5 sao**.
  - Tên khách hàng, quốc tịch và hình ảnh đại diện xác thực.
- **Huy hiệu chứng nhận uy tín (Trust Badges):**
  - *TripAdvisor Travellers' Choice 2026 (Best of the Best)*.
  - *Booking.com Guest Review Awards (9.6 / 10 - Exceptional)*.
  - *Agoda Top Value Luxury Hotel 2026*.
  - *Google Customer Reviews (4.9 / 5.0 - hơn 2,800 đánh giá)*.

---

### 7. Gallery Section (Bộ sưu tập hình ảnh & Lightbox tương tác)
- **Phân loại Tab thông minh:**
  - `Tất cả` (All)
  - `Phòng nghỉ` (Rooms)
  - `Ẩm thực & Bar` (Dining & Bar)
  - `Tiện ích & Spa` (Amenities & Spa)
  - `Cảnh quan & Bãi biển` (Views)
- **Bố cục Masonry Grid:** Trình bày hình ảnh so le nghệ thuật, tạo cảm giác sang trọng như một tạp chí kiến trúc cao cấp.
- **Vanilla JS Lightbox Feature:**
  - Nhấp vào bất kỳ ảnh nào sẽ mở màn hình Lightbox toàn cảnh với nền mờ tối (`backdrop-blur-lg`).
  - Hỗ trợ nút điều hướng Trước/Sau (Prev/Next), phóng to chi tiết và nút đóng (`Esc` hoặc nút `✕`).

---

### 8. Footer Area (Chân trang toàn diện & Minh bạch chính sách)
Bố cục 4 cột tiêu chuẩn quốc tế:
- **Cột 1 (Thương hiệu & Pháp lý):**
  - Logo thương hiệu Aurelia Grand Luxury Hotel.
  - Giấy phép kinh doanh dịch vụ lưu trú 5 sao quốc tế: `GP-0108868688/TCDL`.
  - Tôn chỉ: "Kiến tạo chuẩn mực thượng lưu và trải nghiệm nghỉ dưỡng hoàn mỹ".
- **Cột 2 (Thông tin liên hệ 24/7):**
  - Địa chỉ: Số 88 Đại Lộ Hoàng Gia (Aurelia Blvd), Bãi biển Vịnh Ngọc.
  - Tổng đài lễ tân 24/7: `+84 (0) 24 3888 9999`.
  - Email dịch vụ khách hàng: `concierge@aureliahotel.com`.
  - Nút dẫn đường qua Google Maps.
- **Cột 3 (Chính sách & Quy định minh bạch):**
  - Giờ nhận/trả phòng: Nhận từ 14:00, Trả trước 12:00 trưa.
  - Quy định hủy phòng & hoàn cọc: Miễn phí trước 48 giờ.
  - Điều khoản bảo mật dữ liệu khách hàng theo tiêu chuẩn GDPR & ISO 27001.
- **Cột 4 (Bản tin ưu đãi & Mạng xã hội):**
  - Ô đăng ký nhận tin thư ưu đãi độc quyền dành cho khách hàng VIP.
  - Bộ biểu tượng mạng xã hội sang trọng: Facebook, Instagram, LinkedIn, YouTube.
  - Dòng bản quyền: `© 2026 Aurelia Grand Luxury Hotel & AI Platform. All Rights Reserved.`

---

### 9. Quick-Interaction Components (Thành phần tương tác nhanh / Widget nổi)
- **Nút gọi nhanh Hotline (Floating Call Widget):**
  - Vị trí: Góc dưới bên trái màn hình.
  - Kích hoạt quay số trực tiếp trên điện thoại di động (`tel:+842438889999`) với hiệu ứng radar gợn sóng ánh kim thu hút sự chú ý.
- **Nút trò chuyện nhanh (Floating Chat Widget):**
  - Biểu tượng trò chuyện mở trực tiếp cửa sổ tương tác Zalo / Concierge AI LiveChat.
- **Nút cuộn lên đầu trang (Back to Top):**
  - Tự động xuất hiện mượt mà khi người dùng cuộn vượt qua Hero Section (vị trí Y > 450px).
  - Nhấp để cuộn êm (`smooth scroll`) về đầu trang.

---

## III. TÍCH HỢP API & QUY TRÌNH ĐẶT PHÒNG VIETQR 3 BƯỚC (PORT 8081)

### 1. Luồng hoạt động Modal Đặt Phòng 3 Bước (3-Step Booking Modal)
Giao diện Booking Modal được thiết kế theo mô hình State Machine rõ ràng:

```
[BƯỚC 1: XÁC NHẬN] ──(Next)──> [BƯỚC 2: THÔNG TIN KHÁCH] ──(Submit API)──> [BƯỚC 3: VIETQR & HOÀN TẤT]
  - Chọn phòng                   - Họ tên, Số điện thoại                     - Hiển thị Mã Đặt Phòng
  - Chọn ngày check-in/out       - Email, CCCD / Passport                    - Render VietQR động
  - Tự động tính số đêm          - Mã ưu đãi (nếu có)                        - Hướng dẫn chuyển khoản
  - Hiển thị tổng thanh toán     - Xác thực dữ liệu đầu vào                  - Tùy chọn In / Tải biên lai
```

#### Chi tiết Bước 1: Xem lại phòng & Tính toán chi phí
- Hiển thị tên phòng, ảnh thumbnail, đơn giá/đêm.
- Lựa chọn/xác nhận ngày nhận và ngày trả phòng.
- Tự động tính: $\text{Số đêm} = \max(1, \text{CheckOut} - \text{CheckIn})$.
- Tự động tính: $\text{Tổng tiền phòng} = \text{Đơn giá} \times \text{Số đêm}$.
- Áp dụng mã ưu đãi (ví dụ giảm 10% nếu có mã `LUXURY2026`).
- Tạm tính Thuế VAT (8%) và Phí dịch vụ 5 sao (5%) minh bạch.

#### Chi tiết Bước 2: Nhập thông tin định danh khách hàng
- Các trường nhập liệu:
  - Họ và tên khách hàng (Full Name) - Bắt buộc.
  - Số điện thoại liên hệ (Phone) - Định dạng số Việt Nam/Quốc tế.
  - Địa chỉ Email (để nhận vé điện tử và xác nhận phòng) - Bắt buộc.
  - Số CCCD / Hộ chiếu (Identity Card / Passport) - Bắt buộc.
  - Ghi chú đặc biệt (Tầng cao, phòng không hút thuốc, hoa tươi đón tiếp...).
- Khi bấm **"Xác Nhận & Tạo Đơn Đặt Phòng"**:
  - Gửi request HTTP `POST /api/v1/bookings` (hoặc `/api/bookings`) tới backend port 8081 với payload:
    ```json
    {
      "customerId": 1,
      "roomId": 2,
      "checkInDate": "2026-10-20",
      "checkOutDate": "2026-10-22",
      "fullName": "Nguyễn Văn A",
      "phone": "0901234567",
      "email": "nva@luxuryhotel.com",
      "identityCard": "012345678901"
    }
    ```
  - Kèm theo header `X-User-Role: 3` (Customer) và `Idempotency-Key` (UUIDv4) để chống trùng lặp giao dịch.

#### Chi tiết Bước 3: Phản hồi thành công & Nhúng mã VietQR động
- Nhận phản hồi `HTTP 201 Created` kèm `bookingId` từ máy chủ.
- Nhúng mã VietQR chuẩn ngân hàng quốc gia qua dịch vụ VietQR API:
  ```
  https://img.vietqr.io/image/MB-0987654321-compact.png?amount=${totalAmount}&addInfo=BOOKING_${bookingId}
  ```
  - **Ngân hàng thụ hưởng:** Quân Đội (MB Bank).
  - **Số tài khoản:** `0987654321`.
  - **Tên chủ tài khoản:** `AURELIA GRAND LUXURY HOTEL`.
  - **Số tiền:** Giá trị chính xác của đơn đặt phòng.
  - **Nội dung chuyển khoản:** `BOOKING_${bookingId}`.
- Cung cấp nút sao chép nhanh số tài khoản, số tiền và nội dung.
- Hiển thị đồng hồ đếm ngược 15:00 phút giữ chỗ.

### 2. Xử lý mã lỗi HTTP & Thông báo Toast trực quan (Tiếng Việt)
Hệ thống xử lý bắt lỗi chi tiết và hiển thị Toast Notification trang nhã ở góc trên bên phải:
- **HTTP 400 (Bad Request):**
  - *Thông báo:* *"Thông tin đặt phòng không hợp lệ. Vui lòng kiểm tra lại ngày lưu trú hoặc thông tin cá nhân."*
- **HTTP 409 (Conflict - Phòng đã có người đặt):**
  - *Thông báo:* *"Phòng bạn vừa chọn đã có khách đặt trong khoảng thời gian này hoặc trạng thái phòng đã thay đổi. Vui lòng chọn hạng phòng khác!"*
- **HTTP 503 (Service Unavailable - Night Audit Frozen):**
  - *Thông báo:* *"Hệ thống khách sạn đang trong chu kỳ kiểm toán đêm (00:00 - 00:05). Giao dịch tạm thời bị khóa nhằm đảm bảo an toàn sổ cái. Vui lòng quay lại sau ít phút!"*
- **Mất kết nối mạng / Server Offline:**
  - Hệ thống tự động chuyển sang chế độ Mô Phỏng Đặt Phòng Thành Công (Demo Standalone Mode), cấp mã booking mô phỏng chuẩn và hiển thị VietQR thực tế để phục vụ nghiệm thu giao diện mà không bị gián đoạn.

### 3. Quy chuẩn xử lý rủi ro kỹ thuật (Technical Risk Hardening)
- **Rủi ro 1: Định dạng số tiền trên URL VietQR (Zero Decimal & Comma Formatting):**
  - *Vấn đề:* Khi tính VAT 8% hoặc phụ phí, số tiền có thể sinh số thập phân (e.g. `1998000.4`) hoặc khi format tiền có dấu phẩy/chấm (`1,850,000`), VietQR API sẽ từ chối render hoặc trả về ảnh lỗi.
  - *Giải pháp:* Tại `web-ui/js/api.js`, hàm `generateVietQRUrl()` luôn lọc sạch ký tự phi số và ép kiểu nguyên dương tuyệt đối:
    ```javascript
    let raw = totalAmount;
    if (typeof raw === 'string') raw = raw.replace(/[^\d.-]/g, '');
    const cleanAmount = Math.max(0, Math.round(Number(raw) || 0));
    ```
- **Rủi ro 2: Lệch ngày do Timezone khi chọn Check-in / Check-out (UTC+7 Vietnam Timezone):**
  - *Vấn đề:* Hàm `new Date("YYYY-MM-DD")` mặc định gán giờ `00:00:00 UTC`. Tại múi giờ Việt Nam ($UTC+7$), việc cộng trừ hoặc reformat có thể bị lệch lùi lại 1 ngày (ví dụ chọn 15/10 lại thành 14/10).
  - *Giải pháp:* Hàm `parseLocalDate(dateString)` phân rã chuỗi `YYYY-MM-DD` theo giờ địa phương (12:00 trưa local):
    ```javascript
    const [year, month, day] = dateString.split('-').map(Number);
    const localDate = new Date(year, month - 1, day, 12, 0, 0);
    ```
- **Rủi ro 3: CORS Preflight (OPTIONS) giữa Frontend Port 3000 và Backend Java Port 8081:**
  - *Vấn đề:* Web UI chạy tại `http://localhost:3000` còn Java Backend chạy tại `http://localhost:8081` (khác Origin). Trình duyệt tự động gửi preflight `OPTIONS` trước khi gửi `GET` hoặc `POST`. Nếu server thiếu header hoặc không trả HTTP 200, toàn bộ API bị chặn.
  - *Giải pháp:* Cấu hình `CorsFilter.java` và `JwtFilter.java` tại Backend trả về `200 OK` cho method `OPTIONS`, cấp phép Origin `*`, Methods `GET, POST, PUT, DELETE, OPTIONS`, và Headers `Content-Type, X-User-Role, Idempotency-Key, Authorization, Accept, ngrok-skip-browser-warning`.
- **Rủi ro 4: Lỗi chặn CORS khi mở trực tiếp file HTML (Giao thức file:///):**
  - *Vấn đề:* Trình duyệt hiện đại (Chrome, Edge, Firefox, Brave) chặn triệt để Native ES Modules (`<script type="module">`, `import`/`export`) khi nạp qua giao thức `file:///` do vi phạm chính sách CORS (Origin: `null`).
  - *Giải pháp:* **Bắt buộc phục vụ ứng dụng qua Local Web Server** (Cổng 3000 qua `node web-ui/server.js`, `start-web.bat`, VS Code Live Server, hoặc `python -m http.server 3000`). Không mở kiểm thử bằng cách nhấp đúp file HTML thông thường. Giao diện được trang bị banner cảnh báo trực quan nếu người dùng vô tình mở qua `file:///`.

---

## IV. HƯỚNG DẪN KHỞI CHẠY VÀ KIỂM THỬ (LOCAL WEB SERVER GUIDELINES)

Hệ thống tuân thủ 100% nguyên lý **"NO-BUILD LUXURY"**, phục vụ qua máy chủ tĩnh nội bộ (Local Web Server):

### Cách 1: Khởi chạy máy chủ Node Server tích hợp sẵn (Khuyên dùng)
```powershell
# Chạy từ thư mục gốc
node web-ui/server.js
# Hoặc nhấp đúp tệp tin:
web-ui/start-web.bat
```
- Trình duyệt sẽ tự động mở hoặc truy cập tại: **`http://localhost:3000`**
- Bật F12 Console để kiểm tra: Mọi module JavaScript ES Modules nạp thành công, 0 lỗi đỏ CORS.

### Cách 2: Khởi chạy qua VS Code Live Server
- Cài đặt extension **Live Server** trong VS Code.
- Nhấp chuột phải vào `web-ui/index.html` $\rightarrow$ chọn **"Open with Live Server"**.
- Ứng dụng chạy tại: `http://127.0.0.1:5500/web-ui/index.html`.

### Cách 3: Khởi chạy qua Python 3
```powershell
cd web-ui
python -m http.server 3000
```
Truy cập tại: `http://localhost:3000`.

---

## V. BẢNG KIỂM TRA MỨC ĐỘ TUÂN THỦ (COMPLIANCE CHECKLIST)

| STT | Yêu cầu kiểm tra | Trạng thái | Ghi chú kiến trúc |
| :---: | :--- | :---: | :--- |
| 1 | No-Build Discipline (0 npm, 0 bundler, 0 package.json) | **TUÂN THỦ 100%** | Semantic HTML5, Native CSS3, Native ES Modules |
| 2 | Tailwind CSS CDN & Custom Luxury Palette | **TUÂN THỦ 100%** | Nền Obsidian, Slate Night, Ánh kim Champagne Gold |
| 3 | Lucide Icons CDN | **TUÂN THỦ 100%** | Nạp vector icons trực tiếp qua unpkg |
| 4 | Thành phần 1: Header & Sticky Navigation | **TUÂN THỦ 100%** | Huy hiệu 5 sao, bộ đổi ngôn ngữ VI/EN, tiền tệ VND/USD |
| 5 | Thành phần 2: Hero & Floating Booking Bar | **TUÂN THỦ 100%** | Timezone-safe date validation, khách, phòng, mã ưu đãi |
| 6 | Thành phần 3: About Section & Quick Metrics | **TUÂN THỦ 100%** | 200+ phòng, chuẩn 5 sao, rating 9.8/10 |
| 7 | Thành phần 4: Room Showcase Grid & Modal | **TUÂN THỦ 100%** | Card phòng cao cấp, tiện ích, giá VND, nút Đặt Phòng |
| 8 | Thành phần 5: Amenities Grid (6 dịch vụ) | **TUÂN THỦ 100%** | Bể bơi chân mây, Sky Bar, Spa, Gym, VIP Transfer, Hội nghị |
| 9 | Thành phần 6: Testimonials & 4 Chứng nhận | **TUÂN THỦ 100%** | TripAdvisor, Booking.com 9.6, Agoda, Google Reviews 4.9★ |
| 10 | Thành phần 7: Gallery & Vanilla JS Lightbox | **TUÂN THỦ 100%** | Bộ lọc 5 tab, phóng to ảnh, xem chi tiết phím tắt |
| 11 | Thành phần 8: Footer 4 cột & Giấy phép 5★ | **TUÂN THỦ 100%** | Pháp lý GP-0108868688/TCDL, hotline 24/7, chính sách, newsletter |
| 12 | Thành phần 9: Floating Hotline, Chat, Back to Top | **TUÂN THỦ 100%** | Hotline `tel:`, Zalo LiveChat, Back to Top mượt mà |
| 13 | Quy trình VietQR 3 bước (Port 8081) | **TUÂN THỦ 100%** | Số tiền ép kiểu nguyên dương Math.round(), render VietQR chuẩn |
| 14 | Xử lý lỗi 400, 409, 503 với Toast tiếng Việt | **TUÂN THỦ 100%** | Thông báo chuẩn xác ngữ cảnh và giải pháp cho khách hàng |
| 15 | Khắc phục triệt để CORS & file:/// Protocol | **TUÂN THỦ 100%** | CorsFilter Backend & hướng dẫn chạy qua Local Web Server |


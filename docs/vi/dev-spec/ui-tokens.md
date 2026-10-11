# CÁC DESIGN TOKEN GIAO DIỆN CHUNG (UI DESIGN TOKENS)

## 1. BẢNG MÀU CHUẨN (COLOR PALETTE)
| Token | Giá trị Hex | Mục đích sử dụng |
|---|---|---|
| `color-primary-accent` | `#1B2A4A` | Màu xanh navy chủ đạo, nút bấm chính, thanh tiêu đề |
| `color-success` | `#2E7D32` | Trạng thái thành công, xác nhận, xu hướng tăng trưởng |
| `color-warning` | `#F57C00` | Cảnh báo, trạng thái chờ xử lý, lưu ý |
| `color-error` | `#C62828` | Lỗi nghiêm trọng, xác thực thất bại, hành động hủy |
| `color-info` | `#1565C0` | Thông báo bổ sung, đường dẫn liên kết |
| `color-bg-light` | `#FAFAFA` | Màu nền mặc định toàn hệ thống (Giao diện Sáng) |
| `color-bg-dark` | `#1E1E1E` | Màu nền toàn hệ thống (Giao diện Tối, Giai đoạn 4+) |
| `color-text-primary` | `#212121` | Tiêu đề và nội dung văn bản chính độ tương phản cao |
| `color-text-secondary` | `#757575` | Nhãn phụ, thời gian, chú thích mờ |
| `color-border` | `#E0E0E0` | Đường phân cách, viền thẻ card, viền ô nhập liệu |

### Bảng màu Trạng thái Phòng (Room Status Colors)
| Mã trạng thái | Tên trạng thái | Màu Hex | Ý nghĩa nghiệp vụ |
|---|---|---|---|
| `VC` | Trống sạch (Vacant Clean) | `#4CAF50` | Phòng sẵn sàng đón khách nhận phòng |
| `VD` | Trống bẩn (Vacant Dirty) | `#FF9800` | Cần buồng phòng dọn dẹp |
| `OC` | Có khách - Sạch (Occupied Clean) | `#2196F3` | Khách đang ở, đã dọn phòng |
| `OD` | Có khách - Bẩn (Occupied Dirty) | `#FF5722` | Khách đang ở, cần phục vụ dọn |
| `OOO` | Hỏng hóc (Out of Order) | `#F44336` | Khóa phòng bảo trì / ngưng phục vụ |
| `RESERVED` | Đã đặt trước (Reserved) | `#9C27B0` | Phòng đã gán cho khách sắp đến |

## 2. QUY CHUẨN TYPOGRAPHY
- **Phông chữ:** `Inter, sans-serif` (Nền tảng Web), Phông chữ hệ thống mặc định (Java Swing).
- **Kích thước chữ (Chỉ 6 mức):** `12px`, `14px`, `16px`, `20px`, `24px`, `32px`.
- **Độ đậm chữ (Chỉ 4 mức):** `400` (Regular), `500` (Medium), `600` (Semi-bold), `700` (Bold).
- **Chiều cao dòng (Line height):** `1.5` cho văn bản thông thường; `1.2` cho tiêu đề.

## 3. HỆ THỐNG KHOẢNG CÁCH (Nhịp 4px Rhythm)
Mọi giá trị padding, margin, và gap BẮT BUỘC phải dùng một trong các số sau:
`4px`, `8px`, `12px`, `16px`, `24px`, `32px`, `48px`, `64px`. Nghiêm cấm dùng số lẻ.

## 4. BO GÓC (BORDER RADIUS)
- `radius-sm`: `4px` (Ô nhập liệu, nút bấm, nhãn bảng biểu)
- `radius-md`: `8px` (Thẻ card, cửa sổ modal, container nổi)
- `radius-lg`: `12px` (Thẻ nổi bật lớn, bong bóng tin nhắn chat)
- *Ràng buộc:* Không bao giờ vượt quá `12px` (không dùng nút hình viên thuốc trừ nút gửi chat).
- *Ngoại lệ:* Ô nhập liệu dạng viên thuốc (thanh chat input, prompt chips): tối đa `24px`.

## 5. ĐỘ NỔI VÀ ĐỔ BÓNG (ELEVATION & SHADOWS)
- `elevation-l1`: `0 1px 3px rgba(0,0,0,0.12)` (Thẻ phẳng, hiệu ứng di chuột)
- `elevation-l2`: `0 4px 6px rgba(0,0,0,0.12)` (Menu thả xuống, thanh công cụ nổi, modal)
- `elevation-l3`: `0 10px 20px rgba(0,0,0,0.15)` (Hộp thoại cảnh báo kích hoạt)

## 6. PHÂN LỚP THỨ TỰ (Z-INDEX)
- `z-dropdown`: `10`
- `z-modal`: `100`
- `z-toast`: `1000`
- `z-fullscreen`: `10000`

## 7. THỜI GIAN CHUYỂN ĐỘNG (ANIMATION DURATION)
- **Ứng dụng Nhân viên (Swing Desktop):** `0ms` (tức thì). Không chuyển động CSS. Giới hạn cứng tối đa `150ms` chỉ riêng cho đóng/mở modal.
- **Web Khách hàng (React Guest):** ≤ `300ms` (Mượt mà, hiệu ứng lật trang wizard).
- **Web Quản trị (React Admin):** ≤ `200ms` (Mở ngăn kéo drawer, mở rộng menu).

## 8. THƯ VIỆN GIAO DIỆN & BIỂU TƯỢNG (ICONS)
- **Nhân viên Desktop:** Java Swing với FlatLaf `3.4.1` (Chủ đề Sáng mặc định).
- **Khách hàng Web:** React 18 với MUI `5.x`.
- **Quản trị Web:** React 18 với Ant Design `5.x`.
- **Thư viện biểu đồ:** Recharts `2.x`.
- **Bộ biểu tượng:** Material Icons 24px (Web); Biểu tượng vector tích hợp FlatLaf 24px (Desktop).

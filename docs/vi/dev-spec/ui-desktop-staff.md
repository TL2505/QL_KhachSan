# QUY CHUẨN GIAO DIỆN: ỨNG DỤNG DESKTOP NHÂN VIÊN (JAVA SWING)

## 1. NỀN TẢNG THIẾT KẾ
- **Framework:** Java Swing + FlatLaf `3.4.1` (`FlatLightLaf.setup()`).
- **Đối tượng sử dụng:** Lễ tân, Buồng phòng, Nhà hàng F&B (Ca trực 8 tiếng, ngồi tại bàn làm việc).
- **Triết lý:** Tốc độ quan trọng hơn thẩm mỹ. Không mất trạng thái khi tải lại. Thao tác phím tắt tối đa.
- **Quy chuẩn Token:** Màu sắc, typography, trạng thái phòng và khoảng cách tham chiếu từ `ui-tokens.md`.

## 2. BỐ CỤC VÀ CỬA SỔ
- **Kích thước cửa sổ:** Tối thiểu `1280×720px`. Có thể co giãn.
- **Ghi nhớ cấu hình:** Lưu chiều rộng, chiều cao, tọa độ X, Y vào tệp `.properties` cục bộ khi thoát.
- **Thanh tiêu đề:** `Hotel PMS — [Tên vai trò]` (Ví dụ: `Hotel PMS — Front Desk Agent`).
- **Bố cục 3 bảng (3-Panel):**
  - Thanh điều hướng bên trái: Cố định `200px`.
  - Khung làm việc trung tâm: Co giãn linh hoạt (`100% - 500px`), hiển thị mật độ cao.
  - Bảng chi tiết bên phải: Cố định `300px` (có thể thu gọn).
- **Thanh công cụ trên cùng:** Chiều cao `48px`, chứa các nút tác vụ chính kèm phím tắt.
- **Thanh trạng thái dưới đáy:** Chiều cao `24px`, hiển thị tên người dùng, mạng và kiểm tra kết nối DB.
- **Đa màn hình:** Cửa sổ chính trên màn hình 1. Sơ đồ phòng có thể mở cửa sổ độc lập trên màn hình 2 và tự ghi nhớ vị trí.

## 3. PHÍM TẮT BẮT BUỘC (MANDATORY)
- `F1`: Mở trợ giúp theo ngữ cảnh
- `F2`: Mở hộp thoại Nhận phòng (Check-in)
- `F3`: Mở hộp thoại Trả phòng (Check-out)
- `F4`: Trỏ nhanh vào ô Tìm kiếm phòng
- `F5`: Làm mới dữ liệu hiện tại
- `F6`: Chọn phòng tiếp theo trong danh sách/sơ đồ
- `F7`: Chọn phòng phía trước trong danh sách/sơ đồ
- `F8`: Mở Hóa đơn/Folio của phòng/khách đang chọn
- `F9`: Tạo Đặt phòng mới
- `F10`: In màn hình/hóa đơn hiện tại
- `Ctrl + S`: Lưu bản ghi đang chỉnh sửa
- `Ctrl + Z`: Hoàn tác thao tác form vừa nhập
- `Ctrl + F`: Trỏ vào thanh lọc dữ liệu toàn cục
- `Ctrl + P`: In biểu mẫu
- `Esc`: Đóng modal / Hủy thao tác
- `Enter`: Xác nhận / Gửi biểu mẫu
- `Tab / Shift + Tab`: Di chuyển tới / lùi giữa các ô nhập liệu

## 4. BẢNG DỮ LIỆU MẬT ĐỘ CAO (TABLES)
- **Chiều cao dòng:** Chính xác `24px` (Chế độ dày đặc). Nghiêm cấm vượt quá `32px`.
- **Màu xen kẽ:** Dòng `N` = `#FFFFFF`, Dòng `N+1` = `#F5F5F5`.
- **Khả năng hiển thị:** Tối thiểu 15 dòng hiển thị không cần cuộn trên màn hình `1280×720px`.
- **Tương tác:** Sắp xếp theo cột khi nhấn tiêu đề. Thanh lọc trên đầu bảng gồm ô tìm kiếm và menu chọn trạng thái.
- **Chỉ báo trạng thái:** Vòng tròn màu `8px` kèm nhãn chữ (màu từ `ui-tokens.md`). Tuyệt đối không để chữ đơn thuần.

## 5. SƠ ĐỒ PHÒNG (ROOM MAP GRID)
- **Ma trận:** Lưới 5 cột × N hàng.
- **Kích thước mỗi ô:** Chính xác `80×60px`.
- **Nội dung ô phòng:**
  - Số phòng: Góc trên bên trái, chữ đậm `12px`.
  - Màu nền: Lấy từ mã màu trạng thái trong `ui-tokens.md` với độ phủ 80%.
  - Tên khách: Góc dưới cùng, chữ `12px` (chỉ hiển thị khi phòng ở trạng thái `OC`).
- **Sự kiện:** Nhấp 1 lần cập nhật bảng chi tiết bên phải (`300px`). Nhấp đúp mở ngay Hóa đơn/Folio (`F8`).

## 6. BIỂU MẪU VÀ Ô NHẬP LIỆU
- **Bố cục:** Biểu mẫu 1 cột dọc.
- **Nhãn:** Đặt phía trên ô nhập liệu (`14px`, text-secondary). Trường bắt buộc có dấu sao đỏ `*` phía trước.
- **Ô nhập liệu:** Chiều cao `32px`, viền `1px solid #E0E0E0`, bo góc `4px`.
- **Nút bấm:** Nút gửi ở góc dưới bên phải (cao `32px`, rộng tối thiểu `48px`, nền màu chủ đạo accent, chữ trắng). Nút Hủy kế bên (viền xám, nền trong suốt).

## 7. HỘP THOẠI, MODAL VÀ THÔNG BÁO
- **Cửa sổ Modal:** Căn giữa, chiều rộng tối đa `600px`, đổ bóng `elevation-l2`. Đầu trang có tiêu đề và nút đóng X. Chân trang có Hủy (trái) và Xác nhận (phải). Nền tối: `rgba(0,0,0,0.5)`.
- **Thông báo Toast:** Góc trên bên phải, rộng `300px`, tự tắt sau 5 giây. Viền trái dày `4px` (xanh=thành công, cam=cảnh báo, đỏ=lỗi). Biểu tượng + nội dung văn bản.
- **Trạng thái rỗng:** Biểu tượng xám `48px` + chữ "Không có dữ liệu" (`14px`, `#757575`) + nút hành động.
- **Trạng thái lỗi:** Viền ô chuyển sang đỏ `2px solid #C62828`. Thông báo lỗi `12px` dưới ô. Không có hoạt ảnh rung lắc.

## 8. NGUYÊN TẮC BẮT BUỘC (DO / DON'T)
- **NÊN (DO):** Gán phím tắt cho tất cả chức năng. Giữ bảng dữ liệu cô đọng. Dùng chấm màu trạng thái.
- **KHÔNG NÊN (DON'T):** Không hiệu ứng động (> `150ms`). Không dùng `JOptionPane.showMessageDialog` mặc định. Không dùng cỡ chữ ngoài 6 cỡ chuẩn.

## 9. KHUNG TRỢ LÝ AI COPILOT DOCK (JAVA SWING)
- **Kiến trúc Component:**
  - Không sử dụng `JEditorPane` (tránh lỗi render HTML 3.2 cũ kỹ, vẽ lại chậm và vỡ font tiếng Việt có dấu).
  - Sử dụng `JPanel` với `BoxLayout(box, BoxLayout.Y_AXIS)` đặt trong `JScrollPane` (cuộn mượt, tự động cuộn xuống cuối khi có tin nhắn mới).
- **Quy chuẩn Bong bóng tin nhắn (Message Bubble):**
  - Mỗi bong bóng chat là một `JPanel` con bo góc bằng `new FlatRoundBorder()` (arc `12px`).
  - Nội dung text hiển thị bằng `JTextArea`: `setWrapStyleWord(true)`, `setLineWrap(true)`, `setEditable(false)`, font hệ thống chuẩn `14px`.
  - **Màu sắc bong bóng:**
    - Tin nhắn Copilot (AI): Background `#F5F5F5`, viền `1px solid #E0E0E0`, căn trái.
    - Tin nhắn Staff: Background `#1B2A4A` (Primary Accent), chữ trắng, căn phải.
- **Hiệu năng:** Thời gian thêm tin nhắn và vẽ lại giao diện < 10ms, không gây block Event Dispatch Thread (EDT) bằng cách sử dụng `SwingWorker` khi nhận phản hồi bất đồng bộ từ API.


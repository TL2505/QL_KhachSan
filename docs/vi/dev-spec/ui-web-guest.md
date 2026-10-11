# QUY CHUẨN GIAO DIỆN: WEB KHÁCH HÀNG (REACT 18 + MUI)

## 1. NỀN TẢNG THIẾT KẾ
- **Framework:** React 18 + Material-UI (MUI `5.x`) + TypeScript.
- **Đối tượng sử dụng:** Khách lưu trú trên điện thoại, máy tính bảng, máy tính xách tay.
- **Triết lý:** Ưu tiên di động (Mobile-first). Điểm chạm ngón tay. Giảm tải nhận thức (hoàn thành tác vụ trong 1-3 phút).
- **Quy chuẩn Token:** Màu sắc, typography, trạng thái phòng và khoảng cách tham chiếu từ `ui-tokens.md`.

## 2. ĐIỂM NGẮT ĐÁP ỨNG VÀ BỐ CỤC
- **Điểm ngắt (Breakpoints):** Di động `375px` (iPhone SE), Máy tính bảng `768px` (iPad), Máy tính để bàn `1200px`.
- **Khung chứa tối đa:**
  - Điện thoại: `100%` lên tới `480px`.
  - Máy tính bảng: Căn giữa tối đa `768px`.
  - Máy tính cá nhân: Căn giữa `1200px` với khoảng đệm hai bên `24px`.
- **Kích thước mục chạm:** Vùng chạm tối thiểu `44×44px`. Khoảng cách giữa các phần tử chạm ≥ `8px`.
- **Không phụ thuộc rê chuột (Hover):** Tuyệt đối không yêu cầu rê chuột để hiển thị thao tác hay dữ liệu.

## 3. ĐIỀU HƯỚNG VÀ THANH ĐẦU TRANG
- **Đầu trang (Header):** Cố định dính trên cùng, cao `56px`, đổ bóng `elevation-l1`.
  - Trái: Tên khách sạn (`16px`, độ đậm `600`).
  - Phải: Nút đổi ngôn ngữ (biểu tượng quả địa cầu) + chuông thông báo.
- **Thanh điều hướng dưới đáy (Mobile Tab Bar):** Cố định đáy màn hình, cao `56px`, tối đa 5 tab.
  - Tab đang chọn: Màu chủ đạo accent, biểu tượng đặc ruột, nhãn `12px`.
  - Tab không chọn: Màu `#757575`, biểu tượng nét rỗng, nhãn `12px`.
- **Trang phụ:** Trượt vào từ bên phải (thời gian `300ms`).

## 4. TẢI DỮ LIỆU VÀ QUY TRÌNH TÁC VỤ
- **Màn hình khung xương (Skeleton Screens):** Các khối khung xương màu xám (`#E0E0E0`) nhấp nháy theo chu kỳ `1.5s` khớp với bố cục nội dung thực. KHÔNG BAO GIỜ để màn hình trắng trơn hay chỉ dùng biểu tượng xoay spinner.
- **Biểu mẫu dạng Wizard (Mỗi màn hình 1 câu hỏi):**
  - Thanh tiến trình mỏng phía trên (`4px`, màu chủ đạo accent).
  - Mũi tên quay lại ở góc trên bên trái.
  - Nút "Tiếp tục" chính ở đáy: Tràn toàn bộ chiều rộng, cao `48px`, bo góc `8px`, màu nền accent, chữ trắng `16px` độ đậm `600`.
  - Bước cuối cùng: Nút "Xác nhận đặt phòng".
- **Lưu phiên làm việc:** Nếu quy trình trên 3 bước, tự động lưu dữ liệu vào `localStorage` để khách quay lại có thể tiếp tục ngay.

## 5. THẺ LOẠI PHÒNG VÀ NÚT KÊU GỌI HÀNH ĐỘNG (CTA)
- **Thẻ phòng:**
  - Khung: Đệm trong `16px`, viền `1px solid #E0E0E0`, bo góc `8px`, đổ bóng elevation-l1.
  - Hình ảnh: Đặt trên cùng tỷ lệ `16:9` bo tròn `8px`.
  - Phông chữ: Tên phòng `16px` độ đậm `600`; Giá mỗi đêm `20px` độ đậm `700` màu accent.
  - Tiện ích: Tối đa 3 biểu tượng tiện ích kèm chữ mô tả `12px`.
  - Nút đặt phòng: Tràn toàn chiều rộng đáy thẻ, cao `44px`, bo góc `4px`.
- **Quy tắc nút CTA:** Chỉ DUY NHẤT 1 nút CTA chính trên mỗi màn hình. Trạng thái vô hiệu hóa: nền `#E0E0E0`, chữ `#757575`.

## 6. KHUNG CHAT TRỢ LÝ AI (AI CONCIERGE)
- **Bong bóng tin nhắn:**
  - AI: Canh trái, nền `#F5F5F5`, bo góc `12px`, chiều rộng tối đa 80%.
  - Khách: Canh phải, nền màu accent `#1B2A4A`, chữ trắng, bo góc `12px`, chiều rộng tối đa 80%.
  - Dấu thời gian: `12px` màu `#757575` dưới bong bóng.
  - Giới hạn chữ: Mặc định hiển thị tối đa 4 dòng; vượt quá sẽ có nút "Xem thêm".
- **Thanh nhập tin nhắn:** Cố định đáy trên thanh tab bar, cao `56px`, bo tròn `24px`, nền `#FFFFFF`. Nút gửi tròn `36px` màu accent.
- **Gợi ý nhanh (Prompt chips):** Đúng 3 chip gợi ý phía trên ô chat (`12px`, viền `1px solid #E0E0E0`, bo góc `16px`).

## 7. ĐA NGÔN NGỮ, KIỂM LỖI VÀ PHẢN HỒI
- **Đa ngôn ngữ i18n:** Tự nhận diện theo `navigator.language` trong lần đầu truy cập. Cho phép chọn thủ công: EN, VI, JP, ZH. Lưu mã ngôn ngữ vào `localStorage`.
- **Lỗi xác thực dữ liệu:** Hiển thị ngay dưới ô nhập liệu (`12px`, màu `#C62828`, cách ô `4px`). Tuyệt đối không dùng `alert()` hay chặn màn hình.
- **Trạng thái thành công:** Biểu tượng tích xanh (`24px`) + chữ "Đặt phòng thành công!" (`20px`, đậm `600`) + tự chuyển hướng sau `1.5s`. Không dùng hiệu ứng pháo hoa rườm rà.
- **Trạng thái rỗng:** Hình minh họa vector nhẹ (`120px`) + văn bản mô tả (`16px`, `#757575`) + nút hành động chính.

## 8. NGUYÊN TẮC BẮT BUỘC (DO / DON'T)
- **NÊN (DO):** Đảm bảo vùng chạm ≥ `44px`. Dùng skeleton loading. Hỗ trợ EN/VI/JP/ZH. Tự động lưu biểu mẫu.
- **KHÔNG NÊN (DON'T):** Không làm giao diện phụ thuộc hover. Không quá 1 CTA chính trên một màn hình. Không dùng ảnh dung lượng > `200KB`. Không hỗ trợ Dark mode trong Giai đoạn 3.

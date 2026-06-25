# Quanlykhachsan

## Hướng dẫn chạy dự án

1. Chuẩn bị môi trường
   - Cài đặt JDK 17.
   - Cài đặt MySQL và đảm bảo service đang chạy trên cổng `3306`.
   - Nếu dùng XAMPP, bật MySQL.

2. Cấu hình database
   - Mở `config.properties` và đảm bảo `server.url=http://localhost:8081/api`.
   - Nếu MySQL không có mật khẩu cho root, cấu hình mặc định đã dùng `root` với mật khẩu rỗng.
   - Nếu dùng cấu hình khác, set biến môi trường:
     - `DB_HOST`
     - `DB_PORT`
     - `DB_NAME`
     - `DB_USER`
     - `DB_PASSWORD`

3. Build và chạy backend
   - Chạy `build_project.bat` để biên dịch và tạo class.
   - Khởi chạy backend bằng `Main`:
     - `quanlykhachsan.backend.Main`
   - Backend hiện đang lắng nghe trên cổng `8081`.

4. Chạy giao diện frontend
   - Chạy lớp chính:
     - `quanlykhachsan.Quanlykhachsan`
   - Frontend sẽ gọi API tới `http://localhost:8081/api`.

5. Tham khảo thêm
   - Xem hướng dẫn chi tiết trong `docs/run-project.md`.

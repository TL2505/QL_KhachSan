# Hướng dẫn chạy dự án

## Yêu cầu môi trường

- JDK 17
- MySQL (mặc định chạy cổng 3306)
- Nếu dùng XAMPP thì bật MySQL
- Git để quản lý code

## Thiết lập cấu hình

1. Mở file `config.properties` ở thư mục gốc.
2. Đảm bảo dòng mặc định là:

```properties
server.url=http://localhost:8081/api
```

3. Nếu cần thay đổi cấu hình database, có thể dùng biến môi trường:

- `DB_HOST` (mặc định: `localhost`)
- `DB_PORT` (mặc định: `3306`)
- `DB_NAME` (mặc định: `hotel_prod_db`)
- `DB_USER` (mặc định: `root`)
- `DB_PASSWORD` (mặc định: empty)

## Build và chạy backend

1. Mở terminal ở thư mục gốc dự án.
2. Chạy:

```powershell
build_project.bat
```

3. Khởi chạy backend:

```powershell
cd /d %CD%
java -cp "build/classes;lib/*" quanlykhachsan.backend.Main
```

4. Backend sẽ khởi động ở:

```text
http://localhost:8081/
```

## Chạy frontend

1. Chạy lớp chính frontend:

```powershell
cd /d %CD%
java -cp "build/classes;lib/*" quanlykhachsan.Quanlykhachsan
```

2. Ứng dụng frontend sẽ gọi API tới:

```text
http://localhost:8081/api
```

## Lưu ý

- Nếu backend báo lỗi `Address already in use`, kiểm tra xem cổng `8081` đã bị chiếm bởi tiến trình khác và đổi cổng hoặc tắt tiến trình đó.
- Nếu đăng nhập không thành công, kiểm tra kết nối database và thông tin user trong bảng `users`.
- Nếu cần chạy backend ở cổng khác, đồng bộ cả `Main.java` và `src/quanlykhachsan/frontend/utils/HttpUtil.java`, đồng thời cập nhật `config.properties`.

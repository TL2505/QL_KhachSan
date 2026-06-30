# 🚀 ĐỀ XUẤT CẢI TIẾN KIẾN TRÚC BACKEND

Tài liệu này đánh giá hiện trạng kiến trúc Backend của hệ thống Quản lý Khách sạn và đề xuất các điểm cần cải tiến để hệ thống sẵn sàng hoạt động ở quy mô thực tế (Production-Ready).

---

## 1. Đánh giá hiện trạng

Backend hiện tại được thiết kế theo mô hình **3 lớp (3-tier Architecture)** rất rõ ràng:
- **Controller:** Tiếp nhận request HTTP, xử lý phân quyền JWT, ánh xạ DTO và gọi Service.
- **Service:** Xử lý nghiệp vụ chính của khách sạn.
- **DAO:** Thực hiện các câu lệnh SQL tương tác trực tiếp với cơ sở dữ liệu MySQL.

### Điểm mạnh nổi bật:
- **Tách biệt DTO & Mapper:** Toàn bộ dữ liệu nhạy cảm (như password hash) đã được che giấu qua lớp DTO. Ngăn chặn triệt để lỗi bảo mật **Mass Assignment**.
- **Siêu nhẹ (Zero-dependency):** Sử dụng `com.sun.net.httpserver.HttpServer` của Java giúp server khởi động siêu nhanh (< 1 giây) và tốn rất ít tài nguyên RAM.

---

## 2. Các điểm cần cải tiến (Điểm nghẽn cần tối ưu)

### ⚠️ A. Tích hợp Database Connection Pool (Quan trọng nhất)
- **Hiện trạng:** Lớp `DBconn.getConnection()` đang mở một kết nối vật lý mới qua TCP/IP đến MySQL cho mỗi request (`DriverManager.getConnection(...)`) và đóng ngay lập tức sau khi truy vấn xong.
- **Vấn đề hiệu năng:** Khi Web UI hoạt động, trình duyệt sẽ gửi đồng thời nhiều request API (lấy danh sách hóa đơn, phòng, thông tin user,...). Việc mở/đóng liên tục kết nối sẽ khiến MySQL bị quá tải kết nối, gây tăng độ trễ (latency) lớn và hệ thống sẽ phản hồi rất chậm.
- **Đề xuất:** Tích hợp **HikariCP** (thư viện Connection Pool tốt nhất hiện nay cho Java).
  - *Cơ chế:* HikariCP sẽ duy trì sẵn một số lượng kết nối tối thiểu (ví dụ: 10 kết nối). Khi có request, DAO chỉ cần "mượn" kết nối đã mở sẵn và "trả lại" pool sau khi dùng xong mà không cần đóng kết nối TCP/IP vật lý.

### ⚙️ B. Tối ưu hóa bộ định tuyến (Routing) & Bắt lỗi tập trung
- **Hiện trạng:** Do sử dụng thư viện thô `HttpServer`, các Controller phải phân tích đường dẫn URL bằng Regex thủ công (ví dụ: `pathInfo.matches("/\\d+/status")`). Đồng thời, mọi method đều phải bọc trong các khối `try-catch` lặp đi lặp lại để trả về mã lỗi 500.
- **Đề xuất:**
  - Viết một **BaseController** dùng chung hoặc tích hợp một Web Framework siêu nhẹ (như **Javalin** hoặc **SparkJava**) để cấu hình router ngắn gọn.
  - Xây dựng **Global Exception Handler** để bắt lỗi tập trung, tự động log lỗi và trả về mã lỗi HTTP thích hợp (400, 404, 500), giúp code Controller sạch hơn và không bị lặp lại.

### 🔒 C. Cấu hình CORS chặt chẽ hơn
- **Hiện trạng:** Hầu hết các Controller đang thiết lập header `Access-Control-Allow-Origin: *` để tránh lỗi CORS khi gọi từ trình duyệt.
- **Vấn đề bảo mật:** Cho phép mọi nguồn gốc (Origin) truy cập API có thể tạo điều kiện cho các cuộc tấn công CSRF hoặc đánh cắp tài nguyên.
- **Đề xuất:** Cấu hình nguồn gốc động hoặc cố định, chỉ cho phép địa chỉ IP/Domain của ứng dụng Web UI được quyền gọi API (ví dụ: `http://localhost:3000` hoặc domain chạy production).

### 📝 D. Quản lý biến môi trường (Environment Variables)
- **Hiện trạng:** Đã bước đầu triển khai lấy biến môi trường trong `DBconn` cho cấu hình DB.
- **Đề xuất:** Đồng bộ hóa tất cả các cấu hình khác (cổng chạy Server `8081`, JWT Secret Key, thời hạn hết hạn của Token) ra file môi trường `.env` hoặc file cấu hình tập trung để tránh hardcode trong mã nguồn Java.

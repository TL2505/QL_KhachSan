package quanlykhachsan.backend;

import quanlykhachsan.backend.controller.AuthController;
import quanlykhachsan.backend.controller.InvoiceController;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) {
        try {
            // Đảm bảo cấu trúc Database đã được tạo hoặc cập nhật đầy đủ
            quanlykhachsan.backend.utils.UpdateDbUtility.main(new String[] {});

            // Khởi tạo Server lắng nghe tại cổng 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            System.out.println("🚀 Đang khởi động Backend Server API...");

            // --- ĐĂNG KÝ CÁC ROUTE (API ENDPOINTS) TẠI ĐÂY ---
            // Route Đăng nhập và Đăng ký
            server.createContext("/api/auth/login", new AuthController());
            server.createContext("/api/auth/register", new AuthController());
            // Route Quản lý phòng
            server.createContext("/api/rooms", new quanlykhachsan.backend.controller.RoomController());
            // Route Hồ sơ người dùng
            server.createContext("/api/users/update-profile", new quanlykhachsan.backend.controller.UserController());
            server.createContext("/api/users/change-password", new quanlykhachsan.backend.controller.UserController());
            server.createContext("/api/users/update-theme", new quanlykhachsan.backend.controller.UserController());
            // Route Phân quyền (Roles) - PHẢI đăng ký TRƯỚC /api/users
            server.createContext("/api/roles", new quanlykhachsan.backend.controller.UserController());
            // Route Quản lý nhân sự
            server.createContext("/api/users", new quanlykhachsan.backend.controller.UserController());
            // Route Báo cáo
            server.createContext("/api/reports", new quanlykhachsan.backend.controller.ReportController());
            // Route Khách hàng
            server.createContext("/api/customers", new quanlykhachsan.backend.controller.CustomerController());
            // Route Đặt phòng / Check-in / Check-out
            server.createContext("/api/bookings", new quanlykhachsan.backend.controller.BookingController());
            // Route Thanh toán (Payment)
            server.createContext("/api/payments", new quanlykhachsan.backend.controller.PaymentController());
            // Route Quản lý Hóa đơn
            server.createContext("/api/invoices", new quanlykhachsan.backend.controller.InvoiceController());
            // Route Đánh giá (Review)
            server.createContext("/api/reviews", new quanlykhachsan.backend.controller.ReviewController());
            // Route Chat
            server.createContext("/api/chat", new quanlykhachsan.backend.controller.ChatController());
            // Route Khuyến mãi (Promotion)
            server.createContext("/api/promotions", new quanlykhachsan.backend.controller.PromotionController());
            // Route Khách hàng thân thiết (Loyalty)
            server.createContext("/api/loyalty", new quanlykhachsan.backend.controller.LoyaltyController());

            // Thiết lập cấu hình mặc định và chạy server
            server.setExecutor(null);
            server.start();

            System.out.println("✅ Server đang chạy thành công tại: http://localhost:8080/");
            System.out.println("👉 Hãy mở Postman và test API: POST http://localhost:8080/api/auth/login");

        } catch (IOException e) {
            System.err.println("❌ Lỗi khi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

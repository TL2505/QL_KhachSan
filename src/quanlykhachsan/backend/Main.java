package quanlykhachsan.backend;

import quanlykhachsan.backend.controller.AuthController;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) {
        try {
            // Khởi tạo Server lắng nghe tại cổng 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            System.out.println("🚀 Đang khởi động Backend Server API...");

            // --- ĐĂNG KÝ CÁC ROUTE (API ENDPOINTS) TẠI ĐÂY ---
            // Route Đăng nhập
            server.createContext("/api/auth/login", new AuthController());
            // Route Quản lý phòng
            server.createContext("/api/rooms", new quanlykhachsan.backend.controller.RoomController());
            // Route Khách hàng
            server.createContext("/api/customers", new quanlykhachsan.backend.controller.CustomerController());
            // Route Đặt phòng / Check-in / Check-out
            server.createContext("/api/bookings", new quanlykhachsan.backend.controller.BookingController());
            // Route Thanh toán (Payment)
            server.createContext("/api/payments", new quanlykhachsan.backend.controller.PaymentController());
            // Route Dashboard (Admin only logic handles inside)
            server.createContext("/api/dashboard", new quanlykhachsan.backend.controller.DashboardController());
            // Route Loại Phòng
            server.createContext("/api/room-types", new quanlykhachsan.backend.controller.RoomTypeController());

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

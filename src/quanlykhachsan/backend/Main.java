package quanlykhachsan.backend;

import quanlykhachsan.backend.auth.AuthController;
import quanlykhachsan.backend.booking.InvoiceController;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.utils.CorsFilter;
import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) {
        try {
            // Đảm bảo cấu trúc Database đã được tạo hoặc cập nhật đầy đủ
            quanlykhachsan.backend.utils.UpdateDbUtility.main(new String[] {});

            // Khởi tạo Server lắng nghe tại cổng 8081
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

            System.out.println("🚀 Đang khởi động Backend Server API...");

            // Khởi tạo CORS Filter
            Filter corsFilter = new CorsFilter();

            // --- ĐĂNG KÝ CÁC ROUTE (API ENDPOINTS) TẠI ĐÂY ---
            // Route Đăng nhập và Đăng ký
            server.createContext("/api/health", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                String response = "{\"status\":\"success\",\"data\":\"OK\"}";
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }).getFilters().add(corsFilter);

            registerContext(server, "/api/auth/login", new AuthController(), corsFilter);
            registerContext(server, "/api/auth/register", new AuthController(), corsFilter);
            // Route Quản lý phòng
            registerContext(server, "/api/rooms", new quanlykhachsan.backend.room.RoomController(), corsFilter);
            // Route Hồ sơ người dùng
            registerContext(server, "/api/users/update-profile", new quanlykhachsan.backend.user.UserController(), corsFilter);
            registerContext(server, "/api/users/change-password", new quanlykhachsan.backend.user.UserController(), corsFilter);
            registerContext(server, "/api/users/update-theme", new quanlykhachsan.backend.user.UserController(), corsFilter);
            // Route Phân quyền (Roles) - PHẢI đăng ký TRƯỚC /api/users
            registerContext(server, "/api/roles", new quanlykhachsan.backend.user.UserController(), corsFilter);
            // Route Quản lý nhân sự
            registerContext(server, "/api/users", new quanlykhachsan.backend.user.UserController(), corsFilter);
            // Route Báo cáo
            registerContext(server, "/api/reports", new quanlykhachsan.backend.report.ReportController(), corsFilter);
            // Route Khách hàng
            registerContext(server, "/api/customers", new quanlykhachsan.backend.customer.CustomerController(), corsFilter);
            // Route Đặt phòng / Check-in / Check-out
            registerContext(server, "/api/bookings", new quanlykhachsan.backend.booking.BookingController(), corsFilter);
            // Route Thanh toán (Payment)
            registerContext(server, "/api/payments", new quanlykhachsan.backend.booking.PaymentController(), corsFilter);
            // Route Quản lý Hóa đơn
            registerContext(server, "/api/invoices", new quanlykhachsan.backend.booking.InvoiceController(), corsFilter);
            // Route Đánh giá (Review)
            registerContext(server, "/api/reviews", new quanlykhachsan.backend.interaction.ReviewController(), corsFilter);
            // Route Chat
            registerContext(server, "/api/chat", new quanlykhachsan.backend.interaction.ChatController(), corsFilter);
            // Route Khuyến mãi (Promotion)
            registerContext(server, "/api/promotions", new quanlykhachsan.backend.promotion.PromotionController(), corsFilter);
            // Route Khách hàng thân thiết (Loyalty)
            registerContext(server, "/api/loyalty", new quanlykhachsan.backend.customer.LoyaltyController(), corsFilter);

            // Thiết lập cấu hình mặc định và chạy server
            server.setExecutor(null);
            server.start();

            System.out.println("Server đang chạy thành công tại: http://localhost:8081/");
            System.out.println("Hãy mở Postman và test API: POST http://localhost:8081/api/auth/login");

        } catch (IOException e) {
            System.err.println("Lỗi khi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void registerContext(HttpServer server, String path, HttpHandler handler, Filter filter) {
        server.createContext(path, handler).getFilters().add(filter);
    }
}

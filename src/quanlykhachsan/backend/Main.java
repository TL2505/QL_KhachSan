package quanlykhachsan.backend;

import quanlykhachsan.backend.auth.AuthController;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.utils.CorsFilter;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.GlobalExceptionHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        try {
            // Đảm bảo cấu trúc Database đã được tạo hoặc cập nhật đầy đủ
            quanlykhachsan.backend.utils.UpdateDbUtility.main(new String[] {});

            // Khởi tạo Server lắng nghe tại cổng 8081
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

            System.out.println("🚀 Đang khởi động Backend Server API...");

            // Khởi tạo các Filter
            Filter corsFilter = new CorsFilter();
            Filter jwtFilter = new JwtFilter();
            Filter exceptionFilter = new GlobalExceptionHandler();

            // --- ĐĂNG KÝ CÁC ROUTE (API ENDPOINTS) TẠI ĐÂY ---
            // Route Health
            server.createContext("/api/v1/health", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                String response = "{\"status\":\"success\",\"data\":\"OK\"}";
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }).getFilters().addAll(Arrays.asList(exceptionFilter, corsFilter));

            // Route Đăng nhập và Đăng ký (Được skip JWT auth bên trong JwtFilter)
            registerContext(server, "/api/v1/auth/login", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/auth/register", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
            
            // Route Quản lý phòng
            registerContext(server, "/api/v1/rooms", new quanlykhachsan.backend.room.RoomController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Hồ sơ người dùng
            registerContext(server, "/api/v1/users/update-profile", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/users/change-password", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/users/update-theme", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Phân quyền (Roles) - PHẢI đăng ký TRƯỚC /api/v1/users
            registerContext(server, "/api/v1/roles", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Quản lý nhân sự
            registerContext(server, "/api/v1/users", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Báo cáo
            registerContext(server, "/api/v1/reports", new quanlykhachsan.backend.report.ReportController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Khách hàng
            registerContext(server, "/api/v1/customers", new quanlykhachsan.backend.customer.CustomerController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Đặt phòng / Check-in / Check-out
            registerContext(server, "/api/v1/bookings", new quanlykhachsan.backend.booking.BookingController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Thanh toán (Payment)
            registerContext(server, "/api/v1/payments", new quanlykhachsan.backend.booking.PaymentController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Quản lý Hóa đơn
            registerContext(server, "/api/v1/invoices", new quanlykhachsan.backend.booking.InvoiceController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Đánh giá (Review)
            registerContext(server, "/api/v1/reviews", new quanlykhachsan.backend.interaction.ReviewController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Chat
            registerContext(server, "/api/v1/chat", new quanlykhachsan.backend.interaction.ChatController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Khuyến mãi (Promotion)
            registerContext(server, "/api/v1/promotions", new quanlykhachsan.backend.promotion.PromotionController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Khách hàng thân thiết (Loyalty)
            registerContext(server, "/api/v1/loyalty", new quanlykhachsan.backend.customer.LoyaltyController(), exceptionFilter, corsFilter, jwtFilter);

            // Thiết lập cấu hình mặc định và chạy server
            server.setExecutor(null);
            server.start();

            System.out.println("Server đang chạy thành công tại: http://localhost:8081/");
            System.out.println("Hãy mở Postman và test API: POST http://localhost:8081/api/v1/auth/login");

        } catch (IOException e) {
            System.err.println("Lỗi khi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void registerContext(HttpServer server, String path, HttpHandler handler, Filter... filters) {
        var context = server.createContext(path, handler);
        for (Filter filter : filters) {
            context.getFilters().add(filter);
        }
    }
}

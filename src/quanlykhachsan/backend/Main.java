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
import java.util.concurrent.Executors;
import quanlykhachsan.backend.utils.SystemStatusFilter;
import quanlykhachsan.backend.utils.IdempotencyFilter;

public class Main {
    public static void main(String[] args) {
        try {
            // Đảm bảo cấu trúc Database đã được tạo hoặc cập nhật đầy đủ
            quanlykhachsan.backend.utils.UpdateDbUtility.main(new String[] {});

            // Khởi tạo Server lắng nghe tại cổng 8081
            HttpServer server = createServer(8081);
            server.start();
            var nightAuditJob = new quanlykhachsan.backend.system.NightAuditJob();
            nightAuditJob.start();
            var noShowJob = new quanlykhachsan.backend.booking.NoShowJob();
            noShowJob.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                noShowJob.close();
                nightAuditJob.close();
                server.stop(10);
                quanlykhachsan.backend.utils.DatabaseConnection.close();
            }, "hotel-shutdown"));

            System.out.println("Server đang chạy thành công tại: http://localhost:8081/");
            System.out.println("Hãy mở Postman và test API: POST http://localhost:8081/api/v1/auth/login");

        } catch (IOException e) {
            System.err.println("Lỗi khi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Configures the same production routes used by main, without starting jobs or migrating data. */
    public static HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port),0);
            // Khởi tạo các Filter
            Filter corsFilter = new CorsFilter();
            Filter jwtFilter = new JwtFilter();
            Filter exceptionFilter = new GlobalExceptionHandler();
            Filter systemStatusFilter = new SystemStatusFilter();
            Filter idempotencyFilter = new IdempotencyFilter();

            // --- ĐĂNG KÝ CÁC ROUTE (API ENDPOINTS) TẠI ĐÂY ---
            // Route Health
            registerContext(server,"/api/v1/health", Main::handleHealthCheck, exceptionFilter, corsFilter);

            // Route Đăng nhập và Đăng ký (Được skip JWT auth bên trong JwtFilter)
            registerContext(server, "/api/v1/auth/login", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/auth/register", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/auth/guest-otp", new quanlykhachsan.backend.auth.GuestAuthController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/auth/google", new quanlykhachsan.backend.auth.GoogleOtpAuthController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/customer-portal", new quanlykhachsan.backend.customer.CustomerPortalController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
            registerContext(server, "/api/v1/ai/concierge", new quanlykhachsan.backend.ai.VirtualConciergeController(), exceptionFilter, corsFilter, jwtFilter);
            
            // Route Quản lý phòng
            registerContext(server, "/api/v1/rooms", new quanlykhachsan.backend.room.RoomController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/rooms/availability", new quanlykhachsan.backend.room.RoomAvailabilityController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/rooms", new quanlykhachsan.backend.room.RoomController(), exceptionFilter, corsFilter, jwtFilter);
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
            registerContext(server, "/api/customers", new quanlykhachsan.backend.customer.CustomerController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Đặt phòng / Check-in / Check-out
            var bookingController = new quanlykhachsan.backend.booking.BookingController();
            registerContext(server, "/api/v1/pricing/quote", new quanlykhachsan.backend.pricing.PricingController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/reports/financial/dbr", new quanlykhachsan.backend.report.FinancialReportController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/bookings/public", new quanlykhachsan.backend.booking.PublicBookingController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
            registerContext(server, "/api/v1/bookings/walk-in", new quanlykhachsan.backend.booking.PublicBookingController(true), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
            registerContext(server, "/api/v1/bookings", bookingController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
            registerContext(server, "/api/bookings", bookingController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
            // Route Thanh toán (Payment)
            registerContext(server, "/api/v1/webhooks/vietqr", new quanlykhachsan.backend.booking.BankWebhookController(), exceptionFilter, corsFilter, systemStatusFilter);
            registerContext(server, "/api/v1/payments", new quanlykhachsan.backend.booking.PaymentController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
            registerContext(server, "/api/payments", new quanlykhachsan.backend.booking.PaymentController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
            registerContext(server, "/api/v1/folios", new quanlykhachsan.backend.booking.FolioController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
            registerContext(server, "/api/v1/pos/sync", new quanlykhachsan.backend.booking.PosSyncController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
            // Route Quản lý Hóa đơn
            registerContext(server, "/api/v1/invoices", new quanlykhachsan.backend.booking.InvoiceController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Đánh giá (Review)
            registerContext(server, "/api/v1/reviews", new quanlykhachsan.backend.interaction.ReviewController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Chat
            registerContext(server, "/api/v1/chat", new quanlykhachsan.backend.interaction.ChatController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Khuyến mãi (Promotion)
            registerContext(server, "/api/v1/promotions/validate", new quanlykhachsan.backend.promotion.VoucherValidationController(), exceptionFilter, corsFilter, jwtFilter);
            registerContext(server, "/api/v1/promotions", new quanlykhachsan.backend.promotion.PromotionController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Khách hàng thân thiết (Loyalty)
            registerContext(server, "/api/v1/loyalty", new quanlykhachsan.backend.customer.LoyaltyController(), exceptionFilter, corsFilter, jwtFilter);

            // Thiết lập cấu hình mặc định và chạy server
            var staticFiles = new quanlykhachsan.backend.utils.StaticFileHandler(
                    java.nio.file.Path.of("web-ui", "public"));
            registerContext(server, "/booking", staticFiles, exceptionFilter, corsFilter);
            registerContext(server, "/", staticFiles, exceptionFilter, corsFilter);
            server.setExecutor(Executors.newFixedThreadPool(20));

        return server;
    }

    private static void handleHealthCheck(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            quanlykhachsan.backend.utils.GlobalExceptionHandler.sendError(exchange,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed"); return;
        }
        com.google.gson.JsonObject health = new com.google.gson.JsonObject();
        try (java.sql.Connection c = quanlykhachsan.backend.utils.DatabaseConnection.getConnection();
             java.sql.Statement stmt = c.createStatement()) {
            stmt.setQueryTimeout(1);
            try (java.sql.ResultSet rows = stmt.executeQuery("SELECT 1")) {
                if (!rows.next()) throw new java.sql.SQLException("Database health check failed");
            }
            health.addProperty("status","UP");
            health.addProperty("database","CONNECTED");
            health.addProperty("active_connections",quanlykhachsan.backend.utils.DatabaseConnection.activeConnections());
            health.addProperty("awaiting_connections",quanlykhachsan.backend.utils.DatabaseConnection.awaitingConnections());
            quanlykhachsan.backend.utils.ApiResponseUtil.write(exchange,200,health.toString());
        } catch (java.sql.SQLException e) {
            health.addProperty("status","DOWN");health.addProperty("database","DISCONNECTED");
            quanlykhachsan.backend.utils.ApiResponseUtil.write(exchange,503,health.toString());
        }
    }

    private static void registerContext(HttpServer server, String path, HttpHandler handler, Filter... filters) {
        var context = server.createContext(path, handler);
        context.getFilters().add(new quanlykhachsan.backend.utils.SecurityHardeningFilter());
        for (Filter filter : filters) {
            context.getFilters().add(filter);
        }
    }
}

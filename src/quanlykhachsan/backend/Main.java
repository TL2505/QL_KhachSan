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
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import quanlykhachsan.backend.utils.SystemStatusFilter;
import quanlykhachsan.backend.utils.IdempotencyFilter;

public class Main {
    private static final Map<HttpServer, AutoCloseable> BACKGROUND_SERVICES = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        HttpServer server = null;
        String stage = "CONFIG";
        try {
            quanlykhachsan.backend.customer.GuestIdentityVaultDao.configured();
            quanlykhachsan.backend.utils.JwtTokenUtil.signingKey();
            System.out.println("[STARTUP CONFIG] Vault keys and JWT configuration accepted");
            stage = "DATABASE_PREFLIGHT";
            if (!quanlykhachsan.backend.utils.BackendDiagnostics.run("startup"))
                throw new IllegalStateException("Read-only database/key preflight failed; no migration was run");
            stage = "DATABASE_MIGRATION";
            // Required schema and vault readiness must succeed before accepting traffic.
            quanlykhachsan.backend.utils.UpdateDbUtility.main(new String[0]);
            stage = "VAULT";
            quanlykhachsan.backend.customer.IdentityVaultMigration.requireReady();
            stage = "HTTP";
            server=createServer(8081);
            server.start();
            var nightAuditJob = new quanlykhachsan.backend.system.NightAuditJob();
            nightAuditJob.start();
            var noShowJob = new quanlykhachsan.backend.booking.NoShowJob();
            noShowJob.start();
            var expiryJob=new quanlykhachsan.backend.booking.PendingBookingExpiryJob();
            expiryJob.start();
            var piiJob = new quanlykhachsan.backend.system.PiiAnonymizerJob();
            piiJob.start();
            var notifications=new quanlykhachsan.backend.notification.NotificationWorker();
            notifications.start();

            final HttpServer runningServer = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                noShowJob.close();
                expiryJob.close();
                piiJob.close();
                notifications.close();
                closeBackgroundServices(runningServer);
                nightAuditJob.close();
                runningServer.stop(10);
                quanlykhachsan.backend.utils.DatabaseConnection.close();
            }, "hotel-shutdown"));

            System.out.println("[STARTUP READY] POST http://localhost:8081/api/v1/auth/login");

        } catch (Throwable e) {
            System.err.println("[STARTUP FAILED] stage=" + stage);
            if(server!=null){closeBackgroundServices(server);server.stop(0);}
            throw new IllegalStateException("PMS startup failed",e);
        }
    }

    /** Configures the same production routes used by main, without starting jobs or migrating data. */
    public static HttpServer createServer(int port) throws IOException {
        String bindAddress = System.getenv().getOrDefault("HOTEL_BIND_ADDRESS", "127.0.0.1").trim();
        if (bindAddress.isEmpty()) bindAddress = "127.0.0.1";
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        // Khởi tạo các Filter
        Filter corsFilter = new CorsFilter();
        Filter jwtFilter = new JwtFilter((userId, tokenRole) ->
                new quanlykhachsan.backend.user.UserDAOImpl().isRoleCurrent(userId, tokenRole));
        Filter exceptionFilter = new GlobalExceptionHandler();
        Filter systemStatusFilter = new SystemStatusFilter();
        Filter idempotencyFilter = new IdempotencyFilter();
        Filter ipRateLimiter = new quanlykhachsan.backend.utils.IpRateLimiter();
        Filter staticIpRateLimiter = new quanlykhachsan.backend.utils.IpRateLimiter(70);

        // --- ĐĂNG KÝ CÁC ROUTE (API ENDPOINTS) TẠI ĐÂY ---
        // Route Health (Cả /api/v1/health và /health đều public)
        registerContext(server, ipRateLimiter, "/api/v1/health", Main::handleHealthCheck, exceptionFilter, corsFilter);
        registerContext(server, ipRateLimiter, "/health", Main::handleHealthCheck, exceptionFilter, corsFilter);

        // Route Đăng nhập và Đăng ký (Được skip JWT auth bên trong JwtFilter)
        registerContext(server, ipRateLimiter, "/api/v1/auth/login", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/auth/register", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/auth/facebook", new AuthController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/auth/logout", new quanlykhachsan.backend.auth.LogoutController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/account", new quanlykhachsan.backend.auth.AccountSecurityController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/auth/change-password", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/auth/guest-otp", new quanlykhachsan.backend.auth.GuestAuthController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/auth/google", new quanlykhachsan.backend.auth.GoogleOtpAuthController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/customer-portal", new quanlykhachsan.backend.customer.CustomerPortalController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);

        var aiCheckin = new quanlykhachsan.backend.ai.AiCheckinController();
        registerContext(server, ipRateLimiter, "/api/v1/customer-portal/identity/scan", aiCheckin, exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/ai/id-ocr", aiCheckin, exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/ai/face-verify", aiCheckin, exceptionFilter, corsFilter, jwtFilter);
        var concierge = new quanlykhachsan.backend.ai.VirtualConciergeController();
        registerContext(server, ipRateLimiter, "/api/v1/admin/ai-settings", new quanlykhachsan.backend.ai.AiProviderSettingsController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/admin/rental-policy", new quanlykhachsan.backend.pricing.RentalPolicyAdminController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/admin/catalog", new quanlykhachsan.backend.catalog.RoomCatalogController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/admin/catalog/content", new quanlykhachsan.backend.catalog.LandingContentController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/admin/catalog/media", new quanlykhachsan.backend.catalog.MediaController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, "/api/v1/public/room-types", new quanlykhachsan.backend.catalog.RoomCatalogController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, "/api/v1/public/landing-content", new quanlykhachsan.backend.catalog.LandingContentController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/media", new quanlykhachsan.backend.catalog.MediaController(), exceptionFilter, corsFilter);
        BACKGROUND_SERVICES.put(server, concierge);
        Filter conciergeBudget = new Filter() {
            public String description() { return "Verified-token concierge budget before prompt validation"; }
            public void doFilter(com.sun.net.httpserver.HttpExchange e, Chain chain) throws IOException { if (concierge.checkChatBudget(e)) chain.doFilter(e); }
        };
        registerContext(server, ipRateLimiter, "/api/v1/ai/concierge/chat", concierge, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, conciergeBudget, new quanlykhachsan.backend.ai.PromptGuardrailFilter(concierge::recordRejectedPrompt));
        registerContext(server, ipRateLimiter, "/api/v1/ai/concierge", concierge, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);

        // Route Quản lý phòng
        registerContext(server, ipRateLimiter, "/api/v1/rooms", new quanlykhachsan.backend.room.RoomController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/rooms/availability", new quanlykhachsan.backend.room.RoomAvailabilityController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/housekeeping/tasks", new quanlykhachsan.backend.room.HousekeepingController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/rooms", new quanlykhachsan.backend.room.RoomController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Hồ sơ người dùng
        registerContext(server, ipRateLimiter, "/api/v1/users/update-profile", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/users/change-password", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/users/update-theme", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Phân quyền (Roles) - PHẢI đăng ký TRƯỚC /api/v1/users
        registerContext(server, ipRateLimiter, "/api/v1/roles", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Quản lý nhân sự
        registerContext(server, ipRateLimiter, "/api/v1/users", new quanlykhachsan.backend.user.UserController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Báo cáo
        registerContext(server, ipRateLimiter, "/api/v1/reports", new quanlykhachsan.backend.report.ReportController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Khách hàng
        registerContext(server, ipRateLimiter, "/api/v1/customers", new quanlykhachsan.backend.customer.CustomerController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/customers", new quanlykhachsan.backend.customer.CustomerController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Đặt phòng / Check-in / Check-out
        var bookingController = new quanlykhachsan.backend.booking.BookingController();
        registerContext(server, ipRateLimiter, "/api/v1/checkin-passes", new quanlykhachsan.backend.booking.ExpressPassController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/pricing/quote", new quanlykhachsan.backend.pricing.PricingController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/reports/financial/dbr", new quanlykhachsan.backend.report.FinancialReportController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/reports/financial/reconciliation", new quanlykhachsan.backend.report.FinancialReportController(), exceptionFilter, corsFilter, jwtFilter);
        var yieldController = new quanlykhachsan.backend.report.YieldRecommendationController();
        registerContext(server, ipRateLimiter, "/api/v1/revenue/forecast", yieldController, exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/revenue/apply-rates", yieldController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/bookings/public", new quanlykhachsan.backend.booking.PublicBookingController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/bookings/walk-in", new quanlykhachsan.backend.booking.PublicBookingController(true), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/bookings", bookingController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
        registerContext(server, ipRateLimiter, "/api/bookings", bookingController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
        // Route Thanh toán (Payment)
        registerContext(server, ipRateLimiter, "/api/v1/webhooks/vietqr", new quanlykhachsan.backend.booking.BankWebhookController(), exceptionFilter, corsFilter, systemStatusFilter);
        // Bank events must still be durably received during a night audit.
        registerContext(server, ipRateLimiter, "/api/v1/webhooks/payos", new quanlykhachsan.backend.payment.PayOsWebhookController(), exceptionFilter, corsFilter);
        registerContext(server, ipRateLimiter, "/api/v1/payment-operations", new quanlykhachsan.backend.payment.PaymentOperationsController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/payments", new quanlykhachsan.backend.booking.PaymentController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/payments", new quanlykhachsan.backend.booking.PaymentController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/folios", new quanlykhachsan.backend.booking.FolioController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
        registerContext(server, ipRateLimiter, "/api/v1/pos/sync", new quanlykhachsan.backend.booking.PosSyncController(), exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        // Route Quản lý Hóa đơn
        registerContext(server, ipRateLimiter, "/api/v1/invoices", new quanlykhachsan.backend.booking.InvoiceController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Dịch vụ khách sạn
        registerContext(server, ipRateLimiter, "/api/v1/services", new quanlykhachsan.backend.hotelservice.ServiceController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/service-usage", new quanlykhachsan.backend.hotelservice.ServiceUsageController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Đặt Dịch vụ Phòng & Giám sát Lễ tân (Phase 2.1)
        var serviceOrderController = new quanlykhachsan.backend.hotelservice.ServiceOrderController();
        registerContext(server, ipRateLimiter, "/api/v1/service-orders/active", serviceOrderController, exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/service-orders", serviceOrderController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter);
        // Route Thông báo Vòng đời & Voucher Khách hàng (Phase 2.1)
        var notificationController = new quanlykhachsan.backend.customer.NotificationController();
        registerContext(server, ipRateLimiter, "/api/v1/notifications", notificationController, exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/staff-notifications", new quanlykhachsan.backend.notification.StaffNotificationController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Đánh giá (Review)
        registerContext(server, ipRateLimiter, "/api/v1/reviews", new quanlykhachsan.backend.interaction.ReviewController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Chat
        registerContext(server, ipRateLimiter, "/api/v1/chat", new quanlykhachsan.backend.interaction.ChatController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Khuyến mãi (Promotion)
        registerContext(server, ipRateLimiter, "/api/v1/promotions/validate", new quanlykhachsan.backend.promotion.VoucherValidationController(), exceptionFilter, corsFilter, jwtFilter);
        registerContext(server, ipRateLimiter, "/api/v1/promotions", new quanlykhachsan.backend.promotion.PromotionController(), exceptionFilter, corsFilter, jwtFilter);
        // Route Khách hàng thân thiết (Loyalty)
        registerContext(server, ipRateLimiter, "/api/v1/loyalty", new quanlykhachsan.backend.customer.LoyaltyController(), exceptionFilter, corsFilter, jwtFilter);

        // Thiết lập cấu hình mặc định và chạy server
        var staticFiles = new quanlykhachsan.backend.utils.StaticFileHandler(
                java.nio.file.Path.of("web-ui", "public"));
        // A page loads dozens of scripts/styles without a bearer header. Static assets must
        // not consume the shared unauthenticated API budget during login navigation.
        registerContext(server, staticIpRateLimiter, "/booking", staticFiles, exceptionFilter, corsFilter);
        registerContext(server, staticIpRateLimiter, "/", staticFiles, exceptionFilter, corsFilter);
        server.setExecutor(Executors.newFixedThreadPool(20));

        return server;
    }

    private static void handleHealthCheck(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            quanlykhachsan.backend.utils.GlobalExceptionHandler.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        com.google.gson.JsonObject health = new com.google.gson.JsonObject();
        health.addProperty("status", "UP");
        health.addProperty("timestamp", System.currentTimeMillis());
        try (java.sql.Connection c = quanlykhachsan.backend.utils.DatabaseConnection.getConnection();
             java.sql.Statement stmt = c.createStatement()) {
            stmt.setQueryTimeout(1);
            try (java.sql.ResultSet rows = stmt.executeQuery("SELECT 1")) {
                if (rows.next()) {
                    health.addProperty("database", "CONNECTED");
                    health.addProperty("active_connections", quanlykhachsan.backend.utils.DatabaseConnection.activeConnections());
                    health.addProperty("awaiting_connections", quanlykhachsan.backend.utils.DatabaseConnection.awaitingConnections());
                    quanlykhachsan.backend.utils.ApiResponseUtil.write(exchange, 200, health.toString());
                    return;
                }
            }
        } catch (Exception e) {
            health.addProperty("database", "DISCONNECTED");
            health.addProperty("warning", e.getMessage());
        }
        quanlykhachsan.backend.utils.ApiResponseUtil.write(exchange, 200, health.toString());
    }

    private static void registerContext(HttpServer server, Filter limiter, String path, HttpHandler handler, Filter... filters) {
        var context = server.createContext(path, handler);
        context.getFilters().add(new quanlykhachsan.backend.utils.SecurityHardeningFilter());
        if (limiter != null) {
            context.getFilters().add(limiter);
        }
        for (Filter filter : filters) {
            context.getFilters().add(filter);
        }
        context.getFilters().add(new quanlykhachsan.backend.utils.HousekeepingRoleFilter());
    }

    private static void registerContext(HttpServer server, String path, HttpHandler handler, Filter... filters) {
        registerContext(server, null, path, handler, filters);
    }

    public static void closeBackgroundServices(HttpServer server) {
        if (server != null) {
            AutoCloseable service = BACKGROUND_SERVICES.remove(server);
            if (service != null) {
                try {
                    service.close();
                } catch (Exception ex) {
                    System.err.println("[Main] Error closing background service: " + ex.getMessage());
                }
            }
        }
    }
}

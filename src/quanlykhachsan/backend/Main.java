package quanlykhachsan.backend;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import quanlykhachsan.backend.auth.AuthController;
import quanlykhachsan.backend.booking.PosSyncController;
import quanlykhachsan.backend.system.SystemStateHolder;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.CorsFilter;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.GlobalExceptionHandler;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.Router;
import quanlykhachsan.backend.utils.SecurityHardeningFilter;
import quanlykhachsan.backend.utils.SystemStatusFilter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import java.util.concurrent.Executors;
import quanlykhachsan.backend.utils.SystemStatusFilter;
import quanlykhachsan.backend.utils.IdempotencyFilter;

/**
 * 5★ Luxury Hotel PMS & AI Platform - Backend Entry Point (TASK-A1.1 / TASK-A1.2 / TASK-A4.1).
 * <p>
 * Architectural Strategy:
 * <ul>
 *   <li>The {@link Router} is bound to the single root context ("/") on {@link HttpServer},
 *       eliminating HttpServer Longest Prefix Match conflicts.</li>
 *   <li>Security Hardening Filter attaches OWASP defense headers to all responses.</li>
 *   <li>Deep Healthcheck tests MariaDB (SELECT 1), HikariCP pool metrics, and JVM heap.</li>
 *   <li>Graceful shutdown hook stops HttpServer with a 5-second request draining grace period
 *       before closing the HikariCP connection pool.</li>
 * </ul>
 */
public class Main {

    private static volatile HttpServer activeServer;

    /**
     * Factory method creating and configuring the production HttpServer instance.
     *
     * @param port port to listen on (0 for ephemeral)
     * @return configured HttpServer
     * @throws IOException if server creation fails
     */
    public static HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // TASK-A1.1: Fixed thread pool executor with 20 worker threads
        server.setExecutor(Executors.newFixedThreadPool(20));

        // Restore operational business date from database configuration table (system_settings)
        SystemStateHolder.loadFromDatabase();

        // Filters: Global Exception Handler, OWASP Security Hardening, CORS, System Status (TASK-A3.1), JWT
        Filter exceptionFilter = new GlobalExceptionHandler();
        Filter securityFilter = new SecurityHardeningFilter();
        Filter corsFilter = new CorsFilter();
        Filter systemStatusFilter = new SystemStatusFilter();
        Filter jwtFilter = new JwtFilter();

        // TASK-A1.2 & TASK-A4.1: Unified Router with Deep Healthcheck
        Router router = new Router();
        router.addRoute("GET", "/api/v1/health", Main::handleHealthCheck);

        // TASK-A1.4: Explicit Authentication routes
        AuthController authController = new AuthController();
        router.addRoute("POST", "/api/v1/auth/login", (exchange, params) -> authController.handle(exchange));
        router.addRoute("GET", "/api/v1/protected/ping", (exchange, params) -> {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of(
                    "message", "pong",
                    "userId", exchange.getAttribute("userId") != null ? exchange.getAttribute("userId") : 0,
                    "username", exchange.getAttribute("username") != null ? exchange.getAttribute("username") : "",
                    "role", exchange.getAttribute("role") != null ? exchange.getAttribute("role") : ""
            ));
        });

        // TASK-A2.1, TASK-A2.2, TASK-A2.3: Core Domain Controllers & Explicit Routes
        quanlykhachsan.backend.room.RoomController roomController = new quanlykhachsan.backend.room.RoomController();
        quanlykhachsan.backend.customer.CustomerController customerController = new quanlykhachsan.backend.customer.CustomerController();
        quanlykhachsan.backend.booking.BookingController bookingController = new quanlykhachsan.backend.booking.BookingController();

        router.get("/api/v1/rooms", (ex, params) -> roomController.handleGetAll(ex));
        router.get("/api/v1/rooms/availability", (ex, params) -> roomController.handleGetAvailability(ex));

        router.post("/api/v1/customers", (ex, params) -> customerController.handleCreate(ex));
        router.get("/api/v1/customers", (ex, params) -> customerController.handleSearch(ex));

        router.get("/api/v1/bookings", (ex, params) -> bookingController.handleGetAll(ex));
        router.get("/api/v1/bookings/{id}", (ex, params) -> bookingController.handleGetById(ex, params));
        router.put("/api/v1/bookings/{id}", (ex, params) -> bookingController.handleUpdate(ex, params));
        router.post("/api/v1/bookings", (ex, params) -> bookingController.handleCreate(ex));
        router.post("/api/v1/bookings/{id}/cancel", (ex, params) -> bookingController.handleCancel(ex, params));

        // Mount controllers into Router (Unified Dispatcher)
        router.mount("/api/v1/auth/login", authController);
        router.mount("/api/v1/auth/register", authController);
        router.mount("/api/v1/rooms", roomController);
        router.mount("/api/v1/users/update-profile", new quanlykhachsan.backend.user.UserController());
        router.mount("/api/v1/users/change-password", new quanlykhachsan.backend.user.UserController());
        router.mount("/api/v1/users/update-theme", new quanlykhachsan.backend.user.UserController());
        router.mount("/api/v1/roles", new quanlykhachsan.backend.user.UserController());
        router.mount("/api/v1/users", new quanlykhachsan.backend.user.UserController());
        router.mount("/api/v1/reports", new quanlykhachsan.backend.report.ReportController());
        router.mount("/api/v1/customers", customerController);
        router.mount("/api/v1/bookings", bookingController);
        router.mount("/api/v1/payments", new quanlykhachsan.backend.booking.PaymentController());
        router.mount("/api/v1/invoices", new quanlykhachsan.backend.booking.InvoiceController());
        router.mount("/api/v1/reviews", new quanlykhachsan.backend.interaction.ReviewController());
        router.mount("/api/v1/chat", new quanlykhachsan.backend.interaction.ChatController());
        router.mount("/api/v1/promotions", new quanlykhachsan.backend.promotion.PromotionController());
        router.mount("/api/v1/loyalty", new quanlykhachsan.backend.customer.LoyaltyController());

        // TASK-A3.1: System Freeze Administrative Transition Endpoints
        router.post("/api/v1/system/freeze", (ex, params) -> {
            SystemStateHolder.setSystemFrozen(true);
            ApiResponseUtil.sendSuccess(ex, Map.of("status", "FROZEN"));
        });
        router.post("/api/v1/system/unfreeze", (ex, params) -> {
            SystemStateHolder.setSystemFrozen(false);
            ApiResponseUtil.sendSuccess(ex, Map.of("status", "UNFROZEN"));
        });

        // TASK-A3.2: Store-and-Forward POS Integration Sync Receiver
        PosSyncController posSyncController = new PosSyncController();
        router.post("/api/v1/pos/sync", posSyncController::handleSync);
        router.mount("/api/v1/pos/sync", posSyncController);

        // Register Router as single Root Context with complete Filter Chain (TASK-A4.1 & TASK-A3.1)
        var rootContext = server.createContext("/", router);
        rootContext.getFilters().addAll(Arrays.asList(exceptionFilter, securityFilter, corsFilter, systemStatusFilter, jwtFilter));

        return server;
    }

    /**
     * Deep Healthcheck Handler (TASK-A4.1).
     * <p>
     * 1. Validates MariaDB connectivity with {@code SELECT 1}.
     * 2. Retrieves HikariCP connection pool metrics (active, idle, total).
     * 3. Retrieves JVM memory metrics (heap_used_mb, heap_max_mb).
     * 4. Returns HTTP 200 (UP) if connected, or HTTP 503 (DOWN) on failure.
     */
    public static void handleHealthCheck(HttpExchange exchange, Map<String, String> params) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();

        boolean dbConnected = false;
        String dbError = null;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(1);
            try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    dbConnected = true;
                }
            }
        } catch (Exception e) {
            dbConnected = false;
            dbError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        if (dbConnected) {
            response.put("status", "UP");
            response.put("database", "CONNECTED");

            // HikariCP connection pool metrics
            Map<String, Object> poolMetrics = new LinkedHashMap<>();
            poolMetrics.put("active_connections", DatabaseConnection.getActiveConnections());
            poolMetrics.put("idle_connections", DatabaseConnection.getIdleConnections());
            poolMetrics.put("total_connections", DatabaseConnection.getTotalConnections());
            response.put("pool", poolMetrics);

            // JVM heap memory metrics
            long totalMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            long maxMemory = Runtime.getRuntime().maxMemory();
            long heapUsedMb = (totalMemory - freeMemory) / (1024 * 1024);
            long heapMaxMb = maxMemory / (1024 * 1024);

            Map<String, Object> jvmMetrics = new LinkedHashMap<>();
            jvmMetrics.put("heap_used_mb", heapUsedMb);
            jvmMetrics.put("heap_max_mb", heapMaxMb);
            response.put("jvm", jvmMetrics);

            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.getGson().toJson(response));
        } else {
            response.put("status", "DOWN");
            response.put("database", "DISCONNECTED");
            response.put("error", dbError);

            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.getGson().toJson(response));
        }
    }

    public static HttpServer getActiveServer() {
        return activeServer;
    }

    public static void main(String[] args) {
        try {
            // Đảm bảo cấu trúc Database đã được tạo hoặc cập nhật đầy đủ
            quanlykhachsan.backend.utils.UpdateDbUtility.main(new String[]{});

            // Khởi tạo Server lắng nghe tại cổng 8081
            activeServer = createServer(8081);

            // TASK-A4.1: Graceful Shutdown Hook with 5-second Request Draining
            // Order of Operations: Close socket first via server.stop(5) to reject new connections,
            // await pending in-flight requests, and finally close DatabaseConnection pool.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 [Shutdown] Initiating graceful draining (5s grace period, in-flight: "
                        + SecurityHardeningFilter.getInFlightRequests() + ")...");
                try {
                    if (activeServer != null) {
                        // 1. Close network socket first - prevents accepting new requests
                        activeServer.stop(5);
                        System.out.println("✔ [Shutdown] HttpServer network socket closed (no new requests accepted).");
                    }
                    // 2. Await in-flight requests with 5-second bounded deadline to prevent livelock
                    long deadline = System.currentTimeMillis() + 5000;
                    while (SecurityHardeningFilter.getInFlightRequests() > 0 && System.currentTimeMillis() < deadline) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    System.out.println("✔ [Shutdown] Request draining completed (remaining in-flight: "
                            + SecurityHardeningFilter.getInFlightRequests() + ").");
                } catch (Exception e) {
                    System.err.println("⚠ [Shutdown] Error during server shutdown: " + e.getMessage());
                } finally {
                    // 3. Finally close database pool safely
                    DatabaseConnection.closePool();
                    System.out.println("✔ [Shutdown] HikariCP Connection Pool closed cleanly.");
                }
            }, "GracefulShutdownHook"));

            System.out.println("🚀 Đang khởi động Backend Server API...");
            activeServer.start();


            // Khởi tạo các Filter
            Filter corsFilter = new CorsFilter();
            Filter jwtFilter = new JwtFilter();
            Filter exceptionFilter = new GlobalExceptionHandler();
            Filter systemStatusFilter = new SystemStatusFilter();
            Filter idempotencyFilter = new IdempotencyFilter();

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
            registerContext(server, "/api/v1/bookings", bookingController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
            registerContext(server, "/api/bookings", bookingController, exceptionFilter, corsFilter, systemStatusFilter, jwtFilter, idempotencyFilter);
            // Route Thanh toán (Payment)
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
            registerContext(server, "/api/v1/promotions", new quanlykhachsan.backend.promotion.PromotionController(), exceptionFilter, corsFilter, jwtFilter);
            // Route Khách hàng thân thiết (Loyalty)
            registerContext(server, "/api/v1/loyalty", new quanlykhachsan.backend.customer.LoyaltyController(), exceptionFilter, corsFilter, jwtFilter);

            // Thiết lập cấu hình mặc định và chạy server
            server.setExecutor(Executors.newFixedThreadPool(20));
            server.start();

            System.out.println("Server đang chạy thành công tại: http://localhost:8081/");
            System.out.println("Hãy mở Postman và test API: POST http://localhost:8081/api/v1/auth/login");


        } catch (IOException e) {
            System.err.println("Lỗi khi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

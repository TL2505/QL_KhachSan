package quanlykhachsan.stubs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import quanlykhachsan.BaseIntegrationTest;
import quanlykhachsan.backend.booking.FolioRoutingEngine;
import quanlykhachsan.backend.system.SystemStateHolder;
import quanlykhachsan.backend.utils.SystemStatusFilter;
import quanlykhachsan.backend.booking.PosSyncController;
import quanlykhachsan.backend.booking.PosSyncDao;

/**
 * Mock API Server for Frontend Unblocking (TASK-D1.2).
 * <p>
 * Implements the 10 Core Endpoints matching docs/dev-spec/api-contracts.md:
 * 1. POST /api/v1/auth/login
 * 2. GET  /api/v1/rooms
 * 3. GET  /api/v1/rooms/availability
 * 4. POST /api/v1/customers
 * 5. GET  /api/v1/bookings
 * 6. GET  /api/v1/bookings/{id}
 * 7. POST /api/v1/bookings (Requires Idempotency-Key)
 * 8. PUT  /api/v1/bookings/{id}
 * 9. POST /api/v1/bookings/{id}/cancel
 * 10. POST /api/v1/folios/{id}/pay (Requires Idempotency-Key)
 * <p>
 * Unblocks Frontend (Dev C) by serving http://localhost:8089 with strict
 * architectural compliance to standard JSON envelope, error format, and room statuses.
 */
public class MockApiServer {

    public static final int DEFAULT_PORT = 8089;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final int port;
    private HttpServer server;
    private ExecutorService executor;
    private boolean isRunning = false;

    // In-memory data store for stateful mocking
    private final Map<Integer, JsonObject> roomsMap = new ConcurrentHashMap<>();
    private final Map<Integer, JsonObject> bookingsMap = new ConcurrentHashMap<>();
    private final Map<Integer, JsonObject> customersMap = new ConcurrentHashMap<>();
    private final Map<String, CachedResponse> idempotencyStore = new ConcurrentHashMap<>();
    private final AtomicInteger nextBookingId = new AtomicInteger(100);
    private final AtomicInteger nextCustomerId = new AtomicInteger(50);

    // Atomic non-blocking state tracking for Optimistic Concurrency Control (OCC)
    private final Map<Integer, AtomicInteger> roomVersions = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicReference<String>> roomStatuses = new ConcurrentHashMap<>();

    // Regex routing patterns
    private static final Pattern BOOKING_ID_PATTERN = Pattern.compile("^/(?:api/v1|api)/bookings/([0-9a-zA-Z_-]+)$");
    private static final Pattern BOOKING_CANCEL_PATTERN = Pattern.compile("^/(?:api/v1|api)/bookings/([0-9a-zA-Z_-]+)/cancel$");
    private static final Pattern ROOM_CHECKIN_PATTERN = Pattern.compile("^/(?:api/v1|api)/rooms/([0-9a-zA-Z_-]+)/check-in$");
    private static final Pattern BOOKING_CHECKIN_PATTERN = Pattern.compile("^/(?:api/v1|api)/bookings/([0-9a-zA-Z_-]+)/check-in$");
    private static final Pattern FOLIO_PAY_PATTERN = Pattern.compile("^/(?:api/v1|api)/folios/([0-9a-zA-Z_-]+)/pay$");
    private static final Pattern FOLIO_CLOSE_PATTERN = Pattern.compile("^/(?:api/v1|api)/folios/([0-9a-zA-Z_-]+)/close$");
    private static final Pattern FOLIO_SPLIT_PATTERN = Pattern.compile("^/(?:api/v1|api)/folios/([0-9a-zA-Z_-]+)/split$");
    private static final Pattern FOLIO_ROUTING_PATTERN = Pattern.compile("^/(?:api/v1|api)/folios/([0-9a-zA-Z_-]+)/routing$");
    private static final Pattern FOLIO_CHARGES_PATTERN = Pattern.compile("^/(?:api/v1|api)/folios/([0-9a-zA-Z_-]+)/charges$");
    private static final Pattern FOLIO_GET_PATTERN = Pattern.compile("^/(?:api/v1|api)/folios/([0-9a-zA-Z_-]+)$");

    public static class CachedResponse {
        public final int statusCode;
        public final String responseBody;

        public CachedResponse(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }

    public MockApiServer() {
        this(DEFAULT_PORT);
    }

    public MockApiServer(int port) {
        this.port = port;
        resetState();
    }

    /**
     * Resets the in-memory mock datasets back to standard baseline.
     */
    public synchronized void resetState() {
        roomsMap.clear();
        bookingsMap.clear();
        customersMap.clear();
        idempotencyStore.clear();
        roomVersions.clear();
        roomStatuses.clear();
        FolioRoutingEngine.getInstance().reset();
        SystemStateHolder.reset();
        PosSyncDao.getInstance().reset();

        loadInitialRooms();
        loadInitialBookings();
        loadInitialCustomers();
    }

    private void loadInitialRooms() {
        // Enforces strictly the 6 allowed states: vacant_clean, vacant_dirty, occupied_clean, occupied_dirty, out_of_order, reserved
        addRoom(1, "101", 1, "Standard", 500000.0, "vacant_clean", 1);
        addRoom(2, "102", 1, "Standard", 500000.0, "vacant_dirty", 1);
        addRoom(3, "201", 2, "Deluxe", 1000000.0, "occupied_clean", 1);
        addRoom(4, "202", 2, "Deluxe", 1000000.0, "occupied_dirty", 1);
        addRoom(5, "301", 3, "Family", 1500000.0, "reserved", 1);
        addRoom(6, "302", 3, "Family", 1500000.0, "out_of_order", 1);

        // Pre-allocate rooms 7 to 30 as 'vacant_clean' for group bookings (e.g. Scenario 1: 20 rooms)
        for (int i = 7; i <= 30; i++) {
            String roomNum = String.valueOf(400 + (i - 6));
            addRoom(i, roomNum, (i % 3) + 1, (i % 3 == 0 ? "Standard" : (i % 3 == 1 ? "Deluxe" : "Family")), 1000000.0, "vacant_clean", 1);
        }
    }

    private void addRoom(int id, String number, int typeId, String typeName, double price, String status, int version) {
        JsonObject room = new JsonObject();
        room.addProperty("id", id);
        room.addProperty("roomNumber", number);
        room.addProperty("roomTypeId", typeId);
        room.addProperty("roomTypeName", typeName);
        room.addProperty("price", price);
        room.addProperty("status", status);
        room.addProperty("version", version);
        roomsMap.put(id, room);
        roomVersions.put(id, new AtomicInteger(version));
        roomStatuses.put(id, new AtomicReference<>(status));
    }

    private void loadInitialBookings() {
        addBooking(1, 1, "Nguyen Van A", 1, "101", "2026-10-10T14:00:00Z", "2026-10-13T12:00:00Z", 1500000.0, "checked_out", "Walk-in", 1);
        addBooking(2, 2, "Tran Thi B", 3, "201", "2026-10-14T14:00:00Z", "2026-10-16T12:00:00Z", 2000000.0, "checked_in", "OTA", 1);
        addBooking(3, 3, "Le Hoang C", 5, "301", "2026-10-20T14:00:00Z", "2026-10-25T12:00:00Z", 7500000.0, "confirmed", "Corporate", 1);
    }

    private void addBooking(int id, int customerId, String customerName, int roomId, String roomNumber,
                            String checkIn, String checkOut, double totalPrice, String status, String customerType, int version) {
        JsonObject b = new JsonObject();
        b.addProperty("id", id);
        b.addProperty("customerId", customerId);
        b.addProperty("customerName", customerName);
        b.addProperty("roomId", roomId);
        b.addProperty("roomNumber", roomNumber);
        b.addProperty("checkInDate", checkIn);
        b.addProperty("checkOutDate", checkOut);
        b.addProperty("totalPrice", totalPrice);
        b.addProperty("status", status);
        b.addProperty("customerType", customerType);
        b.addProperty("version", version);
        bookingsMap.put(id, b);
    }

    private void loadInitialCustomers() {
        addCustomer(1, "Nguyen Van A", "012345678912", "0901234567", "nva@email.com", "Ha Noi", true, 1500, "Gold");
        addCustomer(2, "Tran Thi B", "098765432109", "0987654321", "ttb@email.com", "Da Nang", false, 500, "Member");
        addCustomer(3, "Le Hoang C", "036985214701", "0912345678", "lhc@email.com", "TP.HCM", false, 200, "Member");
    }

    private void addCustomer(int id, String name, String idCard, String phone, String email, String address, boolean isVip, int points, String level) {
        JsonObject c = new JsonObject();
        c.addProperty("id", id);
        c.addProperty("fullName", name);
        c.addProperty("identityCard", idCard);
        c.addProperty("phone", phone);
        c.addProperty("email", email);
        c.addProperty("address", address);
        c.addProperty("isVip", isVip);
        c.addProperty("loyaltyPoints", points);
        c.addProperty("totalLoyaltyPoints", points);
        c.addProperty("loyaltyLevel", level);
        customersMap.put(id, c);
    }

    /**
     * Starts the Mock API HTTP Server.
     */
    public synchronized void start() throws IOException {
        if (isRunning) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newFixedThreadPool(10);
        server.setExecutor(executor);

        // Core Handler Registry with System Freeze Filter (TASK-A3.1)
        com.sun.net.httpserver.HttpContext rootContext = server.createContext("/", new DispatcherHandler());
        rootContext.getFilters().add(new SystemStatusFilter());

        server.start();
        isRunning = true;
        System.out.println("==================================================================");
        System.out.println("🚀 [MockApiServer] RUNNING on http://localhost:" + port);
        System.out.println("📡 Serving 10 Core Endpoints for Dev C Frontend Unblocking");
        System.out.println("==================================================================");
    }

    /**
     * Stops the server.
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        isRunning = false;
        System.out.println("🛑 [MockApiServer] Stopped.");
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://localhost:" + port;
    }

    public boolean isRunning() {
        return isRunning;
    }

    // =========================================================================
    // HTTP DISPATCHER & CORE HANDLERS
    // =========================================================================

    private class DispatcherHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Attach standard CORS headers
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key, Accept");
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

                String method = exchange.getRequestMethod().toUpperCase();
                String rawPath = exchange.getRequestURI().getPath();

                // 0. Handle CORS Preflight
                if ("OPTIONS".equals(method)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                // Check System Freeze Lockout (00:00 - 00:05) - TASK-A3.1
                if (SystemStateHolder.isSystemFrozen()) {
                    if (!SystemStatusFilter.isExemptEndpoint(rawPath) && SystemStatusFilter.isMutatingMethod(method)) {
                        SystemStatusFilter.sendServiceUnavailable(exchange);
                        return;
                    }
                }

                // Health check
                if (rawPath.equals("/api/v1/health") || rawPath.equals("/health")) {
                    sendSuccess(exchange, 200, "{\"status\":\"success\",\"data\":\"OK\"}");
                    return;
                }

                // System & Night Audit controls: /api/v1/system/* (TASK-A3.1 / TASK-B3.1)
                if (rawPath.startsWith("/api/v1/system/") || rawPath.startsWith("/system/")) {
                    handleSystemEndpoints(exchange, method, rawPath);
                    return;
                }

                // POS Sync: POST /api/v1/pos/sync (TASK-A3.2 / Scenario 3)
                if (matches(rawPath, "/api/v1/pos/sync", "/api/pos/sync")) {
                    if ("POST".equals(method)) {
                        handlePosSync(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 1. POST /api/v1/auth/login (and /api/auth/login)
                if (matches(rawPath, "/api/v1/auth/login", "/api/auth/login")) {
                    if ("POST".equals(method)) {
                        handleAuthLogin(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 2. GET /api/v1/rooms/availability (Must match before /api/v1/rooms)
                if (matches(rawPath, "/api/v1/rooms/availability", "/api/rooms/availability")) {
                    if ("GET".equals(method)) {
                        handleRoomsAvailability(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Room check-in: POST /api/v1/rooms/{id}/check-in
                Matcher roomCheckInMatcher = ROOM_CHECKIN_PATTERN.matcher(rawPath);
                if (roomCheckInMatcher.matches()) {
                    if ("POST".equals(method)) {
                        handleRoomCheckIn(exchange, roomCheckInMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 3. GET /api/v1/rooms (and /api/rooms)
                if (matches(rawPath, "/api/v1/rooms", "/api/rooms")) {
                    if ("GET".equals(method)) {
                        handleListRooms(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 4. POST /api/v1/customers (and /api/customers)
                if (matches(rawPath, "/api/v1/customers", "/api/customers")) {
                    if ("POST".equals(method)) {
                        handleCreateCustomer(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 5. POST /api/v1/bookings/{id}/cancel
                Matcher cancelMatcher = BOOKING_CANCEL_PATTERN.matcher(rawPath);
                if (cancelMatcher.matches()) {
                    if ("POST".equals(method)) {
                        handleCancelBooking(exchange, cancelMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 6. POST /api/v1/folios/{id}/pay (Requires Idempotency-Key)
                Matcher folioPayMatcher = FOLIO_PAY_PATTERN.matcher(rawPath);
                if (folioPayMatcher.matches()) {
                    if ("POST".equals(method)) {
                        handleFolioPay(exchange, folioPayMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Folio close: POST /api/v1/folios/{id}/close (Enforces Master Folio settlement guardrail)
                Matcher folioCloseMatcher = FOLIO_CLOSE_PATTERN.matcher(rawPath);
                if (folioCloseMatcher.matches()) {
                    if ("POST".equals(method)) {
                        handleFolioClose(exchange, folioCloseMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Folio split: POST /api/v1/folios/{id}/split
                Matcher folioSplitMatcher = FOLIO_SPLIT_PATTERN.matcher(rawPath);
                if (folioSplitMatcher.matches()) {
                    if ("POST".equals(method)) {
                        handleFolioSplit(exchange, folioSplitMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Folio routing: PUT /api/v1/folios/{id}/routing
                Matcher folioRoutingMatcher = FOLIO_ROUTING_PATTERN.matcher(rawPath);
                if (folioRoutingMatcher.matches()) {
                    if ("PUT".equals(method)) {
                        handleFolioRouting(exchange, folioRoutingMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Folio charges: POST /api/v1/folios/{id}/charges
                Matcher folioChargesMatcher = FOLIO_CHARGES_PATTERN.matcher(rawPath);
                if (folioChargesMatcher.matches()) {
                    if ("POST".equals(method)) {
                        handleFolioCharges(exchange, folioChargesMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Folio get: GET /api/v1/folios/{id}
                Matcher folioGetMatcher = FOLIO_GET_PATTERN.matcher(rawPath);
                if (folioGetMatcher.matches()) {
                    if ("GET".equals(method)) {
                        handleGetFolio(exchange, folioGetMatcher.group(1));
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Group booking: POST /api/v1/bookings/group
                if (matches(rawPath, "/api/v1/bookings/group", "/api/bookings/group")) {
                    if ("POST".equals(method)) {
                        handleCreateGroupBooking(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // Booking check-in: POST /api/v1/bookings/{id}/check-in or PUT /api/bookings/checkin/{id}
                Matcher bookingCheckInMatcher = BOOKING_CHECKIN_PATTERN.matcher(rawPath);
                if (bookingCheckInMatcher.matches() || rawPath.startsWith("/api/bookings/checkin/")) {
                    String bId = bookingCheckInMatcher.matches() ? bookingCheckInMatcher.group(1) : rawPath.substring(rawPath.lastIndexOf('/') + 1);
                    if ("POST".equals(method) || "PUT".equals(method)) {
                        handleBookingCheckIn(exchange, bId);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 7. GET or PUT /api/v1/bookings/{id}
                Matcher bookingIdMatcher = BOOKING_ID_PATTERN.matcher(rawPath);
                if (bookingIdMatcher.matches()) {
                    String bookingId = bookingIdMatcher.group(1);
                    if ("GET".equals(method)) {
                        handleGetBookingById(exchange, bookingId);
                    } else if ("PUT".equals(method)) {
                        handleUpdateBooking(exchange, bookingId);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 8. GET or POST /api/v1/bookings
                if (matches(rawPath, "/api/v1/bookings", "/api/bookings")) {
                    if ("GET".equals(method)) {
                        handleListBookings(exchange);
                    } else if ("POST".equals(method)) {
                        handleCreateBooking(exchange);
                    } else {
                        sendMethodNotAllowed(exchange);
                    }
                    return;
                }

                // 9. User Admin Endpoint: /api/v1/users, /api/v1/admin/users, /api/v1/staff
                if (matches(rawPath, "/api/v1/users", "/api/v1/admin/users", "/api/v1/staff", "/api/users")) {
                    handleUserAdmin(exchange);
                    return;
                }

                // Path Not Found
                sendError(exchange, 404, "ERR_404_NOT_FOUND", "Endpoint path not found: " + rawPath);

            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "ERR_500_INTERNAL_SERVER_ERROR", "Internal server error: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // ENDPOINT HANDLERS
    // =========================================================================

    /**
     * 1. POST /api/v1/auth/login
     */
    private void handleAuthLogin(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        String username = req.has("username") ? req.get("username").getAsString() : "";
        String password = req.has("password") ? req.get("password").getAsString() : "";

        if ("invalid_user".equals(username) || "wrong_password".equals(password)) {
            sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid username or password.");
            return;
        }

        JsonObject user = new JsonObject();
        user.addProperty("id", 1);
        user.addProperty("username", username.isEmpty() ? "admin_main" : username);
        user.addProperty("role", "admin");
        user.addProperty("fullName", "Administrator");
        user.addProperty("email", "admin@hotel.com");

        JsonObject data = new JsonObject();
        String token = BaseIntegrationTest.generateSignedJwt("Admin", user.get("username").getAsString(), 1);
        data.addProperty("token", token);
        data.add("user", user);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * 2. GET /api/v1/rooms
     */
    private void handleListRooms(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && isInvalidToken(exchange)) {
            sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
            return;
        }

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String statusFilter = query.get("status");

        JsonArray roomsArray = new JsonArray();
        for (Map.Entry<Integer, JsonObject> entry : roomsMap.entrySet()) {
            JsonObject room = entry.getValue();
            int id = entry.getKey();
            AtomicReference<String> statRef = roomStatuses.get(id);
            AtomicInteger verRef = roomVersions.get(id);
            if (statRef != null) room.addProperty("status", statRef.get());
            if (verRef != null) room.addProperty("version", verRef.get());
            if (statusFilter == null || statusFilter.equalsIgnoreCase(room.get("status").getAsString())) {
                roomsArray.add(room);
            }
        }

        JsonObject meta = new JsonObject();
        meta.addProperty("page", 1);
        meta.addProperty("size", 20);
        meta.addProperty("total", roomsArray.size());

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", roomsArray);
        resp.add("meta", meta);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * 3. GET /api/v1/rooms/availability
     */
    private void handleRoomsAvailability(HttpExchange exchange) throws IOException {
        JsonArray availableTypes = new JsonArray();

        // Standard Type
        JsonObject std = new JsonObject();
        std.addProperty("roomTypeId", 1);
        std.addProperty("roomTypeName", "Standard");
        std.addProperty("basePrice", 500000.0);
        JsonArray stdRooms = new JsonArray();
        for (JsonObject r : roomsMap.values()) {
            if (r.get("roomTypeId").getAsInt() == 1 && "vacant_clean".equals(r.get("status").getAsString())) {
                stdRooms.add(r);
            }
        }
        std.addProperty("availableCount", stdRooms.size());
        std.add("rooms", stdRooms);
        availableTypes.add(std);

        // Deluxe Type
        JsonObject dlx = new JsonObject();
        dlx.addProperty("roomTypeId", 2);
        dlx.addProperty("roomTypeName", "Deluxe");
        dlx.addProperty("basePrice", 1000000.0);
        JsonArray dlxRooms = new JsonArray();
        for (JsonObject r : roomsMap.values()) {
            if (r.get("roomTypeId").getAsInt() == 2 && "vacant_clean".equals(r.get("status").getAsString())) {
                dlxRooms.add(r);
            }
        }
        dlx.addProperty("availableCount", dlxRooms.size());
        dlx.add("rooms", dlxRooms);
        availableTypes.add(dlx);

        // Family Type
        JsonObject fam = new JsonObject();
        fam.addProperty("roomTypeId", 3);
        fam.addProperty("roomTypeName", "Family");
        fam.addProperty("basePrice", 1500000.0);
        JsonArray famRooms = new JsonArray();
        for (JsonObject r : roomsMap.values()) {
            if (r.get("roomTypeId").getAsInt() == 3 && "vacant_clean".equals(r.get("status").getAsString())) {
                famRooms.add(r);
            }
        }
        fam.addProperty("availableCount", famRooms.size());
        fam.add("rooms", famRooms);
        availableTypes.add(fam);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", availableTypes);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * 4. POST /api/v1/customers
     */
    private void handleCreateCustomer(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        String idCard = req.has("identityCard") ? req.get("identityCard").getAsString() : "";
        if ("DUPLICATE".equalsIgnoreCase(idCard)) {
            sendError(exchange, 409, "ERR_409_CONFLICT", "Identity card already registered.");
            return;
        }

        int newId = nextCustomerId.incrementAndGet();
        JsonObject customer = new JsonObject();
        customer.addProperty("id", newId);
        customer.addProperty("fullName", req.has("fullName") ? req.get("fullName").getAsString() : "New Guest");
        customer.addProperty("identityCard", idCard.isEmpty() ? "012345678999" : idCard);
        customer.addProperty("phone", req.has("phone") ? req.get("phone").getAsString() : "0987654321");
        customer.addProperty("email", req.has("email") ? req.get("email").getAsString() : "guest@hotel.com");
        customer.addProperty("address", req.has("address") ? req.get("address").getAsString() : "Vietnam");
        customer.addProperty("isVip", req.has("isVip") && req.get("isVip").getAsBoolean());
        customer.addProperty("loyaltyPoints", 0);
        customer.addProperty("totalLoyaltyPoints", 0);
        customer.addProperty("loyaltyLevel", "Member");

        customersMap.put(newId, customer);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", customer);

        sendSuccess(exchange, 201, gson.toJson(resp));
    }

    /**
     * 5. GET /api/v1/bookings
     */
    private void handleListBookings(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        String statusFilter = query.get("status");

        JsonArray arr = new JsonArray();
        for (JsonObject b : bookingsMap.values()) {
            if (statusFilter == null || statusFilter.equalsIgnoreCase(b.get("status").getAsString())) {
                arr.add(b);
            }
        }

        JsonObject meta = new JsonObject();
        meta.addProperty("page", 1);
        meta.addProperty("size", 20);
        meta.addProperty("total", arr.size());

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", arr);
        resp.add("meta", meta);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * 6. GET /api/v1/bookings/{id}
     */
    private void handleGetBookingById(HttpExchange exchange, String bookingIdStr) throws IOException {
        try {
            int id = Integer.parseInt(bookingIdStr);
            JsonObject b = bookingsMap.get(id);
            if (b != null) {
                JsonObject resp = new JsonObject();
                resp.addProperty("status", "success");
                resp.add("data", b);
                sendSuccess(exchange, 200, gson.toJson(resp));
                return;
            }
        } catch (NumberFormatException ignored) {
            // Check string ID
        }

        sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking ID not found: " + bookingIdStr);
    }

    /**
     * 7. POST /api/v1/bookings (Requires Idempotency-Key)
     */
    private void handleCreateBooking(HttpExchange exchange) throws IOException {
        // 1. Strict Bearer JWT Authentication (Parity with production JwtFilter)
        String role = extractUserRole(exchange);
        if (role == null) {
            sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Missing or invalid Bearer authorization token.");
            return;
        }

        // 2. RBAC Enforcement (TASK-D2.2): Housekeeping and F&B cannot create bookings
        if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
            sendError(exchange, 403, "ERR_403_FORBIDDEN",
                    "Forbidden: User role '" + role + "' does not have permission to create bookings.");
            return;
        }

        // 3. Enforce Idempotency-Key
        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Missing required header: Idempotency-Key");
            return;
        }

        // Return cached idempotent response if duplicate
        CachedResponse cached = idempotencyStore.get(idempotencyKey.trim());
        if (cached != null) {
            System.out.println("🔄 [MockApiServer] Idempotent replay for key: " + idempotencyKey);
            sendRaw(exchange, cached.statusCode, cached.responseBody);
            return;
        }

        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        int roomId = req.has("roomId") ? req.get("roomId").getAsInt() : 1;
        JsonObject targetRoom = roomsMap.get(roomId);

        if (targetRoom == null) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Room not found: " + roomId);
            return;
        }

        AtomicInteger roomVer = roomVersions.get(roomId);
        AtomicReference<String> roomStat = roomStatuses.get(roomId);

        // 4. Non-blocking Optimistic Concurrency Control (OCC)
        // Emulates SQL: UPDATE rooms SET status = 'reserved', version = version + 1
        //               WHERE id = ? AND version = ? AND status = 'vacant_clean'
        String currentStatus = roomStat != null ? roomStat.get() : targetRoom.get("status").getAsString();
        int currentVersion = roomVer != null ? roomVer.get() : targetRoom.get("version").getAsInt();

        // Scenario 5 requirement: Only 'vacant_clean' rooms can be reserved
        if (!"vacant_clean".equalsIgnoreCase(currentStatus)) {
            sendError(exchange, 409, "ERR_409_CONFLICT",
                    "Room state changed by another concurrent transaction (OptimisticLockException). Room "
                            + targetRoom.get("roomNumber").getAsString() + " status is " + currentStatus);
            return;
        }

        // Check version if provided
        int expectedVersion = req.has("roomVersion") ? req.get("roomVersion").getAsInt() : currentVersion;
        if (expectedVersion != currentVersion) {
            sendError(exchange, 409, "ERR_409_CONFLICT",
                    "Room version mismatch: expected " + expectedVersion + " but was " + currentVersion);
            return;
        }

        // Non-blocking Hardware-level Atomic Compare-And-Swap (zero Java mutex locks):
        // Exactly ONE thread succeeds in CAS (rowsAffected == 1); remaining 49 threads fail (rowsAffected == 0)
        boolean won = roomVer != null && roomVer.compareAndSet(expectedVersion, expectedVersion + 1);
        if (!won) {
            sendError(exchange, 409, "ERR_409_CONFLICT",
                    "Room state changed by another concurrent transaction (OptimisticLockException). rowsUpdated=0");
            return;
        }

        // Winner atomically transitions room status to 'reserved'
        if (roomStat != null) {
            roomStat.set("reserved");
        }
        targetRoom.addProperty("status", "reserved");
        targetRoom.addProperty("version", expectedVersion + 1);

        int newBookingId = nextBookingId.incrementAndGet();
        int customerId = req.has("customerId") ? req.get("customerId").getAsInt() : 1;
        JsonObject cust = customersMap.get(customerId);
        String custName = cust != null ? cust.get("fullName").getAsString() : "Valued Guest";

        JsonObject booking = new JsonObject();
        booking.addProperty("id", newBookingId);
        booking.addProperty("customerId", customerId);
        booking.addProperty("customerName", custName);
        booking.addProperty("roomId", roomId);
        booking.addProperty("roomNumber", targetRoom.get("roomNumber").getAsString());
        booking.addProperty("checkInDate", req.has("checkInDate") ? req.get("checkInDate").getAsString() : "2026-10-20T14:00:00Z");
        booking.addProperty("checkOutDate", req.has("checkOutDate") ? req.get("checkOutDate").getAsString() : "2026-10-22T12:00:00Z");
        booking.addProperty("totalPrice", req.has("totalPrice") ? req.get("totalPrice").getAsDouble() : 1000000.0);
        booking.addProperty("status", "confirmed");
        booking.addProperty("customerType", req.has("customerType") ? req.get("customerType").getAsString() : "Walk-in");
        booking.addProperty("version", 1);

        bookingsMap.put(newBookingId, booking);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", booking);

        String jsonOut = gson.toJson(resp);
        idempotencyStore.put(idempotencyKey.trim(), new CachedResponse(201, jsonOut));

        sendSuccess(exchange, 201, jsonOut);
    }

    /**
     * 8. PUT /api/v1/bookings/{id}
     */
    private void handleUpdateBooking(HttpExchange exchange, String bookingIdStr) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            if (isInvalidToken(exchange)) {
                sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
                return;
            }
            String role = extractUserRole(exchange);
            if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
                sendError(exchange, 403, "ERR_403_FORBIDDEN",
                        "Forbidden: User role '" + role + "' does not have permission to modify bookings.");
                return;
            }
        }

        int id;
        try {
            id = Integer.parseInt(bookingIdStr);
        } catch (NumberFormatException e) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Invalid booking ID: " + bookingIdStr);
            return;
        }

        JsonObject b = bookingsMap.get(id);
        if (b == null) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking ID not found: " + bookingIdStr);
            return;
        }

        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        if (req.has("checkInDate")) b.addProperty("checkInDate", req.get("checkInDate").getAsString());
        if (req.has("checkOutDate")) b.addProperty("checkOutDate", req.get("checkOutDate").getAsString());
        if (req.has("totalPrice")) b.addProperty("totalPrice", req.get("totalPrice").getAsDouble());
        if (req.has("roomId")) {
            int newRoomId = req.get("roomId").getAsInt();
            b.addProperty("roomId", newRoomId);
            JsonObject room = roomsMap.get(newRoomId);
            if (room != null) {
                b.addProperty("roomNumber", room.get("roomNumber").getAsString());
            }
        }
        int curVer = b.has("version") ? b.get("version").getAsInt() : 1;
        b.addProperty("version", curVer + 1);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", b);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * 9. POST /api/v1/bookings/{id}/cancel
     */
    private void handleCancelBooking(HttpExchange exchange, String bookingIdStr) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            if (isInvalidToken(exchange)) {
                sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
                return;
            }
            String role = extractUserRole(exchange);
            if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
                sendError(exchange, 403, "ERR_403_FORBIDDEN",
                        "Forbidden: User role '" + role + "' does not have permission to cancel bookings.");
                return;
            }
        }

        int id;
        try {
            id = Integer.parseInt(bookingIdStr);
        } catch (NumberFormatException e) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Invalid booking ID: " + bookingIdStr);
            return;
        }

        JsonObject b = bookingsMap.get(id);
        if (b == null) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking ID not found: " + bookingIdStr);
            return;
        }

        b.addProperty("status", "cancelled");
        int roomId = b.has("roomId") ? b.get("roomId").getAsInt() : -1;
        JsonObject room = roomsMap.get(roomId);
        if (room != null) {
            room.addProperty("status", "vacant_clean");
        }

        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("status", "cancelled");
        data.addProperty("message", "Booking " + id + " has been cancelled successfully.");

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * 10. POST /api/v1/folios/{id}/pay (Requires Idempotency-Key)
     */
    private void handleFolioPay(HttpExchange exchange, String folioIdStr) throws IOException {
        // RBAC Enforcement (TASK-D2.2): Housekeeping and F&B cannot process payments
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            if (isInvalidToken(exchange)) {
                sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
                return;
            }
            String role = extractUserRole(exchange);
            if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
                sendError(exchange, 403, "ERR_403_FORBIDDEN",
                        "Forbidden: User role '" + role + "' does not have permission to access payment endpoint.");
                return;
            }
        }

        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Missing required header: Idempotency-Key");
            return;
        }

        CachedResponse cached = idempotencyStore.get(idempotencyKey.trim());
        if (cached != null) {
            System.out.println("🔄 [MockApiServer] Idempotent replay for folio pay: " + idempotencyKey);
            sendRaw(exchange, cached.statusCode, cached.responseBody);
            return;
        }

        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        double amount = req.has("amount") ? req.get("amount").getAsDouble() : 1500000.0;
        String method = req.has("paymentMethod") ? req.get("paymentMethod").getAsString() : "credit_card";

        JsonObject data = new JsonObject();
        data.addProperty("folioId", folioIdStr);
        data.addProperty("transactionId", "TXN-" + System.currentTimeMillis());
        data.addProperty("amount", amount);
        data.addProperty("paymentMethod", method);
        data.addProperty("status", "paid");
        data.addProperty("timestamp", Instant.now().toString());

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        String jsonOut = gson.toJson(resp);
        idempotencyStore.put(idempotencyKey.trim(), new CachedResponse(200, jsonOut));

        sendSuccess(exchange, 200, jsonOut);
    }

    /**
     * 11. GET/POST /api/v1/users (User Administration Endpoint - TASK-D2.2)
     * Enforces RBAC: Only 'Admin' is permitted. FrontDesk, Housekeeping, F&B receive 403 Forbidden.
     */
    private void handleUserAdmin(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || isInvalidToken(exchange)) {
            sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Missing or invalid Bearer authorization token.");
            return;
        }

        String role = extractUserRole(exchange);
        if (role == null || !"Admin".equalsIgnoreCase(role)) {
            sendError(exchange, 403, "ERR_403_FORBIDDEN",
                    "Forbidden: User role '" + (role != null ? role : "Anonymous") + "' does not have permission to access user administration.");
            return;
        }

        JsonArray users = new JsonArray();
        JsonObject u1 = new JsonObject();
        u1.addProperty("id", 1);
        u1.addProperty("username", "admin_main");
        u1.addProperty("role", "Admin");
        u1.addProperty("fullName", "Administrator");
        users.add(u1);

        JsonObject u2 = new JsonObject();
        u2.addProperty("id", 2);
        u2.addProperty("username", "staff_frontdesk");
        u2.addProperty("role", "FrontDesk");
        u2.addProperty("fullName", "Front Desk Staff");
        users.add(u2);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", users);
        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    // =========================================================================
    // SCENARIO 1 ENDPOINTS (TASK-D3.1 / TASK-B3.2)
    // =========================================================================

    /**
     * POST /api/v1/bookings/group (Group Booking of 20 Rooms)
     */
    private void handleCreateGroupBooking(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            if (isInvalidToken(exchange)) {
                sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
                return;
            }
            String role = extractUserRole(exchange);
            if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
                sendError(exchange, 403, "ERR_403_FORBIDDEN",
                        "Forbidden: User role '" + role + "' does not have permission to create group bookings.");
                return;
            }
        }

        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        String groupName = req.has("groupName") ? req.get("groupName").getAsString() : "Group Reservation";
        int customerId = req.has("customerId") ? req.get("customerId").getAsInt() : 1;
        String checkIn = req.has("checkInDate") ? req.get("checkInDate").getAsString() : "2026-10-25T14:00:00Z";
        String checkOut = req.has("checkOutDate") ? req.get("checkOutDate").getAsString() : "2026-10-28T12:00:00Z";

        JsonArray roomIdsArray = req.has("roomIds") ? req.getAsJsonArray("roomIds") : new JsonArray();
        List<Integer> roomIds = new ArrayList<>();
        for (JsonElement re : roomIdsArray) {
            roomIds.add(re.getAsInt());
        }

        if (roomIds.isEmpty()) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Group booking requires at least one room.");
            return;
        }

        // Verify all target rooms exist and are vacant_clean
        for (int rId : roomIds) {
            JsonObject room = roomsMap.get(rId);
            if (room == null) {
                sendError(exchange, 404, "ERR_404_NOT_FOUND", "Room not found: " + rId);
                return;
            }
            String stat = roomStatuses.get(rId) != null ? roomStatuses.get(rId).get() : room.get("status").getAsString();
            if (!"vacant_clean".equalsIgnoreCase(stat)) {
                sendError(exchange, 409, "ERR_409_CONFLICT",
                        "Room " + room.get("roomNumber").getAsString() + " is not vacant_clean (status: " + stat + ")");
                return;
            }
        }

        int groupId = 100 + (int) (System.currentTimeMillis() % 1000);
        String masterFolioId = "FOLIO-MASTER-GRP-" + groupId;
        JsonArray createdBookings = new JsonArray();
        List<Integer> createdBookingIds = new ArrayList<>();
        double totalGroupPrice = 0.0;

        for (int rId : roomIds) {
            JsonObject room = roomsMap.get(rId);
            AtomicReference<String> statRef = roomStatuses.get(rId);
            AtomicInteger verRef = roomVersions.get(rId);

            if (statRef != null) statRef.set("reserved");
            room.addProperty("status", "reserved");
            int newVer = verRef != null ? verRef.incrementAndGet() : 2;
            room.addProperty("version", newVer);

            int bId = nextBookingId.incrementAndGet();
            createdBookingIds.add(bId);
            double price = room.get("price").getAsDouble();
            totalGroupPrice += price;

            JsonObject b = new JsonObject();
            b.addProperty("id", bId);
            b.addProperty("customerId", customerId);
            b.addProperty("customerName", groupName);
            b.addProperty("roomId", rId);
            b.addProperty("roomNumber", room.get("roomNumber").getAsString());
            b.addProperty("checkInDate", checkIn);
            b.addProperty("checkOutDate", checkOut);
            b.addProperty("totalPrice", price);
            b.addProperty("status", "confirmed");
            b.addProperty("customerType", "Corporate");
            b.addProperty("version", 1);
            b.addProperty("masterFolioId", masterFolioId);
            b.addProperty("guestFolioId", "FOLIO-GUEST-" + bId);

            bookingsMap.put(bId, b);
            createdBookings.add(b);
        }

        // Establish dual-folio split routing for all rooms and group master folio
        FolioRoutingEngine.getInstance().splitGroupFolio(masterFolioId, createdBookingIds, totalGroupPrice);

        JsonObject data = new JsonObject();
        data.addProperty("groupId", "GRP-" + groupId);
        data.addProperty("groupName", groupName);
        data.addProperty("masterFolioId", masterFolioId);
        data.addProperty("totalRooms", roomIds.size());
        data.addProperty("totalPrice", totalGroupPrice);
        data.add("bookings", createdBookings);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 201, gson.toJson(resp));
    }

    /**
     * POST /api/v1/rooms/{id}/check-in (Independent early check-in for specific room)
     */
    private void handleRoomCheckIn(HttpExchange exchange, String roomIdStr) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            if (isInvalidToken(exchange)) {
                sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
                return;
            }
            String role = extractUserRole(exchange);
            if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
                sendError(exchange, 403, "ERR_403_FORBIDDEN",
                        "Forbidden: User role '" + role + "' does not have permission to check-in rooms.");
                return;
            }
        }

        int roomId;
        try {
            roomId = Integer.parseInt(roomIdStr);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid room ID: " + roomIdStr);
            return;
        }

        JsonObject room = roomsMap.get(roomId);
        if (room == null) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Room not found: " + roomId);
            return;
        }

        AtomicReference<String> statRef = roomStatuses.get(roomId);
        AtomicInteger verRef = roomVersions.get(roomId);

        // Transition room status to occupied_clean
        if (statRef != null) statRef.set("occupied_clean");
        room.addProperty("status", "occupied_clean");
        int newVer = verRef != null ? verRef.incrementAndGet() : (room.has("version") ? room.get("version").getAsInt() + 1 : 2);
        room.addProperty("version", newVer);

        // Update associated booking status to checked_in
        int matchedBookingId = 0;
        for (JsonObject b : bookingsMap.values()) {
            if (b.get("roomId").getAsInt() == roomId && "confirmed".equalsIgnoreCase(b.get("status").getAsString())) {
                b.addProperty("status", "checked_in");
                matchedBookingId = b.get("id").getAsInt();
                break;
            }
        }

        JsonObject data = new JsonObject();
        data.addProperty("roomId", roomId);
        data.addProperty("roomNumber", room.get("roomNumber").getAsString());
        data.addProperty("status", "occupied_clean");
        data.addProperty("version", newVer);
        data.addProperty("bookingId", matchedBookingId);
        data.addProperty("bookingStatus", "checked_in");

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * POST /api/v1/bookings/{id}/check-in (Check-in by booking ID)
     */
    private void handleBookingCheckIn(HttpExchange exchange, String bookingIdStr) throws IOException {
        int bookingId;
        try {
            bookingId = Integer.parseInt(bookingIdStr);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid booking ID: " + bookingIdStr);
            return;
        }

        JsonObject booking = bookingsMap.get(bookingId);
        if (booking == null) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking not found: " + bookingId);
            return;
        }

        int roomId = booking.get("roomId").getAsInt();
        JsonObject room = roomsMap.get(roomId);
        int newVer = 2;
        if (room != null) {
            AtomicReference<String> statRef = roomStatuses.get(roomId);
            AtomicInteger verRef = roomVersions.get(roomId);
            if (statRef != null) statRef.set("occupied_clean");
            room.addProperty("status", "occupied_clean");
            newVer = verRef != null ? verRef.incrementAndGet() : 2;
            room.addProperty("version", newVer);
        }
        booking.addProperty("status", "checked_in");

        JsonObject data = new JsonObject();
        data.addProperty("bookingId", bookingId);
        data.addProperty("roomId", roomId);
        data.addProperty("roomStatus", "occupied_clean");
        data.addProperty("bookingStatus", "checked_in");
        data.addProperty("version", newVer);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * POST /api/v1/folios/{id}/split (Dual-folio split configuration)
     */
    private void handleFolioSplit(HttpExchange exchange, String folioOrBookingId) throws IOException {
        int bookingId = 1;
        try {
            bookingId = Integer.parseInt(folioOrBookingId.replaceAll("\\D+", ""));
        } catch (Exception ignored) {}

        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);
        double roomRate = req.has("roomRate") ? req.get("roomRate").getAsDouble() : 1000000.0;

        FolioRoutingEngine.SplitRoutingPair pair = FolioRoutingEngine.getInstance().splitFolio(bookingId, roomRate);

        JsonObject routing = new JsonObject();
        routing.addProperty("roomCharges", pair.getMasterFolioId());
        routing.addProperty("incidentals", pair.getGuestFolioId());

        JsonObject data = new JsonObject();
        data.addProperty("bookingId", bookingId);
        data.addProperty("masterFolioId", pair.getMasterFolioId());
        data.addProperty("guestFolioId", pair.getGuestFolioId());
        data.add("routing", routing);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * PUT /api/v1/folios/{id}/routing (Configure folio routing rules)
     */
    private void handleFolioRouting(HttpExchange exchange, String folioId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("folioId", folioId);
        data.addProperty("status", "active");
        data.addProperty("rule", "SPLIT_ROOM_MASTER_INCIDENTALS_GUEST");

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * POST /api/v1/folios/{id}/charges (Post charge with automatic incidental routing)
     */
    private void handleFolioCharges(HttpExchange exchange, String folioId) throws IOException {
        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        String catStr = req.has("category") ? req.get("category").getAsString().toUpperCase() : "INCIDENTAL";
        String desc = req.has("description") ? req.get("description").getAsString() : "Incidentals charge";
        double amount = req.has("amount") ? req.get("amount").getAsDouble() : 100000.0;
        int bookingId = req.has("bookingId") ? req.get("bookingId").getAsInt() : 0;
        if (bookingId == 0) {
            try {
                bookingId = Integer.parseInt(folioId.replaceAll("\\D+", ""));
            } catch (Exception ignored) {
                bookingId = 1;
            }
        }

        FolioRoutingEngine.ChargeCategory cat;
        try {
            cat = FolioRoutingEngine.ChargeCategory.valueOf(catStr);
        } catch (IllegalArgumentException e) {
            cat = FolioRoutingEngine.ChargeCategory.INCIDENTAL;
        }

        FolioRoutingEngine.ChargeItem chargeItem = FolioRoutingEngine.getInstance().routeChargeByBooking(bookingId, cat, desc, amount);

        JsonObject data = new JsonObject();
        data.addProperty("chargeId", chargeItem.getChargeId());
        data.addProperty("category", chargeItem.getCategory().name());
        data.addProperty("description", chargeItem.getDescription());
        data.addProperty("amount", chargeItem.getAmount());
        data.addProperty("routedTo", chargeItem.getRoutedFolioId());
        data.addProperty("isIncidental", FolioRoutingEngine.isIncidental(cat));
        data.addProperty("timestamp", chargeItem.getTimestamp());

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * GET /api/v1/folios/{id} (Inspect folio balance, status, and charges)
     */
    private void handleGetFolio(HttpExchange exchange, String folioId) throws IOException {
        FolioRoutingEngine.Folio folio = FolioRoutingEngine.getInstance().getFolio(folioId);
        if (folio == null) {
            try {
                int bId = Integer.parseInt(folioId);
                FolioRoutingEngine.SplitRoutingPair pair = FolioRoutingEngine.getInstance().getPairByBooking(bId);
                if (pair != null) {
                    folio = FolioRoutingEngine.getInstance().getFolio(pair.getMasterFolioId());
                }
            } catch (Exception ignored) {}
        }

        if (folio == null) {
            sendError(exchange, 404, "ERR_404_NOT_FOUND", "Folio not found: " + folioId);
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("folioId", folio.getFolioId());
        data.addProperty("bookingId", folio.getBookingId());
        data.addProperty("type", folio.getType().name());
        data.addProperty("status", folio.getStatus().name());
        data.addProperty("balance", folio.getBalance());
        data.addProperty("paidAmount", folio.getPaidAmount());

        JsonArray chargesArr = new JsonArray();
        for (FolioRoutingEngine.ChargeItem chg : folio.getCharges()) {
            JsonObject ci = new JsonObject();
            ci.addProperty("chargeId", chg.getChargeId());
            ci.addProperty("category", chg.getCategory().name());
            ci.addProperty("description", chg.getDescription());
            ci.addProperty("amount", chg.getAmount());
            ci.addProperty("routedFolioId", chg.getRoutedFolioId());
            ci.addProperty("businessDate", chg.getBusinessDate());
            ci.addProperty("timestamp", chg.getTimestamp());
            if (chg.getOfflineTimestamp() != null) {
                ci.addProperty("offlineTimestamp", chg.getOfflineTimestamp());
            }
            chargesArr.add(ci);
        }
        data.add("charges", chargesArr);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    /**
     * POST /api/v1/folios/{id}/close (Enforces Master Folio Settlement Block when Guest Incidentals are Unpaid)
     */
    private void handleFolioClose(HttpExchange exchange, String folioId) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            if (isInvalidToken(exchange)) {
                sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
                return;
            }
            String role = extractUserRole(exchange);
            if ("Housekeeping".equalsIgnoreCase(role) || "F&B".equalsIgnoreCase(role)) {
                sendError(exchange, 403, "ERR_403_FORBIDDEN",
                        "Forbidden: User role '" + role + "' does not have permission to close folios.");
                return;
            }
        }

        // Verify Master Folio Settlement Guardrail (DoD of TASK-B3.2 / Scenario 1)
        boolean canClose = FolioRoutingEngine.getInstance().canSettleMasterFolio(folioId);
        if (!canClose) {
            sendError(exchange, 400, "ERR_400_BAD_REQUEST",
                    "Cannot close Master Folio '" + folioId + "': Associated guest folio has unpaid incidental charges.");
            return;
        }

        FolioRoutingEngine.Folio folio = FolioRoutingEngine.getInstance().getFolio(folioId);
        if (folio != null) {
            try {
                folio.closeFolio();
            } catch (IllegalStateException e) {
                sendError(exchange, 400, "ERR_400_BAD_REQUEST", e.getMessage());
                return;
            }
        }

        JsonObject data = new JsonObject();
        data.addProperty("folioId", folioId);
        data.addProperty("status", "CLOSED");
        data.addProperty("message", "Master folio closed successfully.");

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        sendSuccess(exchange, 200, gson.toJson(resp));
    }

    // =========================================================================
    // SCENARIO 3 ENDPOINTS (TASK-D3.2 / TASK-A3.1 / TASK-A3.2)
    // =========================================================================

    /**
     * POST /api/v1/pos/sync (Offline POS Store-and-Forward Sync Receiver)
     */
    private void handlePosSync(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && isInvalidToken(exchange)) {
            sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid Bearer authorization token.");
            return;
        }

        String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        String body = readBody(exchange);
        JsonObject req = parseJsonObject(body);

        PosSyncController.SyncResult result = PosSyncController.getInstance().processSync(req, idempotencyKey);
        if (result.success) {
            sendSuccess(exchange, result.statusCode, result.responseJson);
        } else {
            sendRaw(exchange, result.statusCode, result.responseJson);
        }
    }

    /**
     * /api/v1/system/* (System Freeze, Unfreeze, and Night Audit Lifecycle)
     */
    private void handleSystemEndpoints(HttpExchange exchange, String method, String rawPath) throws IOException {
        if (rawPath.endsWith("/freeze") && "POST".equals(method)) {
            SystemStateHolder.setFrozen(true);
            JsonObject data = new JsonObject();
            data.addProperty("frozen", true);
            data.addProperty("business_date", SystemStateHolder.getBusinessDate().toString());
            data.addProperty("message", "System frozen for Night Audit (00:00 - 00:05).");
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "success");
            resp.add("data", data);
            sendSuccess(exchange, 200, gson.toJson(resp));
            return;
        }

        if (rawPath.endsWith("/unfreeze") && "POST".equals(method)) {
            SystemStateHolder.setFrozen(false);
            JsonObject data = new JsonObject();
            data.addProperty("frozen", false);
            data.addProperty("business_date", SystemStateHolder.getBusinessDate().toString());
            data.addProperty("message", "System unfrozen.");
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "success");
            resp.add("data", data);
            sendSuccess(exchange, 200, gson.toJson(resp));
            return;
        }

        if (rawPath.endsWith("/advance-date") && "POST".equals(method)) {
            SystemStateHolder.advanceBusinessDate();
            JsonObject data = new JsonObject();
            data.addProperty("business_date", SystemStateHolder.getBusinessDate().toString());
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "success");
            resp.add("data", data);
            sendSuccess(exchange, 200, gson.toJson(resp));
            return;
        }

        if (rawPath.endsWith("/night-audit") && "POST".equals(method)) {
            SystemStateHolder.advanceBusinessDate();
            SystemStateHolder.setFrozen(false);
            JsonObject data = new JsonObject();
            data.addProperty("status", "COMPLETED");
            data.addProperty("frozen", false);
            data.addProperty("business_date", SystemStateHolder.getBusinessDate().toString());
            data.addProperty("message", "7-step Night Audit batch completed successfully.");
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "success");
            resp.add("data", data);
            sendSuccess(exchange, 200, gson.toJson(resp));
            return;
        }

        if (rawPath.endsWith("/status") && "GET".equals(method)) {
            JsonObject data = new JsonObject();
            data.addProperty("frozen", SystemStateHolder.isSystemFrozen());
            data.addProperty("business_date", SystemStateHolder.getBusinessDate().toString());
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "success");
            resp.add("data", data);
            sendSuccess(exchange, 200, gson.toJson(resp));
            return;
        }

        sendError(exchange, 404, "ERR_404_NOT_FOUND", "System endpoint not found: " + rawPath);
    }

    /**
     * Extracts user role from Bearer JWT token or mock token payload.
     */
    /**
     * Extracts user role from Bearer JWT token using cryptographic HMAC-SHA256 signature verification.
     */
    public String extractUserRole(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }

        try {
            SecretKey key = BaseIntegrationTest.getSigningKey();
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.get("role") != null) {
                return claims.get("role", String.class);
            }
        } catch (Exception e) {
            // Cryptographic signature invalid, expired, or tampered
            return null;
        }

        return null;
    }

    /**
     * Checks if Bearer token is syntactically invalid, expired, or has invalid cryptographic signature.
     */
    public boolean isInvalidToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) return false;
        if (!authHeader.startsWith("Bearer ")) return true;
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) return true;

        try {
            SecretKey key = BaseIntegrationTest.getSigningKey();
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    // =========================================================================
    // UTILITY METHODS & RESPONSE BUILDERS
    // =========================================================================

    private boolean matches(String path, String... candidates) {
        for (String c : candidates) {
            if (c.equals(path)) return true;
        }
        return false;
    }

    private void sendSuccess(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        sendRaw(exchange, statusCode, responseJson);
    }

    private void sendError(HttpExchange exchange, int statusCode, String errorCode, String message) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("error_code", errorCode);
        err.addProperty("message", message);
        err.addProperty("timestamp", Instant.now().toString());
        sendRaw(exchange, statusCode, gson.toJson(err));
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed: " + exchange.getRequestMethod());
    }

    private void sendRaw(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private JsonObject parseJsonObject(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonElement el = JsonParser.parseString(body);
            if (el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (idx > 0) {
                map.put(pair.substring(0, idx), "");
            }
        }
        return map;
    }

    /**
     * Helper to load JSON mock files from classpath resources or file fallback.
     */
    public static String loadMockResource(String filename) {
        // 1. Classpath resource
        try (InputStream is = MockApiServer.class.getResourceAsStream("/mock-server/" + filename)) {
            if (is != null) {
                return readStream(is);
            }
        } catch (Exception ignored) {
        }

        // 2. Local filesystem fallbacks
        String[] candidatePaths = {
            "src/test/resources/mock-server/" + filename,
            "test/resources/mock-server/" + filename,
            filename
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists()) {
                try (InputStream is = new FileInputStream(f)) {
                    return readStream(is);
                } catch (Exception ignored) {
                }
            }
        }

        return "{}";
    }

    private static String readStream(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    // =========================================================================
    // STANDALONE MAIN EXECUTION
    // =========================================================================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            MockApiServer mockServer = new MockApiServer(port);
            mockServer.start();

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(mockServer::stop));

            System.out.println("Press Ctrl+C to terminate Mock API Server.");
        } catch (IOException e) {
            System.err.println("❌ Failed to start MockApiServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
